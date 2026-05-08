# Research — Spec 005 (action-architecture-v2)

Documents the **alternatives considered** for each one-way door from [`spec.md` §5](./spec.md). Format per CLAUDE.md §3: rationale, alternatives, regret condition, exit ramp.

---

## R1. ProviderId — `value class<String>` vs `enum`

### Decision
`value class ProviderId(val value: String)` with companion-object constants for the 8 known providers.

### Alternatives considered

| Option | Pros | Cons |
|--------|------|------|
| **`enum class ProviderId`** | Type safety, exhaustive `when`, no string typos. | Forward-compat broken: backend (spec 007) or QR-share (spec 010) sending an unknown id crashes the app at parse time. Custom-providers (spec 006) impossible. Migrating from string is a wire-format breaking change. |
| **Plain `String`** (no wrapper) | Zero-cost, simplest. | No domain semantics: any `String` accepted; typo `"whatsap"` silently wrong; can't grep for "all providerId producers"; can't add validation later without breaking. |
| **`value class ProviderId`** *(chosen)* | Type-safety at boundary; constants for known providers; unknown ids parse OK and surface as `ProviderUnavailable(UnknownInThisVersion)`. | Verbosity vs raw String (minor); requires `.value` to extract underlying string (intentional). |

### Regret condition
Newer-version configs producing arbitrary unknown providers (e.g. `"whatsap"`, typo) propagate to disk and never surface as bug. **Mitigation**: `ProviderId.fromWire(s)` validates non-empty + non-whitespace + matches `[a-z][a-z0-9_-]{1,31}` (regex documented in `Action.kt`). Typos still pass the regex but are caught at dispatch time as `ProviderUnavailable(UnknownInThisVersion)` — surfaced to user as snackbar.

### Exit ramp
Switch to enum: change `value class` → `enum class`, remove `.fromWire`, add `Unknown` variant explicitly. Replace all `ProviderId.WHATSAPP` references — none in adapters, only in handler-registry map. ≤ 1 day.

### References
- ADR-001 Platform Parity Gate (forward-compat principle).
- Clarification C1.

---

## R2. ActionPayload shape — sealed + Custom escape-hatch

### Decision
`sealed class ActionPayload` with 9 typed variants + `Custom(key: String, params: Map<String, String>)`.

### Alternatives considered

| Option | Pros | Cons |
|--------|------|------|
| **Sealed only (no Custom)** | Maximum type safety; `when` exhaustive without `else`. | Spec 006/008 require user-defined and remote-defined providers; adding extensibility later = breaking wire-format change for all stored configs. |
| **Open `Map<String, JsonElement>`** | Maximum flexibility. | Lose type safety: every handler casts; runtime errors instead of compile errors; serialization tests can't enforce shape. |
| **Sealed + `Custom`** *(chosen)* | Type safety for known cases; explicit escape-hatch for unknown/future; one place (`Custom`-emitting code) to migrate as new variants get added. | Two paths exist — risk of `Custom` overuse. **Mitigation**: lint rule + code review for `Custom` introduction in mock JSON. |

### Regret condition
`Custom` becomes load-bearing for many providers — wire-format degrades to untyped map. **Mitigation**: `Custom` permitted in mock JSON only when corresponding GitHub issue exists for typed-variant follow-up; checked in code review.

### Exit ramp
Sunset `Custom`: introduce typed variants for actually-used keys; mark `Custom` `@Deprecated`; finally `@DeprecatedSinceKotlin` and remove. Migration of mock JSONs is mechanical.

### Why `Map<String, String>` not `Map<String, JsonElement>`?
Per Clarification C2: simpler wire format, no JSON-element serialization quirks, sufficient for foreseeable custom-provider payloads. If real numeric/boolean payloads are needed later, switching to `JsonElement` is a purely additive type change (`Custom`-emitting code updates).

### References
- ADR-005 Resource Budget Delta (no new dependencies).
- Clarification C2.

---

## R3. ReturnContextStore — what to do with persisted records on dev devices

### Decision
One-shot cleanup at `LauncherCore.start()`: if SharedPreferences `launcher.communication.return_context` exists, delete the entire prefs file. No data migration.

### Alternatives considered

| Option | Pros | Cons |
|--------|------|------|
| **Migrate to new schema** | Preserves user state. | Feature itself is removed — there's nothing to migrate state into. Net waste. |
| **Leave records in place** (orphaned prefs file) | Zero code. | Long-term cruft on every device; would need removing in a future spec anyway. |
| **One-shot cleanup** *(chosen)* | Removes orphan immediately; documented with grep-anchor (`// FEATURE-RETURN-CONTEXT-REMOVED-IN-SPEC-005`); idempotent (safe on repeat runs). | None significant. |

