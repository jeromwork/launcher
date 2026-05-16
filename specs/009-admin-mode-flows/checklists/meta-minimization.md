# Checklist: meta-minimization

**Spec**: `spec.md` (rev. 1 — post-`/speckit.clarify` 2026-05-15)
**Run**: 2026-05-15 — before `/speckit.plan`.

Anti-bloat audit per Article XI of [`.specify/memory/constitution.md`](../../../.specify/memory/constitution.md) and rule 4 of [`CLAUDE.md`](../../../CLAUDE.md).

---

## Inventory of new abstractions / fields / structures in spec 009

| Entity | Type | Source in spec | Concrete consumer in 009 |
|---|---|---|---|
| `PhoneHealthIndicator` | Local UI value (app/health-ui) | Key Entities, FR-017 | FR-001 (list), FR-017 (adapter output), all health-UI rendering. **Не domain layer.** |
| `PhoneHealthSeverity` | enum | Key Entities | FR-018, FR-020 (cadence по severity), FR-021 |
| `PhoneHealthPreset` | data class with thresholds | Key Entities, FR-019 | FR-018 (severity computation), `HealthToPhoneIndicatorAdapter` |
| `DEFAULT_PHONE_HEALTH_PRESET` | const instance | FR-019 | Единственный экземпляр в спеке 9 |
| `PhoneHealthCriticalEvent` | local event | Key Entities, FR-021 | **Эмитится, но подписчика НЕТ в этом спеке** |
| `presetOverrides: PresetSettings?` | Wire-format field, always `null` | FR-013, Key Entities | **Нет consumer'a в спеке 9** (forward-compat) |
| `PresetSettings` | Wire entity (reserved) | Key Entities | **Нет consumer'a в спеке 9** |
| `ConfigSnapshot` | Wire entity, subcollection record | FR-036, Key Entities | FR-037..043 (write/read/rollback) |
| `ConfigSnapshot.snapshotSchemaVersion` | Envelope schema version | FR-036 | FR-043 (validation), TODO-ARCH-015 |
| `ConfigSnapshot.config.schemaVersion` | Inner config schema version | FR-036 | Наследуется из ConfigCurrent спека 8 |
| `Contact.fromRaw(rawName, rawPhone)` | Domain factory function | Domain validation contract, FR-026/028 | FR-026 (system picker), FR-028 (VCard), FR-033 (dedup) |
| `ValidationError` (sealed) | Domain error type | Domain validation contract | FR-026/028 |
| Per-provider adapter contract | Pattern / 2 implementations | Domain validation contract | `SystemContactPickerAdapter` (FR-026), `VCardImportAdapter` (FR-028) |
| `HealthToPhoneIndicatorAdapter` | Adapter (Health → PhoneHealthIndicator) | FR-017 | UI layer (health-ui) |
| `editMode: Boolean` + edit-callbacks | Composable param expansion | FR-005a | Все edit-операции FR-006..012, FR-046 |
| `PendingLocalChanges` Room table | Reused entity from spec 8 | FR-014a | Local draft per linkId |
| Forward-compat: drag-and-drop primary API choice | Decision (not abstraction) | FR-008 + two-way door | FR-008 — built-in Compose API; ramp задокументирован |

---

## New abstractions

- [x] **CHK001 — Every new interface/port has at least one concrete consumer in this spec**
  - `Contact.fromRaw()` — два consumer'а **в этом спеке**: `SystemContactPickerAdapter` (FR-026), `VCardImportAdapter` (FR-028). NOT "in spec 011 we'll need it". ✅
  - `HealthToPhoneIndicatorAdapter` — consumer: FR-001 (list), FR-017 (один путь Health→UI). ✅
  - `editMode` parameter expansion — consumer: вся US-1 (FR-005..012, FR-046). ✅
  - **`PhoneHealthCriticalEvent`** — ⚠️ **подписчика нет в спеке 9**. Эмитится, но никто не слушает. См. CHK002.
  - `ConfigSnapshot` — consumer: FR-037..043 (write на каждый push, read для history UI, read+push при rollback). ✅
  - **`presetOverrides`/`PresetSettings`** — ⚠️ **нет consumer'a в спеке 9** (всегда `null`). См. CHK008.