### Regret condition
Return-context feature returns in spec 008/009 with the **same** schemaVersion namespace, conflicting with stale records. **Unlikely**: a returning feature would have new fields → new schemaVersion → won't read old records anyway.

### Exit ramp
If feature returns: grep for `FEATURE-RETURN-CONTEXT-REMOVED-IN-SPEC-005`, delete cleanup block, write fresh `ReturnContextStoreV2` with new namespace and schemaVersion.

### References
- CLAUDE.md §5 (wire format versioning).
- Clarification (implicit; addressed in spec §5.3).

---

## R4. ActionCycleGuard / RestoreOutcomeEvaluator removal

### Decision
Remove both classes wholesale. UI-level debounce (`clickableDebounced(500ms)`) replaces double-tap protection.

### Alternatives considered

| Option | Pros | Cons |
|--------|------|------|
| **Keep both, generalise to non-WhatsApp** | Reuses existing code. | These classes were written for one feature in spec 002 and never reused. Generalising introduces premature abstraction (Article XI) without a current consumer. |
| **Remove both, replace with global dispatcher-level idempotency** | Defends against rapid duplicate `dispatch()` calls. | No demonstrated need yet; anti-double-tap is a UI concern, not a dispatcher concern. |
| **Remove both, UI-level debounce only** *(chosen)* | Smallest correct solution; matches CLAUDE.md §4 "Minimum Viable Architecture". | If real dispatch idempotency need emerges later (spec 008?), it's a new concern with proper requirements. |

### Regret condition
Backend (spec 007) sends bursts of duplicate dispatches (network retry storm) and double-launches occur. **Mitigation**: dispatch idempotency would be a spec 007/008 concern, not a spec 005 carryover.

### Exit ramp
If needed: introduce `IdempotentActionDispatcher` decorator in `androidMain` that dedupes by `Action.hashCode()` + `sourceModuleId` within a window. Implementable in 1 file; no Action/wire-format change required.

### References
- Spec §5.4.

---

## R5. Handler-registry vs single big `when` in `AndroidActionDispatcher`

### Decision
`Map<ProviderId, ActionHandler>` injected into `AndroidActionDispatcher`; one handler class per provider.

### Alternatives considered

| Option | Pros | Cons |
|--------|------|------|
| **Single `when` over `providerId` in `AndroidActionDispatcher.dispatch`** | Less indirection; everything in one file. | 8 cases × ~30 lines = 240-line method; merge conflicts when multiple providers change at once; harder to unit-test in isolation. |
| **One file per provider with extension functions** | Splits the file. | Loses DI seam — can't inject fake handler for tests. |
| **Handler interface + registry** *(chosen)* | Open-closed: adding a provider = new file + DI registration; per-handler unit tests trivial; consistent shape across providers. | Indirection cost (small for 8 implementations). |

### Regret condition
Future maintenance overhead becomes higher than collapsed `when` would have been. **Probability low**: 8 providers is comfortably above the "≥3 implementations justify abstraction" threshold from CLAUDE.md §4.

### Exit ramp
Collapse into `when`: mechanical refactor (move handler bodies into `dispatch`, remove handler classes, remove DI bindings). Action contract unchanged. ≤ 1 day.

### References
- Spec §5.5.
- Article XI §5 (no orchestrator that just passes data through — confirmed: `AndroidActionDispatcher` does substantive work: schemaVersion check, registry availability check, fallback recursion, event emit).

---

## Orthogonal research notes

### N1. Why `kotlinx.serialization` and not Moshi/Gson for Action wire format?
Inherited from spec 004 stack (ADR-005). Already cross-platform via KMP. No new dependency.

### N2. Why `Robolectric` for handler tests not androidTest (instrumented)?
Existing test infrastructure from spec 004. Faster CI. `Intent` construction is pure-JVM. Real-device launch verification happens in two-emulator smoke per `android-emulator` skill.