- [⚠️] **CHK002 — If a new interface has only one implementation: justified by port-shape need**
  - `Contact.fromRaw()` — один domain-уровневый validator + **N adapter'ов** (2 сейчас, future Telegram/LINE). Это classical ACL pattern per CLAUDE.md rule 2. Mock-first: тестируется отдельно от adapter'ов. Port-shape justified ✅.
  - `HealthToPhoneIndicatorAdapter` — единственная implementation, единственный consumer (UI). **Inline test**: если inline'ить trasform в UI код — потеряем testability (FR-018 severity logic). Adapter оправдан ✅.
  - **`PhoneHealthCriticalEvent`** ⚠️ — event **без подписчика**. Это чистой воды speculative architecture per Article XI §2. Inline TODO в коде ссылается на `SRV-MONITOR-001` (push админу при closed app). **Verdict**: оправдано **только** если event эмитится **дёшево** и **в одном месте**. Если в спеке 9 эмиссия — это просто `eventBus.emit(...)` в одном месте, цена ≈ 0 строк. Если же ради event'a вводится `EventBus` infrastructure — **отвергнуть**. Spec.md не вводит EventBus; предполагается local Flow/StateFlow в ViewModel. **Сохраняем, но требуем в plan.md: одна строка эмиссии, не EventBus framework.**
  - `SystemContactPickerAdapter` / `VCardImportAdapter` — две отдельные implementations с разными formats (URI vs VCard text). Не один port, а два adapter'a над одним domain validator'ом. ✅

- [x] **CHK003 — Mediator/orchestrator/manager class is justified by data transformation**
  Spec не вводит `*Manager` / `*Mediator` / `*Orchestrator` / `*Coordinator`. `HealthToPhoneIndicatorAdapter` — это **adapter** (transformer), не оркестратор. ✅

- [x] **CHK004 — No custom DSL, registry, or plugin system unless simpler composition has been tried**
  Никаких DSL/registry/plugin. Per-provider adapters — это **обычные интерфейсы**, не plugin system (нет dynamic discovery, нет ServiceLoader, нет dependency injection magic). Future Telegram adapter будет просто новый класс, реализующий `Contact.fromRaw()` контракт parsing-side. ✅

## New modules / packages

Spec 009 **не вводит новых gradle модулей**. Implicit план:
- `PhoneHealthIndicator`, severity, preset, event → `:app/health-ui/` (UI module, existing pattern).
- `Contact.fromRaw()` + `ValidationError` → `:core/commonMain/com/launcher/api/config/Contact.kt` (existing file extended).
- `SystemContactPickerAdapter`, `VCardImportAdapter` → `:core/androidMain` или `:app/contacts/` (TBD в plan.md).
- `ConfigSnapshot` types + history repository → `:core/commonMain` + `:core/androidMain` (Firestore).
- Editor mode flag + callbacks → existing composables в `:core/.../components/`.

- [x] **CHK005 — New gradle module satisfies at least one of Article V §3 criteria** — N/A (нет новых модулей).
- [x] **CHK006 — If new module is added: "Why is a package not enough?"** — N/A.
- [x] **CHK007 — No "utils" / "common" / "helpers" dumping ground module created** — N/A; spec не создаёт generic-helpers.