### N3. Why `<queries>` in manifest, not `QUERY_ALL_PACKAGES` permission?
Play policy: `QUERY_ALL_PACKAGES` requires declared use category that doesn't fit a launcher's needs (per Google's policy text). `<queries>` for known packages (whatsapp, telegram, youtube) is the correct narrow approach. Documented per `permissions-platform` CHK-008.

### N4. Why no `expect`/`actual` for `ActionDispatcher` itself?
`expect class ActionDispatcher` would tightly couple commonMain to a specific implementation shape per platform. `interface ActionDispatcher` (port) + `AndroidActionDispatcher : ActionDispatcher` (adapter) gives stricter ACL per CLAUDE.md §2 and easier swap of implementations (production vs test, eventually iOS).

---

## R6. Региональные провайдеры в масштабе (десятки → сотни → тысячи)

### Decision
В spec 005 в коде остаётся **узкое ядро** «глобальных» провайдеров (8 штук: app, whatsapp, telegram, phone, sms, browser, youtube, system_settings). Региональные (LINE — Япония, KakaoTalk — Корея, WeChat — Китай, Viber — Восточная Европа, региональные банк-приложения, локальные мессенджеры) — **отложены до spec 008/009 как remote provider registry**.

### Alternatives considered

| Option | Pros | Cons |
|--------|------|------|
| **Всё в сборке APK** (зашить тысячи provider id + метаданных) | Работает оффлайн; никаких сетевых вызовов. | Раздувает APK (нарушает ADR-005 budget); требует релиза приложения для нового провайдера; региональные пользователи получают «мусор» в выборе. |
| **Чисто remote** (даже whatsapp/phone приходят с сервера) | Полная гибкость. | Без сети приложение бесполезно; первый запуск требует регистрации; нарушает Article XIV §4 «local-first preferred». |
| **Ядро в коде + remote для остальных** *(chosen)* | Без сети работают «глобальные» провайдеры; региональные подгружаются по геолокации/языку; APK маленький. | Требует remote-registry инфраструктуры (spec 008+); кэш и invalidation для метаданных. |

### Что **уже** заложено в spec 005 для этого сценария

- `ProviderId` — value class над `String`, не enum. Новые id из remote registry парсятся без переcompilation.
- `ProviderUnavailable(UnknownInThisVersion)` — старая версия приложения, увидев `providerId: "line"`, не падает; в визарде provider скрывается; в `FlowScreen` slot показывает «недоступно в этой версии».
- `Custom` payload escape-hatch — региональный провайдер с нестандартной структурой запроса не требует bumpa wire-format'а.

### Что **появится** в spec 008/009

- Контракт `provider-registry-snapshot.json` (тоже с `schemaVersion`) — `[{ providerId, displayName_localized, iconUrl, storeUrl, deepLinkTemplate, regionTags, … }]`.
- Локальный кэш в DataStore с TTL.
- UI визарда: «доступные провайдеры в твоём регионе» (фильтр по locale + IP / SIM-region).

### Regret condition
Бэкенд недоступен или не написан вовремя — пользователи в Японии не могут добавить LINE. **Митигация**: глобальные провайдеры (whatsapp, telegram, phone, sms, browser) покрывают ≥ 80% сценариев и идут в коде. LINE в spec 005 не нужен.

### Exit ramp
Если remote registry окажется неоправданным (slishком много инфраструктуры на 30 регионов) — переключиться на «релиз = новые провайдеры»: тогда `Action.kt` companion-object растёт от 8 до 30 константных id; чтения старых JSON совместимость не нарушают.

### References
- ADR-005 §3 Resource Budget Delta (APK не должен раздуваться).
- Article XIV §4 (local-first preferred).
- Clarification C1 (forward-compat для unknown provider).

---

## R7. Action wire format рассчитан на reuse в шаримых конфигах

### Decision
`Action` wire format v1.0.0 **сознательно проектируется как кандидат для будущих use cases**: not just локальные mock JSON, но и:
- админ-конфиг, отправляемый с телефона админа на телефон олда (spec 008+);
- шаримые шаблоны flow между пользователями (spec 010 — QR/share);
- backend-managed контент (spec 007).

### Что это означает для дизайна сейчас

- **`contactRef` — абстрактная ссылка, не конкретный ID** (телефон/email). Резолвится через локальный contact-registry получателя. Шаримый шаблон содержит `contactRef: "primary_doctor"` — получатель в импорт-визарде сам выбирает реального человека из своих контактов.
- **`packageHint` в `OpenApp`** — **подсказка**, а не requirement: получатель может иметь другую версию (whatsapp.w4b вместо whatsapp), handler'ы умеют fallback'ить.
- **URI валидация в `BrowserHandler`** — реализована *уже* в spec 005 (T574: rejects не-`https:`/`http:`). Это первая линия защиты от вредоносных импортируемых конфигов.
- **`Custom.params` лимиты** (16 ключей × 64 chars × 1024 chars) — реализованы в spec 005 (T532, security CHK-011). Не дают сделать «троянский» payload с гигантским blob.

### Alternatives considered

| Option | Pros | Cons |
|--------|------|------|
| **Закладывать шаринг сейчас** (placeholder syntax `@SLOT_CONTACT_1`, manifest шаблона, подпись) | Spec 010 будет тривиальным. | Добавляет сущности без consumer'а в spec 005 (нарушает Article XI и CLAUDE.md §4). |
| **Игнорировать шаринг как неактуальный** | Минимальный спек. | Через 2–3 спека выяснится, что `contactRef` нельзя ресолвить локально, нужна wire-format миграция (breaking). |
| **«Совместим, но не реализован»** *(chosen)* | Минимальный спек + явная зафиксированная design constraint, проверяемая на ревью. | Нужна дисциплина: каждое будущее изменение wire-format должно учитывать сценарий шаринга. |

### Что **появится** в spec 010

- Контракт `flow-share-bundle.json` v1.0.0 (надстройка над массивом `Action`'ов): manifest (title, author, version, preview, categories), список placeholders.
- Placeholder syntax (например, `@@CONTACT_DOCTOR@@` внутри `contactRef` строки) — резолвится import-визардом получателя.
- Import wizard UI (preview → fill placeholders → confirm).
- Опционально — подпись/чексумма для anti-tampering.

### Regret condition
Через год выяснится, что `contactRef: "primary_doctor"` не подходит для шаринга, потому что нужен **типизированный** placeholder с метаданными («это контакт-врач, нужна категория health»). **Митигация**: добавление `placeholderHint` поля в `Action` будет minor-bumpa контракта (1.0.0 → 1.1.0), не major — старые читатели игнорируют новое поле.

### Exit ramp
Если шаринг окажется ненужным — все design constraints из этого решения **бесплатны** (они не добавили сложности в код spec 005, только не дали сделать иначе).

### References
- CLAUDE.md §4 Test 1 (минимум сейчас, расширяемость потом без миграции).
- ADR-005 wire format versioning rules.
- Roadmap: spec 010 — QR/share.

---

## TL;DR (по-русски, для новичка и для будущего AI)

**Суть.** Зафиксировано 5 ключевых выборов (R1–R5), каждый с альтернативами и «exit ramp» (стоимость отката ≤ 1 дня для всех пяти). Плюс 4 ортогональных решения (N1–N4) поменьше.

**Конкретика, которую стоит запомнить:**
- **R1.** `ProviderId` = `value class<String>` с регексом `[a-z][a-z0-9_-]{1,31}`. Альтернативу `enum` отвергли: backend/QR-share с новым providerId уронит старые телефоны.
- **R2.** `ActionPayload` = sealed (9 вариантов) + `Custom(key, Map<String,String>)`. Чисто `Map<String,JsonElement>` отвергли (теряем тип-безопасность); чисто sealed без `Custom` отвергли (расширяемость потом = breaking change).
- **R3.** SharedPreferences `launcher.communication.return_context` — стираем один раз в `LauncherCore.start()` без миграции (фича удалена, мигрировать нечего). Якорь-комментарий `// FEATURE-RETURN-CONTEXT-REMOVED-IN-SPEC-005`.
- **R4.** Удаляем `ActionCycleGuard` + `RestoreOutcomeEvaluator`. UI-уровневый `clickableDebounced(500ms)` заменяет защиту от двойного тапа.
- **R5.** `Map<ProviderId, ActionHandler>` injected в `AndroidActionDispatcher`. Один большой `when` отвергли: 8 implementations × ~30 строк = 240-строчный метод + конфликты слияния.
- **R6.** Региональные провайдеры (LINE, KakaoTalk, WeChat, Viber, банки и т.д.) — НЕ в сборке APK. Узкое ядро 8 «глобальных» в коде; региональные приедут через remote provider registry в spec 008+. Старая версия не падает на unknown providerId (Clarification C1).
- **R7.** Action wire format v1.0.0 — рассчитан как кандидат для шаримых конфигов (spec 010). `contactRef` остаётся абстрактной ссылкой (placeholder-friendly), URI scheme валидируется, `Custom.params` имеет жёсткие лимиты — это снижает риск вредоносного импорта.
- **N3.** `<queries>` в манифесте, не `QUERY_ALL_PACKAGES` (Play-policy ограничение).
- **N4.** `interface ActionDispatcher`, не `expect class` — port даёт строже ACL и проще fake adapter.

**На что смотреть с осторожностью:**
- R2 «Custom-злоупотребление» — escape-hatch может стать свалкой; митигация = code review при добавлении `Custom` в JSON-моки.
- R5 — handler-registry оправдан 8 implementations (>3 порог); если в будущем останется 2-3 провайдера — стоит свернуть в `when` (≤ 1 дня работы).
- R6 + R7 — два «зарезервированных направления» расширения. Любое будущее изменение wire-format `Action` должно проходить sanity-check: «не ломает ли это remote registry (R6) и не делает ли невозможным шаринг шаблонов (R7)».