**Note для plan.md**: решить — `app/contacts/` (отдельный package для adapter'ов) или inline в `:app/` без выделения. Не блокирующее, plan.md решит per Article V §3.

## New configuration

### Inventory of new fields в wire format

| Field | Document | Consumer in 009 | Future use | Verdict |
|---|---|---|---|---|
| `/config.presetOverrides` | ConfigDocument | **None** — всегда `null` | `TODO-FUTURE-SPEC-005: preset-editor` | ⚠️ See CHK008 |
| `/config/history/{autoId}` (entire subcollection) | New | FR-036..043 (full CRUD) | — | ✅ |
| `ConfigSnapshot.snapshotSchemaVersion` | History envelope | FR-043 (validation) | TODO-ARCH-015 (transformers) | ✅ |
| `ConfigSnapshot.config` (nested) | History envelope | FR-036, FR-040 (preview), FR-041 (rollback) | — | ✅ |
| `ConfigSnapshot.recordedAt` | History envelope | FR-036, FR-039 (sort) | — | ✅ |
| `ConfigSnapshot.recordedFromDeviceId` | History envelope | FR-039 (UI label) | — | ✅ |

- [⚠️] **CHK008 — New config field has a current FR consuming it (not "future feature might use it")**

  - `/config/history/*` все поля: каждое consumed FR-036..043. ✅
  - **`/config.presetOverrides: PresetSettings?`** ❌ — **violates CHK008 strictly**: ни один FR в спеке 9 не читает и не пишет это поле. FR-013 явно говорит «всегда `null` в спеке 9». Это **speculative field** под `TODO-FUTURE-SPEC-005`.

  **Rule 4 Test 1** (если удалить и inline'ить — что потеряем?):
  - Удаляем поле сейчас → когда придёт preset-editor (отдельный спек, может быть через 3-6 месяцев), добавим поле как **additive change** (per FR-006 спека 8 — additive не bump'ает schemaVersion). **Loss: ноль** для текущей версии; **add-cost потом: одна строка в Kotlin data class + добавление serialization**.
  - Сохранение поля сейчас: **add-cost сейчас: одна строка** + проверка roundtrip-теста с `null`. **Save-cost потом: ноль**.

  **Vector сравним.** Что **может** оправдать сохранение:
  - (a) Если без зарезервированного поля будущий preset-editor спек потребует bump `schemaVersion` (forced migration) — **ложь**, потому что additive policy спека 8 FR-006 явно разрешает добавлять поля без bump'a.
  - (b) Если поле в roundtrip-тесте уже сейчас доказывает «механика nullable optional field работает» — **слабое оправдание**, и так покрывается другими nullable полями.

  **Verdict**: ⚠️ `presetOverrides` — **mild speculative architecture**. Rule 4 Test 1 говорит **remove**. Однако:
  - Cost удержания **минимален** (1 nullable поле, всегда `null`, roundtrip-тест бесплатно покрывает).
  - User в pre-specify session Q2 явно решил оставить как «зарезервированное место» — это conscious decision, не недосмотр.
  - Constitution Article XI §2 говорит "do not create abstraction layers for future flexibility without **a current consumer or constraint**" — `presetOverrides` не abstraction layer, это nullable field. Менее строгое правило.

  **Recommendation**: **оставить, но добавить inline-TODO** в wire-format types с явным указанием: «если в течение 2 будущих спеков (роадмап до spec 011) preset-editor не материализуется — удалить поле как dead code, читать spec 9 archived». Это и есть «exit ramp» per CLAUDE.md rule 3. Spec.md уже close to this — FR-013 говорит «`TODO-FUTURE-SPEC-005`», но не упоминает условие удаления.

  **Action item для plan.md**: добавить в `PresetSettings` data class комментарий-exit-ramp.

- [x] **CHK009 — Config field defaults documented; backward-compat policy defined; migration path documented**
  - `/config/history/*` schemaVersion = 1 from first commit (FR-036).
  - Backward-compat: FR-036 явно описывает **two independent schemaVersion fields** (envelope + inner config) + cascade transformer chain (TODO-ARCH-015).
  - Defaults: `recordedFromDeviceId` обязателен, `recordedAt` обязателен.
  - Retention: FR-038 (10 snapshots client-side housekeeping, migration → SRV-CONFIG-002).
  - `presetOverrides` defaults to `null` (FR-013).

## CLAUDE.md rule 4 self-test

- [x] **CHK010 — Test 1 applied: if abstraction were inlined, what would be lost?**

  - **`Contact.fromRaw()` (domain validator + per-provider adapter contract)**:
    Inline alternative — каждый adapter (system picker, VCard) делает **свою** валидацию имени/телефона.
    **Loss**: divergent validation rules between sources (one adapter trim'ит whitespace, другой нет; один отвергает >100 chars, другой >50). Bugs где «контакт из WhatsApp проходит, идентичный из system picker — отвергнут». **Plus loss**: testability — нет unit-теста "все adapters → ValidationError на одинаковое плохое имя".
    **Plus**: future Telegram SDK adapter (TODO-FUTURE-SPEC-003) **обязательно** будет требовать те же rules — без common validator придётся помнить «как мы валидировали в picker'е». Это classical CLAUDE.md rule 2 ACL situation. **Port оправдан** ✅.

  - **`HealthToPhoneIndicatorAdapter`**:
    Inline alternative — UI Composable получает raw `Health` и сам считает severity.
    **Loss**: FR-018 severity logic (`<5%` Critical / `<20%` Warning) теряет unit-testability (тестировать только через Composable rendering — дороже). **Plus**: если в будущем добавится **второй** consumer severity logic (например, push-notification сервис per `SRV-MONITOR-001`) — придётся выделять, и это будет рефакторинг **уже работающего** UI. **Adapter оправдан** ✅.

  - **`PhoneHealthIndicator` (local UI type) vs прямое использование `Health`**:
    Inline alternative — UI читает `Health` напрямую, severity вычисляет ad-hoc.
    **Loss**: разделение «что показывать» (label / value / severity) от «что измерили» (`Health.batteryPercent: Int`). UI часто рендерит **derived** values («2 мин назад», «🔴 3% критично»). Без UI-типа это разсыпется по Composable'ам.
    **Verdict**: оправдано, но **минимально**. PhoneHealthIndicator — это **тонкий** view-model wrapper, не abstraction layer. Article XI §2 не нарушает.

  - **`PhoneHealthPreset` (data class с thresholds)**:
    Inline alternative — литералы `5`, `20`, `1h`, `24h` разбросаны по коду severity-вычисления.
    **Loss**: FR-019 явно требует **all-in-one-place** для future configurable thresholds. Без этой структуры в `TODO-ARCH-010` (UI редактирования) придётся искать литералы по всему codebase'у. **Verdict**: оправдано **именно** как сборщик констант, **без** UI-абстракции / interface'a / port'a. Это **data class с константами**, не abstraction layer. ✅.

  - **`PhoneHealthCriticalEvent` (event без подписчика)**:
    Inline alternative — нет event'a, нет инфраструктуры.
    **Loss**: ничего **в спеке 9**. Loss появится при имплементации `SRV-MONITOR-001` (push админу), когда нужен подписчик.
    **Article XI §2 verdict**: «do not create abstraction layers for future flexibility without a current consumer». **Технически нарушает.** Но:
    - Эмиссия event'a — **одна строка** (`_phoneHealthCriticalEvents.emit(event)`).
    - Без emit'a — когда придёт subscriber, придётся **искать**, где severity становится Critical, и добавлять emit там. Это **прогресс forward-coupled** код, который Article XI как раз и пытается избежать обратным образом.
    **Compromise**: эмиссия allowed **только если** реализована как `MutableSharedFlow.emit()` (≤ 3 строки), **без** введения generic EventBus framework. См. CHK002.
    **Verdict**: ⚠️ **сохранить, но constrain'нуть imple­ment­ation в plan.md**: «one-line `SharedFlow.emit` без подписчика; запрет на EventBus/registry/dispatcher паттерны».

  - **`ConfigSnapshot.snapshotSchemaVersion` vs `ConfigSnapshot.config.schemaVersion` (два независимых schemaVersion)**:
    Inline alternative (С2 clarification rejected option) — один schemaVersion для всего, transformer один.
    **Loss**: при evolve envelope (например, добавим `revertedFromId: String?`) — без bump единственного schemaVersion'a. С одним schemaVersion'ом — bump envelope = вынужденный bump config (или сложная "envelope-or-config" logic в одном transformer'е). С двумя — каждый эволюционирует независимо.
    **Test 2 (cost of swap)**: если решим объединить позже — придётся написать migration script, который проставит `envelope.schemaVersion = config.schemaVersion` для существующих snapshot'ов. Estimate: 1 день migration + downtime. **Verdict**: на момент `schemaVersion = 1` обе версии равны, никакой overhead'а сейчас. **Future-proofing оправдан**, но это **mild** speculative — Rule 4 Test 1: «что потеряем если inline?» — потеряем future independence, что в спеке 9 не используется. **Tradeoff: одно лишнее поле в record per snapshot**.
    **Verdict**: ⚠️ оправдано **при условии**, что transformer chain (TODO-ARCH-015) действительно использует независимость. Если первый bump окажется bump'aющим обе версии одновременно — second-look это решение и **возможно упростить** до одного version'a. Plan.md или первый transformer-спек должен это re-evaluate.

  - **`editMode: Boolean` + edit-callbacks на существующих composables (FR-005a)**:
    Inline alternative — два **отдельных** набора composable'ов: `TileCard` (view) + `EditableTileCard` (edit).
    **Loss**: divergent rendering. Если изменим визуальный стиль плитки (например, новый shadow) — придётся менять **два** composable'a и помнить про это. Спек 8 уже зафиксировал, что **edit и view используют один rendering pipeline** — это **продуктовое решение**, не реализационная роскошь. Без `editMode` flag это требование невыполнимо.
    **Verdict**: оправдано как минимум `editMode` flag + handful of optional callbacks. Однако:
    - **Watch**: 4-8 callbacks на каждый composable (C5 в Clarifications) — это **много** параметров. Если в plan.md выявится, что callbacks group'ируются в один `EditCallbacks` interface — это OK (один параметр взамен 4-8). Если callbacks остаются разбросанными — это будущий refactor smell.

- [x] **CHK011 — Test 2 applied: if dependency on the other side doubled in price / was deprecated / violated privacy, how long to swap?**

  - **System Contacts API deprecated**: `ContactsContract` — Android stable, маловероятно. Если бы — `SystemContactPickerAdapter` (single file) — replace время **1-2 дня**. Domain validator не меняется. **Adapter seam justified.**
  - **VCard format deprecation**: VCard RFC stable, не меняется. Adapter (VCardImportAdapter) — single file replace или dual-format если бы появился JSON-vcard. **1 день**. Seam justified.
  - **Firestore deprecated for history**: ConfigSnapshot — Firestore document type. Repository pattern с adapter (`:core/androidMain`). Migration на Supabase / own-backend — **1-2 недели** (так же как для `/config/current` спека 8). Seam justified ✅.
  - **Compose 1.6 `Modifier.dragAndDropSource/Target` deprecated / broken**: FR-008 явно two-way door на `Modifier.pointerInput` fallback. **Estimate fallback**: 3-5 дней работы (drag-and-drop из scratch). **Seam justified** через **decision documentation**, не через port.
  - **Android Play Store deprecated** (FR-035, `market://` URI): теоретически. Replace на web URL fallback — **1 день** (уже в FR-035 как secondary fallback). Seam justified.
  - **`PendingLocalChanges` (Room table) deprecated**: расширяется из спека 8 (inherited). Спек 9 не добавляет своего seam'a здесь — наследует существующий. ✅

## Removal validation

- [x] **CHK012 — If spec removes existing abstractions/modules: dangling references audited**
  Spec 009 **не удаляет** abstractions/modules. Удаляются только:
  - Existing bug в `TileCard.kt:73` (захардкоженная иконка Call) — это **fix**, не abstraction removal.
  - Idea «push команда `/commands/install_app` для установки приложения» (заменено на штатную плитку через `Action open_app` — US-7). Эта идея **не была** реализована в коде; это decision pivot, не removal. OUT-017 явно фиксирует.
  - Mock list `AdminDevicesFragment` (из спека 3-4): заменяется реальным списком FR-001. **Dangling reference check**: `AdminDevicesFragment` упоминается в спеке 8 как пример «до 008 — заглушка». После 009 — реальный экран. **Action item для plan.md**: явно delete старый AdminDevicesFragment или rename / repurpose. **Audit needed.**

- [x] **CHK013 — If spec marks code "deprecated, will remove later" — concrete removal task in tasks.md**
  Spec 009 не использует «deprecated, will remove later» pattern. Все OUT-блоки (20 пунктов) — forward references в будущие спеки или TODO-ARCH-NNN backlog items, не deprecation. ✅

---

## Summary

| Status | Count | Items |
|---|---|---|
| ✅ Pass | 10 | CHK001 (with caveat on PhoneHealthCriticalEvent), CHK003, CHK004, CHK005-007 (N/A), CHK010, CHK011, CHK012, CHK013 |
| ⚠️ Watch | 3 | CHK002 (PhoneHealthCriticalEvent — допустимо только как 1-line SharedFlow.emit без EventBus), CHK008 (presetOverrides — speculative field, рекомендация: добавить exit-ramp inline-TODO), CHK009 (passes, но ConfigSnapshot dual schemaVersion — second-look после первого transformer-спека) |
| ❌ Fail | 0 | — |

**Verdict: PASS с тремя watch-item'ами** — спек 9 в целом следует Article XI / rule 4. Нет premature modules, нет registry/DSL/plugin systems, нет «manager/mediator» классов. Однако три места — **граничные** и требуют действий в plan.md.

---

## Top 3 потенциальных over-abstractions (для plan.md внимания)

### 1. ⚠️ `presetOverrides: PresetSettings?` (FR-013) — speculative wire field

**Issue**: поле всегда `null` в спеке 9. Ни один FR его не consum'ит. Article XI §2 говорит "no abstraction without current consumer".

**Mitigating factors**:
- User в pre-specify Q2 явно решил оставить как «зарезервированное место».
- Cost удержания минимален (1 nullable поле, бесплатное в roundtrip-тесте).
- Additive policy спека 8 FR-006 разрешает добавлять поля **позже** без bump schemaVersion — то есть **технически** удержание сейчас **не нужно** для future compatibility.

**Action item для plan.md**:
- (a) Добавить в `PresetSettings` data class явный exit-ramp comment: «если spec preset-editor (TODO-FUTURE-SPEC-005) не материализуется в течение 2 следующих спеков — remove поле как dead code».
- (b) Либо: удалить поле сейчас, опираясь на additive policy спека 8 для future re-introduction. **Это более чисто per Article XI.**

**Recommendation**: leaning toward **(b) — удалить**. Cost re-introduction = одна строка через 6 месяцев. Сохранение = forever ψ violation Article XI §2. Озвучить пользователю.

---

### 2. ⚠️ `PhoneHealthCriticalEvent` (FR-021) — event без подписчика в спеке 9

**Issue**: «эмитится, нет подписчика». Article XI §2 нарушено технически.

**Mitigating factors**:
- Spec явно ссылается на `SRV-MONITOR-001` (push админу при closed app) как будущий subscriber.
- Cost эмиссии = 1-3 строки `SharedFlow.emit` в одном месте.
- Без emit'a — когда subscriber придёт, придётся **искать** где severity становится Critical, что hurts future maintainability.

**Action item для plan.md**:
- (a) Constrain implementation: ровно **одна** `MutableSharedFlow<PhoneHealthCriticalEvent>` + одна точка эмиссии в severity-вычислении. **No** EventBus / dispatcher / registry pattern.
- (b) Добавить unit-тест «severity Info→Critical эмитит event», даже без real subscriber'a. Это закрывает testability gap.
- (c) Inline-TODO в коде (уже в spec): "subscriber appears in spec covering SRV-MONITOR-001; until then — emitted but consumed only by tests."

**Recommendation**: keep, with implementation constraints.

---

### 3. ⚠️ `ConfigSnapshot.snapshotSchemaVersion` vs `ConfigSnapshot.config.schemaVersion` — dual versioning

**Issue**: два независимых schema version поля. На момент v1 — оба равны 1, никакой пользы. Future-proofing для independent envelope evolution.

**Mitigating factors**:
- C2 clarification явно обсудил и выбрал **dual** as opposed to single (conscious decision).
- Independent evolution **возможна** (envelope добавит `revertedFromId: String?` без trigger'а config transformer'а).
- Cost = одно лишнее `Int` field per snapshot.

**Risk**:
- Если первый realistic schema bump (через 6-12 месяцев) okазывается bump'aющим обе версии одновременно — окажется, что независимость **не использовалась**, а complexity (два transformer chain'a в TODO-ARCH-015) уже заплачена.

**Action item для plan.md**:
- Не действие сейчас, но **flag для retrospective**: при имплементации первого config schema migration — re-evaluate, нужна ли independence envelope от config. Если первый migration не использует, рассмотреть de-prication одного из version'ов в спеке, где появится transformer.

**Recommendation**: keep, with retrospective flag.

---

## Нужно ли удалять что-то из спека

**Hard remove (strict Article XI)**: `presetOverrides` field (см. Top 1). Альтернатива — exit-ramp comment, который не remove'ит, но создаёт условие удаления.

**Soft constrain (plan.md)**:
1. `PhoneHealthCriticalEvent` — лимит implementation на `SharedFlow.emit`, запрет на EventBus pattern.
2. `editMode` callbacks (FR-005a) — если в plan.md выявится 4-8 разрозненных callback'ов на composable, рассмотреть group'ировку в `EditCallbacks` interface (один param взамен).
3. `ConfigSnapshot` dual versioning — flag для retrospective при первом schema bump.

**Nothing to delete outright** кроме (опционально) `presetOverrides`. Spec 009 в целом дисциплинирован.

---

## Action items для plan.md / tasks.md

1. **Decide on `presetOverrides`**: оставить с exit-ramp comment, или delete сейчас (preferred per Article XI strict). Озвучить пользователю.
2. **Constrain `PhoneHealthCriticalEvent` implementation**: однострочный `MutableSharedFlow.emit`, не EventBus framework.
3. **Group `editMode` callbacks**: если 4-8 раз­розненных callback'ов на composable — group в `EditCallbacks` data interface.
4. **Inventarize dangling references** на старый `AdminDevicesFragment` (mock list) — delete или repurpose.
5. **Schedule retrospective** на ConfigSnapshot dual versioning при первом config schema migration (нет дедлайна сейчас, but track в TODO-ARCH-015).

---

## TL;DR на русском

**Вердикт**: PASS с 3 watch-item'ами. Спек 9 в целом анти-bloat-дисциплинированный — нет новых модулей, нет registry/DSL/plugin систем, нет manager/mediator классов. Universal `Contact.fromRaw()` + per-provider адаптеры — это классический ACL pattern (CLAUDE.md rule 2), полностью оправдан (два consumer'а уже в спеке, future Telegram/LINE неизбежны).

**Три серых зоны:**

1. **`presetOverrides` field — speculative** (всегда `null` в спеке 9). По строгой букве Article XI §2 — удалить. По духу (additive policy спека 8 разрешает добавлять поля позже без bump'a) — **тем более удалить**, потому что cost re-introduction через 6 месяцев = одна строка. Рекомендация: **delete** или добавить exit-ramp comment с условием удаления.

2. **`PhoneHealthCriticalEvent` без подписчика** — технически нарушает Article XI §2, но cost эмиссии = 3 строки и без него future subscriber придётся искать place эмиссии вручную. Keep, но constrain в plan.md: ровно `SharedFlow.emit`, без EventBus framework.

3. **`ConfigSnapshot` dual schemaVersion** (envelope + inner config) — future-proofing для independent evolution. Conscious decision (C2 clarification). На v1 не используется. Keep с retrospective flag: при первом config schema bump — re-evaluate, нужна ли independence.

**Удалять из спека**: только `presetOverrides` (опционально). Остальное — soft constrain'ы в plan.md.

**Особое достоинство спека 9** (как у спека 8):
- Куча будущих use cases (push админу при Critical, configurable thresholds, multiple presets, contact drift, LINE/WeChat, shared contact book, server-side history writes) **вынесена** в OUT-009..018 + TODO-ARCH-NNN + SRV-NNN. **НЕ** добавлены поля/абстракции «на потом» (за единственным исключением `presetOverrides`).
- Universal `Contact.fromRaw()` — это **не** premature universal interface, это **ACL** под существующих 2 consumer'ов с явным планом 3-го (Telegram). Правильная application CLAUDE.md rule 2.
- `editMode` flag на composables — это **продуктовое решение** «один rendering pipeline», не реализационная роскошь. Без него спек невозможен.
