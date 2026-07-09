# Feature Specification: Simple Launcher Profile (first MVP-demo)

**Feature Branch**: `task-7-simple-launcher-first-run`
**Created**: 2026-06-24 (rewritten after architecture clarification, verification, and constitution amendments 1.7 / 1.8)
**Status**: Verification (post-device-verify 2026-06-25)
**Input**: User description: backlog [TASK-7](../../backlog/tasks/task-7%20-%20Simple-Launcher-first-run-Setup-Wizard.md) S-1. Ship `simple-launcher` profile as composition of bundled JSON documents on top of the existing wizard engine (TASK-1 / F-3 Done). LOCAL mode, без cloud. Constitution Article VII §9-15 + Article II §8 + Article III §7.

> **⚠️ UPDATE 2026-06-25 — post-device-verification revert.**
>
> The spec below was written before device verification surfaced two architectural problems on Xiaomi 11T:
> 1. **Koin DI cycle** between `Map<*, *>` bindings — fixed by named qualifiers in [commit `8882c71`](https://github.com/jeromwork/launcher/commit/8882c71) + new regression test `Spec015DiGraphTest`.
> 2. **`StepType.Custom` misuse**. F-3 (spec 015) added `StepType.Custom(name: String)` as a *placeholder for future extension* with the comment "Custom types от app-family добавляются через DI Map" — no concrete use case (violation of CLAUDE.md rule 4). TASK-7 Phase-5 (T046-T049) misused this placeholder to wire `pair-admin` as a `Custom` step with a `PairAdminCustomStepHandler` that fire-and-forget-launched `PairingActivity` on engine step entry — no user UI, no skip path, no `CheckSpec`-based state read. This breaks Article VII §11 (wizard = projection of config, not source of truth) and §13 (no per-profile code branches).
>
> **Constitution amendment 1.10** (2026-06-25) retires `StepType.Custom` and forbids per-refId Kotlin handlers (new Article VII §16). Phase-5 reverted. The 4th wizard step (`Custom("pair-admin") Optional`) **removed** from `simple-launcher.wizard.manifest.json`. The manifest is now **3-step**: `ROLE_HOME` + `tileSet` + `POST_NOTIFICATIONS`.
>
> Pair-admin returns at **TASK-8** as a standard `SystemSetting` step via the proper declarative seam:
> - `CheckSpec.PairAdminLink` reading `activeAdminLinkCount > 0` from `LinkRegistry`.
> - `ApplySpec.PairAdminIntent` dispatching to pairing UI via the generic intent ApplyHandler.
> - New pool entry `pair-admin` in `android-pool.json` v2 with these `check.kind` / `apply.kind`.
> - Step re-added to `simple-launcher.wizard.manifest.json` as `stepType: "SystemSetting"`, `canSkip: true`, `criticality: "Optional"`.
>
> See [`docs/dev/project-backlog.md`](../../docs/dev/project-backlog.md) → TODO-TASK7-005 for the full re-implementation contract.
>
> **All references below to "4 steps", "Custom step", "pair-admin Custom", `Custom("PairAdmin")`, `CustomStep`, `CustomStepHandler`, FR-027, FR-028, FR-029, Part G "Pairing integration as Custom step", and SC-005 in its pre-2026-06-25 form describe the original (now-reverted) Phase-5 design.** They are kept inline as historical record. The authoritative current state is in [`tasks.md`](tasks.md) Phase 5 revert notice + Device Verification Findings.

---

## Clarifications

### 2026-06-24 — Pre-plan clarification pass

After mentor-mode dialog, owner provided product-level direction. Below are resolutions woven into FRs / scope / Constitution amendments:

| # | Question | Resolution |
|---|----------|------------|
| 1 | MVP definition | All base functional blocks built end-to-end; polish via JSON config, not code. Codified as Article II §8 (constitution amendment 1.8). |
| 2 | Wizard actor | Primarily `assisting` (родственник / помощник / IT-support / медсестра). Aspiration: проходимый и `primary user`'ом самостоятельно. Senior-safe baseline (Article VIII §7) holds for both paths. |
| 3 | Orphan dependencies (spec 010/007/003) | Verified 2026-06-24: spec 010 ARCH-016 closure landed in code (`ConfigBackedFlowRepository` reads `/config/current`, `RoleHomeCheckAdapter` + 5 others, `GmsHardBlockActivity`); spec 007 pairing UI substantial (PairingActivity, ConsentScreen, QrDisplayScreen, FirestoreLinkRegistry, ManagedDevicesRegistry); spec 003 ui-skeleton — HomeActivity + RootComponent + ConfigBackedFlowRepository. **All dependencies functional in code.** TASK-7 can build on them without prerequisite fix-up. |
| 4 | Wizard step count and content | **3 mandatory + 0 silent + 1 optional = 4 visible steps**. Order: (1) `android.role.home` Required canSkip:false, (2) `tileSet` Required canSkip:false default classic-6, (3) `android.permission.POST_NOTIFICATIONS` Required canSkip:true (auto-skip API < 33), (4) PairAdmin Optional canSkip:true. Language → auto-detect (no wizard step). Theme → default warm light + post-wizard banner if user wants to change. Other settings (CALL_PHONE, accessibility-service, battery-optimization, hide-status-bar) → Optional Silent (available in Settings, no wizard step, no banner). |
| 5 | Bundled documents count | One `tile.set` (existing `classic-6`), one `screen.layout` (existing `3x4-classic`). No new bundled documents in TASK-7. Additional sizes / variants → post-MVP polish through JSON authoring (Article II §8). |
| 6 | Pairing in wizard | YES — `Custom("PairAdmin")` optional step. Real QR-scanner (spec 007 verified working). Cloud config push deferred to TASK-8; in TASK-7 pair handshake demonstrates trust establishment only. Inline TODO at PairAdmin step site: `// TODO(TASK-8): admin config push activates here when TASK-8 lands`. |
| 7 | Locale: app override or system follow | App-level override **persists** through `AppCompatDelegate.setApplicationLocales()`. System locale changes do NOT silently override the user's wizard-time choice. Codified as Article III §7 (constitution amendment 1.8). |
| 8 | Theme editing post-wizard | OUT of TASK-7. Theme defaults to warm light. Editing through future Settings entry (separate task) or via banner if wizard step is added later in pool. |
| 9 | Tutorial hints in MVP | None. `TutorialHintManager` port stays as seam (F-3); no concrete hints in TASK-7. |
| 10 | Bundled JSON corruption UX | Spec 010 `PlayStoreFallbackActivity` already handles `IncompatibleVersion` / `ParseError` from `ConfigSource`. TASK-7 inherits, does NOT duplicate. |
| 11 | Pool schema v2 (`check.kind` callback decoupling) | YES — TASK-7 ships schema v1 → v2 bump. Sealed `CheckSpec` in `commonMain` with `@JsonClassDiscriminator("kind")`. Handler registry in `AndroidSystemSettingAdapter` via Koin DI. Backward-compat read: v1 entries (no `check` block) fall through to legacy hardcoded `when(settingId)` dispatch (deprecated path, removed after migration). |
| 12 | Theme value migration when pool choices change | OUT of TASK-7. New backlog task created: "Pool choice migration policy". |
| 13 | Cache for `SystemSettingPort.status()` | YES — simple TTL cache (30s) in `AndroidSystemSettingAdapter` invalidated on Activity Lifecycle.RESUMED. Pattern follows spec 010 FR-020a re-check semantics. |
| 14 | Diagnostic events for MVP | Wizard engine continues to call `DiagnosticEmitter.emit()`. Real adapter remains no-op (per F-3 A-17). No analytics backend in MVP. |
| 15 | GMS-less device handling | OUT of TASK-7. Spec 010 FR-042 `GmsHardBlockActivity` already handles. Hard-block path inherited unchanged. |
| 16 | MCP / AI agent config authoring | OUT of TASK-7. Architecturally compatible: `ConfigSource` adapter pattern supports future `McpConfigSource`; `CheckSpec` sealed class doubles as capability surface. TASK-33 (Capability Registry Foundation) + TASK-36 (AI provider implementations) carry implementation. |
| 17 | Donastroika wizard when config updates | YES — engine's `computePending(manifest)` filter (new in TASK-7) returns only pending steps. If pool entries added in updated bundled JSON or in non-bundled `ConfigSource` adapter (future), engine includes them as pending on next launch. Codified as Article VII §14. |

---

## Контекст и цель

**Архитектурная модель** (constitution Article VII §9–15, glossary §2).

Приложение — generic shell. Его поведение для конкретного product variant'а определяется **профилем** (`profile`) — именованной композицией bundled JSON-документов. `simple-launcher` — это **первый concrete profile**, валидирующий модель: elderly-friendly handheld variant с большими плитками, тёплыми цветами, минимумом choices.

**Personas vs domain roles** (CLAUDE.md). Wizard проходит **assisting** — родственник в гостях, платный помощник, IT-support в clinic'е, HR в B2B. Aspirational secondary path — `primary user` (пожилой пользователь) проходит wizard самостоятельно. Senior-safe baseline (Article VIII §7) держится для обоих путей.

**Что уже есть в коде** (verified 2026-06-24):

| Slice | Status | Evidence |
|---|---|---|
| F-3 Wizard engine (TASK-1 Done) | ✅ Working | `WizardEngineImpl.kt`, `AndroidSystemSettingAdapter.kt`, `BundledConfigSource.kt`, `WizardActivity.kt` |
| Bundled pools | ✅ Working | `android-pool.json` (6 entries), `ui-pool.json` (6 options) |
| Bundled documents | ✅ Working | One `screen.layout` (`3x4-classic`), one `tile.set` (`classic-6`) |
| `simple-launcher.json` manifest | ⚠️ Stub | `autoOrder: true, steps: null` — wizard works via auto-order but no per-profile overrides applied |
| Spec 010 setup-assistant (orphan) | ✅ Working | `RoleHomeCheckAdapter`, `ConfigBackedFlowRepository` (ARCH-016 closed), `GmsHardBlockActivity` (FR-042), `PlayStoreFallbackActivity` |
| Spec 007 pairing (orphan) | ✅ Working | `PairingActivity`, `ConsentScreen`, `QrDisplayScreen`, `FirestoreLinkRegistry`, `ManagedDevicesRegistry` |
| Spec 003 ui-skeleton (orphan) | ✅ Working | `HomeActivity`, `RootComponent` (Decompose), `FlowRepository`, `PresetRepository` |
| Locale persistence via `AppCompatDelegate` | ❌ Missing | Engine saves `UserPreferences.languageOverride` but never calls `AppCompatDelegate.setApplicationLocales()` |
| Engine `computePending` (state-of-device check) | ❌ Missing | `WizardEngineImpl.run()` traverses linearly without `SystemSettingPort.status()` per-step pre-flight |
| Pool entry `check.kind` callback ref | ❌ Missing | `AndroidSystemSettingAdapter` dispatches via hardcoded `when(settingId)` blocks |
| `SystemSettingPort.status()` cache | ❌ Missing | Every call is a fresh Android API query |

**LOCAL mode**: без Google Sign-In, без cloud. Cloud features deferred per decision 2026-06-15-deferred-cloud/01. Cloud config push (admin → primary user) активируется с TASK-8.

---

## Сценарии использования

> Эти сценарии — концентрированный взгляд «как это будет работать в реальной жизни». Читая их, можно проверить, движется ли спека в правильном направлении, без необходимости погружаться в FRs. Каждый сценарий соответствует одному или нескольким FRs (помечено в конце сценария).
>
> **Терминология**: «настройка» = запись в pool (например, ROLE_HOME, theme, tileSet) — содержит свои texts (labelKey / descriptionKey / extendedInstructionKey). «Шаг wizard'а» = StepEntry в manifest, который **ссылается** на настройку через `refId` и задаёт per-profile policy (`canSkip`, `criticality`). Texts всегда в настройке; policy — в манифесте. Wizard — это view конфига профиля.

### Сценарий 1 — Чистая установка: помощник проводит бабушку через wizard

**Контекст**: дочка-помощник приехала к бабушке в гости, ставит ей наш лончер первый раз.

1. Помощник тапает иконку приложения. Через 1-2 секунды появляется первый экран wizard'а. Wizard рендерится по конфигу профиля `simple-launcher` — крупные кнопки, тёплая палитра, читаемый текст. Styling baked в конфиг профиля, одинаково для всех кто будет проходить.
2. ★ Wizard показывает настройку «Сделать наш лончер главным». Заголовок и описание берутся из настройки (pool entry для ROLE_HOME) — текст хранится там, не в wizard'е. Помощник нажимает кнопку «Подтвердить» → системный диалог Android → выбирает наш лончер.
3. ★ Wizard показывает настройку «Выбери раскладку плиток». Доступна одна bundled раскладка — 6 плиток (`classic-6`). Помощник подтверждает.
4. ★ На Android 13+: wizard показывает настройку «Разрешить уведомления». На Android < 13 эта настройка не существует в pool для соответствующего API → wizard её не показывает.
5. ☆ Wizard показывает необязательную настройку «Соединиться с админом по QR-коду». Помощник нажимает «Пропустить, настрою позже». (Реальный QR-flow — ответственность spec 007; TASK-7 либо использует существующую функциональность, либо ship'ает stub-кнопку — решается во время implementation.)
6. Wizard завершается. Через 1 секунду открывается главный экран — 6 плиток с базовыми action'ами.

**Что закрывает**: US-1, FR-001, FR-002, FR-014, SC-001, SC-002.

**Trouble case 1.b — Помощник убил приложение посреди wizard'а**: на шаге выбора раскладки помощник свайпнул приложение из списка недавних. Через 5 минут открывает заново → wizard продолжается с того же шага.

**Trouble case 1.c — Помощник отказался от ROLE_HOME в системном диалоге**: wizard остаётся на той же настройке, показывает текст из настройки (`extendedInstructionKey` в pool entry для ROLE_HOME): «Без этого Home button не откроет приложение — попробуй ещё раз». Текст хранится в pool entry, не в wizard'е. Помощник нажимает «Повторить».

**Trouble case 1.d — Перезагрузка устройства после wizard'а**: бабушка перезагрузила телефон. После reboot Home button открывает наш лончер, плитки те же, wizard не повторяется.

---

### Сценарий 2 — Wizard пропускает уже применённые настройки (config-check master)

**Контекст**: помощник в прошлый раз начал настройку, потом зашёл в Android Settings вручную и сделал наш лончер главным. Через неделю открывает приложение.

1. Помощник тапает иконку. Приложение при старте загружает **конфиг профиля** (или его кэш) из store. Конфиг — first-class entity: список того, какие настройки в этом профиле задействованы, какие mandatory, какие optional, как их проверять. Wizard это лишь отображение того подмножества настроек, что pending.
2. Приложение для каждой настройки из конфига проверяет фактическое состояние **на устройстве**: «применена ли эта настройка сейчас?», не «прошёл ли я её в wizard'е».
3. Видит: ROLE_HOME уже выставлен (помощник сделал это вручную через Android Settings) → настройка считается применённой → wizard её не показывает. Раскладка ещё не выбрана → pending. POST_NOTIFICATIONS ещё не выдан → pending.
4. Wizard открывается **сразу на pending настройке** — выбор раскладки. Без повторного запроса ROLE_HOME.
5. Помощник проходит оставшиеся pending. Wizard завершается.

**Что закрывает**: US-2, FR-013, FR-014, SC-003.

**Trouble case 2.b — Все настройки конфига уже применены**: помощник в прошлый раз прошёл всё. Тапает иконку — wizard не показывается, приложение сразу открывает главный экран.

**Trouble case 2.c — Bundled конфиг повреждён или версия выше известной**: приложение получает ошибку при чтении. Открывается специальный экран «Установи обновление приложения» (уже сделан, наследуется из существующей инфраструктуры — spec 010 `PlayStoreFallbackActivity`).

---

### Сценарий 3 — Язык стабилен при изменении системного, indicator в Settings

**Контекст**: телефон бабушки на русском (системная локаль). Через 2 месяца кто-то меняет системный язык Android на английский.

1. При первом запуске приложение определило системную локаль (русский) и **запомнило как свой собственный язык** через `AppCompatDelegate.setApplicationLocales()`. Это сохранено отдельно от системной локали.
2. Wizard завершён, бабушка пользуется приложением на русском.
3. Через 2 месяца бабушка случайно сменила в Android Settings → Languages → English. Системная локаль теперь — английский.
4. Бабушка вернулась на главный экран нашего лончера. **Лончер остался на русском.** Никакого surprise.
5. **В нашем Settings есть indicator** «Язык приложения: Русский (системный Android: English)» — чтобы помощник, заглянувший в Settings когда-то позже, понял почему есть расхождение и мог осмысленно решить, что с этим делать.
6. Если помощник хочет переключить app на английский — заходит в наш Settings → Language → выбирает English. Override снимается, app начинает следовать системной локали.

**Что закрывает**: US-3, FR-017, FR-017a, FR-018, SC-004, Article III §7 stability.

**Trouble case 3.b — Android < 13**: AppCompat shim handle'ит persistence иначе, но visible UX тот же.

---

### Сценарий 4 — Новая настройка после обновления приложения → тихий banner + checklist в Settings

**Контекст**: помощник три месяца назад настроил телефон бабушке. Сегодня вышла обновлённая версия приложения, в bundled конфиге profile'а появилась новая настройка — например, разрешение «Не беспокоить».

**UX precedent** (per Article XV §14): checklist + banner pattern — GitHub onboarding «Setup your repo», Slack «Get started», Stripe «Activate your account», Notion onboarding. Каждый item — independent row, status indicator, tap → settings.

1. Бабушкин телефон автоматически обновился через Google Play.
2. Бабушка пользуется лончером как обычно. **Wizard не запускается заново**. Главный экран как был.
3. **В нашем Settings — иконка «есть что донастроить» с числом** (например `[!] 1`). Это означает, что в конфиге profile'а есть pending настройка.
4. Когда помощник или admin зайдёт в Settings — увидит **checklist** с pending настройками: каждая строка с label из настройки + кнопкой «Настроить сейчас». Тап → открывается стандартный flow для одной настройки (тот же UI, что был бы в wizard'е, но как standalone screen).
5. **Бабушке не показывается ничего навязчивого** — она не зайдёт в Settings, индикатор её не отвлекает. Lifecycle: app продолжает работать как есть, только в Settings виден pending.

**Что закрывает**: FR-013, FR-014, Article VII §14 config-check master.

**Trouble case 4.b — Pending настройка optional**: иконка `[?] 1` другого цвета (рекомендуется, не критично), всё остальное так же.

**Trouble case 4.c — Pending должна быть видна и админу удалённо**: admin заходит в свой UI «настройки удалённого телефона бабушки» → видит точно тот же `[!] 1` индикатор и список pending. Это требует **синхронизации pending setup state в облако** (зашифрованным). В TASK-7 (LOCAL mode) — отображается только локально на бабушкином Settings. **Inline TODO**: `// TODO(TASK-8+): cloud sync of pending setup state для admin visibility`.

**Trouble case 4.d — Изменилась структура pool (schemaVersion bump)**: приложение читает new-version JSON. Если несовместимо — экран «Установи обновление приложения» (см. trouble case 2.c).

---

### Сценарий 5 — «Пройти все настройки заново пошагово» из Settings

**Контекст**: помощник заметил `[!] 1` индикатор в Settings бабушки, но хочет не просто закрыть pending, а пересмотреть **все** настройки сразу (например, посмотреть, что не подкорректировать и где).

**UX precedent** (per Article XV §14): sequential walk-through с current value as default — **Apple Setup Assistant on factory reset** (показывает все настройки, current value preserved, helper жмёт «Оставить» или «Изменить»), **Windows OOBE on rerun** (аналогично), **TurboTax sections walk**. Альтернативные patterns (skip-by-default через 30 шагов) отвергнуты как cognitive-load-overload.

1. Помощник в нашем Settings нажимает кнопку «Пройти все настройки пошагово».
2. Запускается тот же самый wizard UI, но **показываются все настройки конфига профиля**, не только pending. Для каждой настройки **default = current value** (что выбрано сейчас).
3. На каждом шаге — две большие кнопки: «Оставить» (продвигает на следующий шаг без изменений) и «Изменить» (открывает picker / system dialog для изменения).
4. Помощник пробегает все 6-8 настроек simple-launcher, оставляя большинство как есть, меняя одну-две.
5. По завершении wizard'а — главный экран обновляется с новой композицией если были изменения.

**Что закрывает**: FR-014a, Article VII §11 (wizard is view of profile, this is alternate view).

**Trouble case 5.b — Помощник нажал «Выход» в середине walk-through**: настройки, изменённые до выхода, **сохранены** (не транзакция). Те, что не пройдены — остаются как были.

---

### Сценарий 6 — Wizard uniform для всех (verification senior-safe из конфига профиля)

**Контекст**: simple-launcher профиль конфигурирован с senior-safe styling — крупные кнопки, большой шрифт, тёплая палитра. **Wizard выглядит и ведёт себя одинаково независимо от того, кто его проходит** — помощник, бабушка, IT-support, AI agent через MCP. Это not condition-ная логика, а конфиг.

1. Любой actor открывает приложение в первый раз. Wizard рендерится по конфигу profile'а: senior-safe styling задан в `simple-launcher` config (не определяется runtime'ом «кто это»).
2. Шаги одинаковые. Кнопки одинаково крупные. Текст одинаково крупный. Tap targets одинаково большие.
3. Если помощник предложил бабушке самой пройти — wizard такой же, как был бы у помощника. Бабушка может, потому что **уже** senior-safe (профилем задано), не потому что приложение «решило в runtime, что это бабушка».
4. **Verification (manual, `[hand]` AC)**: tester или AI открывает wizard на эмуляторе через skill `android-emulator`, walk through, confirms — wizard соответствует senior-safe baseline (Article VIII §7: ≥ 56dp tap targets, ≥ 24sp text, ≥ 4.5:1 contrast). Это check'ает не реализацию wizard engine, а **конфиг profile'а** — он properly задаёт senior-safe styling.

**Что закрывает**: US-6, SC-007 (`[hand]` AC), Article VII §13 (no condition logic per actor), Article VIII §7.

**Trouble case 6.b — TalkBack включён**: actionable elements озвучиваются, focus order осмыслен. Это **в конфиге профиля** (semantics descriptors), не в условной логике.

**Trouble case 6.c — Системный font scale 150%**: все строки помещаются (тестируется в senior-safe verification).

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Fresh install → wizard → working home screen (Priority: P1)

`Assisting` устанавливает APK на телефон `primary user`'а. Открывает приложение. Cold-start ≤ 2 сек до первого экрана wizard'а. Engine читает `simple-launcher.wizard.manifest.json` через `ConfigSource`, вычисляет pending steps через **config-check master** (`computePending`) — проверяет `SystemSettingPort.status()` per SystemSetting step + `UserPreferencesStore.current()` per UIChoice step, исключает уже applied. Assisting проходит pending steps. По завершении wizard'а ≤ 1 сек — `HomeActivity` показывает реальные плитки из выбранного tile.set + screen.layout композиции.

**Why this priority**: главный demo-критерий MVP. Без US-1 продукта не существует.

**Independent Test**: эмулятор `pixel_5_api_34`, fresh install APK, открыть → wizard → пройти 3 mandatory + 1 optional → home screen с плитками виден.

**Acceptance Scenarios**:

1. **Given** свежеустановленный APK на эмуляторе, никакого state в DataStore, **When** assisting открывает app первый раз, **Then** wizard launcher активность открывается ≤ 2 сек после tap'а на иконку; первый pending step показан (ROLE_HOME).
2. **Given** assisting прошёл все 3 mandatory шага без skip'а, optional PairAdmin skipped, **When** wizard завершается, **Then** `HomeActivity` открывается ≤ 1 сек, рендерит `classic-6` tile.set поверх `3x4-classic` screen.layout. Плитки реально содержат `actionType` записи из tile.set (placeholder без contactId — реальные контакты в TASK-9).
3. **Given** assisting на шаге ROLE_HOME, **When** swipes app из recents (kill), **Then** при повторном open wizard продолжается с того же шага (через `WizardCheckpoint`, F-3 path).
4. **Given** wizard завершён, ROLE_HOME granted, **When** assisting нажимает Home button на устройстве, **Then** наш `HomeActivity` открывается, плитки отрисованы из применённой композиции.

---

### User Story 2 — Config-check master skips applied steps (Priority: P1)

`Assisting` вручную выдал ROLE_HOME через системные Android Settings **до** запуска wizard'а (например, во время предыдущей сессии). Открывает app. Engine на startup'е вызывает `computePending(manifest)`, который вызывает `SystemSettingPort.status("android.role.home")` → возвращает `Applied`. Step ROLE_HOME исключается из pending. Wizard показывает только оставшиеся pending steps (tileSet + POST_NOTIFICATIONS + optional PairAdmin).

**Why this priority**: validates Article VII §14 config-check master pattern. Без US-2 wizard стал бы показывать уже-применённые настройки, ломая trust (Article III §7 stability).

**Independent Test**: emulator, manually grant ROLE_HOME через `adb shell cmd role add-role-holder android.app.role.HOME com.launcher.app` → fresh launch app → wizard does NOT show ROLE_HOME step, переходит сразу к tileSet.

**Acceptance Scenarios**:

1. **Given** ROLE_HOME уже granted на устройстве, остальные настройки в дефолте, **When** app launches и engine computes pending, **Then** wizard показывает только tileSet + POST_NOTIFICATIONS + optional PairAdmin, без шага ROLE_HOME.
2. **Given** все mandatory settings applied (включая через non-wizard paths), POST_NOTIFICATIONS granted через runtime ask из другой части app, tileSet выбран ранее, **When** app launches, **Then** wizard не показывается вообще — engine routes directly to HomeActivity.
3. **Given** новая версия bundled JSON ships с новой записью в `android-pool.json` (например, `android.permission.READ_PHONE_STATE`) и `simple-launcher.json` обновлён с этим step'ом, **When** app launches после update, **Then** wizard показывается как donastroika — только новый step (existing settings still detected as applied).
4. **Given** `SystemSettingPort.status()` для какого-то setting'а возвращает `Indeterminate` (нет Programmatic detection), **When** engine computes pending, **Then** step **включён** в pending (graceful — лучше переспросить чем silently skip).

---

### User Story 3 — App-level locale override holds against system locale change (Priority: P1)

`Assisting` в wizard'е выбирает русский для `primary user`'а — бабушки (auto-detected было English, override to Russian). Wizard завершён. Через месяц `primary user` случайно меняет system locale Android на English (зашла в Settings → Languages, тыкнула не туда). **Наш launcher остаётся на русском** — application-level locale override через `AppCompatDelegate.setApplicationLocales()` имеет приоритет (Article III §7).

**Why this priority**: stability для elderly. Без US-3 пользователь получает «surprise English UI» которое не понимает.

**Independent Test**: эмулятор с system locale `en-US`, fresh install → wizard → выбрать `ru` на language step (или auto-detect и потом switch) → finish wizard → home screen на русском → `adb shell setprop persist.sys.locale en-US` (или Android Settings → Languages → English) → kill app → reopen → home screen **всё ещё на русском**.

**Acceptance Scenarios**:

1. **Given** system locale `en-US`, **When** wizard runs на первом launch'е, **Then** language auto-detected `en`; engine saves `UserPreferences.languageOverride = "en"` AND calls `AppCompatDelegate.setApplicationLocales(LocaleListCompat.create(Locale.forLanguageTag("en")))`.
2. **Given** wizard завершён, `UserPreferences.languageOverride = "ru"`, **When** assisting / primary user меняет system locale на `en-US` в Android Settings, **Then** наш launcher остаётся на русском после restart'а.
3. **Given** wizard завершён с `languageOverride = "ru"`, **When** primary user открывает наш Settings (через future entry) и сбрасывает override, **Then** app начинает follow system locale.
4. **Given** Android < 13 (API < 33), где `AppCompatDelegate.setApplicationLocales` имеет ограничения, **When** locale override applied, **Then** **fallback** на restart-time locale switching через config change (стандартный pre-API-33 path); app не падает.

---

### User Story 4 — Pairing handshake в wizard (Priority: P2)

`Assisting` на optional шаге `PairAdmin` нажимает «Соединиться с админом». Запускается QR-сканер (через spec 007 `PairingActivity`). Admin device показывает QR с своего устройства. `Assisting` сканирует с primary user device. Trust handshake завершён (через `LinkRegistry.activate()` + spec 007 ConsentScreen). Wizard продолжается → home screen.

**Why this priority**: pairing = killer feature продукта. P2 потому что (а) optional шаг (skip без последствий — нет банера), (б) cloud config push deferred до TASK-8 — pairing demonstrate'ит только trust establishment.

**Independent Test**: two эмулятора (или эмулятор + физическое устройство), на одном — admin profile (TASK-8 stub в будущем; в TASK-7 — manual QR generation utility), на другом — primary user в wizard'е → tap PairAdmin → сканер открывается → QR отсканирован → ConsentScreen → consent.allow → `LinkRegistry.activate()` → wizard продолжается.

**Acceptance Scenarios**:

1. **Given** wizard на шаге PairAdmin, **When** assisting нажимает «Соединиться», **Then** `PairingActivity` (spec 007) запускается через explicit intent.
2. **Given** PairingActivity успешно отсканировала QR и user'у показан ConsentScreen, **When** assisting нажимает «Allow», **Then** `LinkRegistry.activate(linkId)` отрабатывает, wizard возвращается в managed-side flow и переходит к финальному шагу «Готово».
3. **Given** assisting нажимает «Пропустить» на PairAdmin шаге, **When** wizard продолжается, **Then** нет banner в Settings (Optional Silent semantics per Article VII §12); pairing доступен через Settings entry позже.
4. **Given** spec 007 `FirestoreLinkRegistry` не может достучаться до сервера (offline emulator), **When** assisting нажимает «Соединиться», **Then** wizard показывает «Нет интернета — попробуй позже» сообщение; step остаётся skippable.

---

### User Story 5 — Reboot persistence (Priority: P2)

После wizard'а: `UserPreferences.wizardCompletedPresets` содержит `"simple-launcher"` (per F-3 spec 015 FR-007). Reboot устройства → wizard не повторяется; FirstLaunchActivity routes directly to HomeActivity.

**Why this priority**: edge case robustness; механизм уже инфраструктурно сделан в F-3 и spec 010.

**Acceptance Scenarios**:

1. **Given** wizard завершён, applied configuration в `/config/current`, **When** `adb reboot`, **Then** после reboot Home button → наш `HomeActivity` (если ROLE_HOME granted), плитки те же.
2. **Given** wizard незавершён (kill в середине), **When** open app, **Then** wizard продолжается с того же шага через `WizardCheckpoint`.
3. **Given** factory reset устройства, **When** install и open app снова, **Then** wizard re-runs from start (DataStore wiped, UserPreferences reset).

---

### User Story 6 — Wizard uniform per profile config (senior-safe baked into simple-launcher) (Priority: P3)

Wizard выглядит и ведёт себя **одинаково** независимо от того, кто его проходит — помощник, бабушка, IT-support, AI agent через MCP. Различий по actor нет (constitution Article VII §13: no condition logic per actor). Senior-safe styling (большие кнопки, крупный шрифт, тёплая палитра) **запечён в конфиге** profile'а `simple-launcher` — не в условной runtime-логике. Verification — tester или AI walk through wizard на эмуляторе через skill `android-emulator`, confirms что конфиг profile'а produces senior-safe baseline per Article VIII §7 (≥ 56dp tap targets, ≥ 24sp text, ≥ 4.5:1 contrast).

**Acceptance Scenarios**:

1. **Given** эмулятор с TalkBack enabled, **When** проходит wizard, **Then** каждый actionable element имеет `contentDescription`; focus order осмыслен.
2. **Given** font size в Android Settings `largest` (150%), **When** проходит wizard, **Then** все strings помещаются без обрезания.
3. **Given** senior-safe walkthrough manual через skill `android-emulator`, **When** AI или тестировщик проходит wizard, **Then** все шаги понятны без помощи документации.

---

### Edge Cases

- **GMS-less устройство** (Huawei post-2019): spec 010 FR-042 hard-block screen **до** wizard'а через `GmsHardBlockActivity`. TASK-7 wizard не запускается. Regression test, не дублирует.
- **Bundled JSON corruption на disk**: `ConfigSourceResult.ParseError` или `IncompatibleVersion` → spec 010 / F-3 `PlayStoreFallbackActivity` шанс на recovery. TASK-7 inherits.
- **`SystemSettingPort.status()` exception** (Xiaomi MIUI quirks, etc.): adapter catches и возвращает `SettingStatus.CheckFailed(reason)`. Engine treats CheckFailed как `Indeterminate` → step pending (graceful).
- **Wizard kill во время системного диалога Android**: после restart engine restores `WizardCheckpoint`, retry того же шага. F-3 already handles.
- **OEM-specific системный диалог ROLE_HOME** (Samsung One UI confirm dialog): wizard ждёт результата через `SystemSettingPort.status()` после возврата фокуса — Android lifecycle обрабатывает.
- **Concurrent state change** (system app uninstall while wizard running): engine `computePending` re-checked on Activity RESUMED через cache invalidation.
- **`AppCompatDelegate.setApplicationLocales` not supported on API < 33**: fallback to `Configuration` change + Activity recreate path. inline TODO `// TODO(api-floor): when minSdk reaches 33, simplify locale path`.

---

## Requirements *(mandatory)*

### Functional Requirements

#### Part A — `simple-launcher.wizard.manifest.json` content authoring

- **FR-001**: System MUST update [`simple-launcher.wizard.manifest.json`](../../core/src/androidMain/assets/wizard/wizard-manifests/simple-launcher.json) с **явным** `steps` массивом, `autoOrder: false`. Engine читает manifest через существующий `ConfigSource` path (no change).
- **FR-002**: Manifest MUST содержать exactly these steps in this order:
  1. `SystemSetting` → `refId: "android.role.home"` — Required, `canSkip: false` (override pool default `true`).
  2. `UIChoice` → `refId: "tileSet"` — Required, `canSkip: false` (matches pool default).
  3. `SystemSetting` → `refId: "android.permission.POST_NOTIFICATIONS"` — Required, `canSkip: true` (per-profile override от pool default `false` чтобы primary user или helper мог пропустить и настроить позже). Auto-skip on API < 33 per existing pool `androidMinApi: 33`.
  4. `Custom` → `refId: "pair-admin"` — Optional Silent (`canSkip: true`, no banner после skip).
- **FR-003**: Per-step `canSkip` overrides per FR-002 #1 (ROLE_HOME false) and #3 (POST_NOTIFICATIONS true). Other pool defaults take precedence where no override.
- **FR-004**: Manifest MUST NOT include other pool entries (CALL_PHONE, accessibility-service, battery-optimization, hide-status-bar, language, theme, fontScale, grid, screenLayout). Those remain Optional Silent — available in Settings, not promoted in wizard.

#### Part B — Pool schema v1 → v2: declarative `check` + `apply` dispatch

- **FR-005**: System MUST bump `system-settings.pool` wire format `schemaVersion: 1` → `2`. Backward-compatible read: v1 entries (no `check` block) fall through to legacy hardcoded dispatch (deprecated; kept until all bundled v1 entries migrated to v2 then removed).
- **FR-006**: v2 `SystemSettingEntry` MUST contain optional `check: CheckSpec?` and `apply: ApplySpec?` blocks. Existing `mechanism + deepLink + detectionStrategy` fields remain for v1 compat.
- **FR-007**: `CheckSpec` sealed class в `core/commonMain/api/wizard/data/` with `@JsonClassDiscriminator("kind")`:
  ```kotlin
  @JsonClassDiscriminator("kind")
  @Serializable
  sealed class CheckSpec {
    @Serializable @SerialName("android-role")
    data class AndroidRole(val role: String) : CheckSpec()
    @Serializable @SerialName("android-permission")
    data class AndroidPermission(val permission: String) : CheckSpec()
    @Serializable @SerialName("android-special-permission")
    data class AndroidSpecialPermission(val kind: String) : CheckSpec()  // ignore_battery_optimizations, etc.
    @Serializable @SerialName("android-accessibility-service")
    data class AndroidAccessibilityService(val componentName: String? = null) : CheckSpec()
    @Serializable @SerialName("android-package-home")
    data class AndroidPackageHome(val packageName: String? = null) : CheckSpec()  // null = self
  }
  ```
  Variants для других платформ (iOS, Android TV) — TODO inline, добавляются когда соответствующие adapter модули материализуются.
- **FR-008**: `ApplySpec` sealed class analogous to `CheckSpec`:
  ```kotlin
  @JsonClassDiscriminator("kind")
  @Serializable
  sealed class ApplySpec {
    @Serializable @SerialName("standard-permission-request")
    data class StandardPermissionRequest(val permission: String) : ApplySpec()
    @Serializable @SerialName("android-role-request")
    data class AndroidRoleRequest(val role: String) : ApplySpec()
    @Serializable @SerialName("settings-deep-link")
    data class SettingsDeepLink(val action: String, val packageScoped: Boolean = false) : ApplySpec()
    @Serializable @SerialName("in-app-only")
    data object InAppOnly : ApplySpec()
  }
  ```
- **FR-009**: `AndroidSystemSettingAdapter` MUST register handler registry as `Map<KClass<out CheckSpec>, CheckHandler>` and `Map<KClass<out ApplySpec>, ApplyHandler>` через Koin DI (`coreModule`). Each handler — small class в `core/androidMain/adapters/wizard/handlers/`.
- **FR-010**: When `SystemSettingPort.status(settingId)` called, adapter looks up entry from pool, if `entry.check != null` → dispatch to handler matching `CheckSpec` variant class; else fall back to legacy `mechanism`-based dispatch (v1 compat).
- **FR-011**: `android-pool.json` MUST migrate all 6 existing entries to v2 format with `check` and `apply` blocks. Example for ROLE_HOME:
  ```json
  {
    "id": "android.role.home",
    "criticality": "Required",
    "canSkip": true,
    "androidMinApi": 29,
    "check": { "kind": "android-package-home" },
    "apply": { "kind": "android-role-request", "role": "HOME" },
    "labelKey": "system_setting_role_home_label",
    "descriptionKey": "system_setting_role_home_desc"
  }
  ```
- **FR-012**: Konsist fitness function MUST verify `core/commonMain/api/wizard/data/CheckSpec.kt` does NOT import Android types (commonMain isolation per CLAUDE.md rule 1 + constitution Article VII §15).

#### Part C — Engine `computePending` (config-check master)

- **FR-013**: `WizardEngine` interface MUST gain method:
  ```kotlin
  suspend fun computePending(manifest: WizardManifest): List<StepEntry>
  ```
  Implementation in `WizardEngineImpl`: for each `StepEntry` in manifest's ordered steps, query state per stepType:
  - `SystemSetting` → `systemSettingPort.status(entry.refId)` — if `Applied` → exclude; else include.
  - `UIChoice` → `userPreferencesStore.current()` — check if value present and valid against current pool's choices; if valid → exclude; else include.
  - `TutorialHint` → `dismissedHintsStore.isDismissed(entry.refId)` — if dismissed → exclude.
  - `Custom` → always include (no generic state check; Custom step handler decides).
- **FR-014**: `WizardEngine.run(manifest)` MUST call `computePending(manifest)` as **pre-flight**. If returned list is empty → return `WizardOutcome.Completed` immediately without traversal. If non-empty → traverse only pending steps. **Replaces** the existing linear traversal of all manifest steps in `WizardEngineImpl.run()`.
- **FR-014a**: Settings UI MUST include a button «Пройти все настройки пошагово» (label key `settings_walk_through_all_label`) that re-launches wizard UI in **walk-through mode**: traverses **all** manifest steps (not filtered by `computePending`), with each step pre-populated with current value as default and offering «Оставить» / «Изменить» actions. Walk-through mode requires a new `WizardEngine` parameter or sibling method (e.g., `engine.runWalkThrough(manifest)`). Visible state changes per step are saved immediately (not transactional) per Trouble case 5.b. **UX precedent**: Apple Setup Assistant on factory reset, Windows OOBE on rerun, TurboTax sections walk (per Article XV §14).
- **FR-015**: `WizardActivity` SHOULD also expose `computePending(manifest)` ahead of `engine.run(...)` to **decide** whether to launch wizard at all vs route directly to `HomeActivity`. This complements `UserPreferencesStore.isWizardCompleted(presetId)` boolean: even if `isWizardCompleted == true`, if a profile/pool update added new pending steps, wizard runs as donastroika.
- **FR-016**: `diffPending(savedCompletedManifest, currentManifest)` method on `WizardEngine` is **deprecated** by FR-013 (snapshot-based approach not used). Kept for backward compat; documented as deprecated; remove in TASK-22 (Optional Step Reminder System) or sooner.

#### Part D — App-level locale override

- **FR-017**: When wizard's language step completes (auto-detected or user-chosen via Settings — even though language is not a wizard step in TASK-7 per FR-002, this FR applies whenever `UserPreferences.languageOverride` is updated through any path), `WizardActivity` (or post-engine.run integration glue) MUST call `AppCompatDelegate.setApplicationLocales(LocaleListCompat.create(Locale.forLanguageTag(languageOverride)))`.
- **FR-017a**: Settings UI MUST display a locale indicator when app-level locale override diverges from system locale: «Язык приложения: Русский (системный Android: English)» (or equivalent localized format). When override matches system locale OR no override is set → indicator hidden. Helper opening Settings later understands the divergence without confusion.
- **FR-018**: At app cold-start (in `Application.onCreate()` or `FirstLaunchActivity` pre-route logic), the same call MUST apply persisted `UserPreferences.languageOverride` if present. This ensures restart-after-system-locale-change preserves app-level choice.
- **FR-019**: `AppCompatDelegate.setApplicationLocales` is API 33+ semantically distinct. On API < 33, the platform persists the override differently but the call is still valid (AppCompat shim handles). No special branching needed in domain code per CLAUDE.md rule 1; AppCompat fallback handled by AndroidX.
- **FR-020**: Konsist fitness function MUST verify `AppCompatDelegate` is **not** called from `commonMain` — only from `androidMain` adapter or `app/` integration glue (constitution Article VII §15 multi-platform seam).

#### Part E — Cache for `SystemSettingPort.status()`

- **FR-021**: `AndroidSystemSettingAdapter` MUST implement TTL cache for `status()` results: `Map<settingId, Pair<SettingStatus, Instant>>` with TTL 30 seconds.
- **FR-022**: Cache MUST be invalidated on `Lifecycle.RESUMED` of any Activity that uses `SystemSettingPort` (WizardActivity, future Settings activity, etc.). Pattern follows spec 010 FR-020a.
- **FR-023**: Cache invalidation MUST also occur when `applyOrPrompt()` returns `ApplyResult.Applied` (we just changed state, refresh).
- **FR-024**: Inline TODO at cache site: `// TODO(perf): if pool grows beyond ~30 entries, move to background-refresh coroutine`.

#### Part F — Localization

- **FR-025**: All localization keys referenced in updated `simple-launcher.wizard.manifest.json`, in v2-migrated pool entries, and в bundled `screen.layout` / `tile.set` MUST have records in `strings.xml` for en + ru. Existing F-3 strings reused; new keys for `Custom("pair-admin")` step added.
- **FR-026**: Missing key → fallback to EN per ADR-004; никакого hardcoded русского текста в Kotlin-коде / JSON-литералах.

#### Part G — Pairing integration as Custom step

- **FR-027**: `WizardEngineImpl` MUST register `Custom("pair-admin")` step handler via Koin DI. Handler launches `PairingActivity` (spec 007) through explicit intent and waits for result.
- **FR-028**: `PairAdmin` step result mapping: pairing successful → `StepResult.AnswerCaptured(JsonPrimitive("paired"))`; user cancelled / skipped → `StepResult.Skipped`; pairing error → `StepResult.Skipped` with toast notification (offline / server error).
- **FR-029**: Inline TODO at pair-admin step site: `// TODO(TASK-8): admin config push activates here when TASK-8 lands; currently demonstrates trust handshake only`.

#### Part H — Multi-platform seam preservation

- **FR-030**: All work in TASK-7 MUST preserve the constitution Article VII §15 multi-platform seam:
  - `CheckSpec`, `ApplySpec`, `WizardManifest`, `StepEntry`, all pool data classes — `core/commonMain`.
  - `SystemSettingPort`, `UserPreferencesStore`, `ConfigSource`, `WizardEngine` — `core/commonMain`.
  - Handlers + adapter + AppCompatDelegate calls + Android-specific UI — `core/androidMain` or `app/`.
- **FR-031**: Inline TODO at adapter site: `// TODO(multiplatform): IosSystemSettingAdapter — TASK-26 / TASK-29 — both ship as new adapters in iosMain / androidTvMain without changing engine, ports, or commonMain CheckSpec sealed class`.

#### Cross-cutting

- **FR-032**: TASK-7 MUST NOT add new Gradle modules dedicated to simple-launcher (constitution Article VII §13).
- **FR-033**: TASK-7 MUST NOT add code branches keyed on `presetId == "simple-launcher"` в business logic (constitution Article VII §13).
- **FR-034**: TASK-7 MUST NOT introduce new `ConfigKind` enum entries (constitution Article VII §10) — uses existing five.
- **FR-035**: TASK-7 MAY add new ports (`CheckHandler`, `ApplyHandler`) where this clearly reduces handler-dispatch complexity (rule 4 MVA exception justified because handler-per-CheckSpec-variant is the Capability Registry seam — pre-replacing for future Article VII §10 evolution + MCP integration).

### Key Entities

**Updated by TASK-7** (Kotlin code changes):
- `WizardEngine.computePending(manifest): List<StepEntry>` — new method.
- `CheckSpec` — new sealed class в `core/commonMain/api/wizard/data/`.
- `ApplySpec` — new sealed class в `core/commonMain/api/wizard/data/`.
- `CheckHandler` — new port в `core/commonMain/api/wizard/`.
- `ApplyHandler` — new port в `core/commonMain/api/wizard/`.
- `SystemSettingEntry` (v2 wire format) — gains `check`, `apply` optional fields.

**Existing** (not modified):
- `WizardManifest`, `StepEntry`, `ConfigKind`, `ScreenLayout`, `TileSet`, `UserPreferences`, `SystemSettingPool`, `UICustomizationPool` — F-3 data model.
- `WizardEngine`, `SystemSettingPort`, `ConfigSource`, `UserPreferencesStore` — F-3 ports (with `computePending` addition to `WizardEngine`).

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001 [backlog]**: Assisting установил APK на эмулятор → wizard первый pending step виден ≤ 2 сек после tap'а на иконку.
- **SC-002 [backlog]**: Assisting прошёл 3 mandatory + 1 optional шага → `HomeActivity` рендерит выбранную композицию (classic-6 поверх 3x4-classic) ≤ 1 сек после wizard exit'а.
- **SC-003 [backlog]**: ROLE_HOME уже granted через Android Settings до wizard'а → wizard не показывает ROLE_HOME step (config-check master в действии).
- **SC-004 [backlog]**: System locale change (Android Settings → Languages → English) после wizard'а с `languageOverride: ru` → app остаётся на русском после restart'а (Article III §7 stability).
- **SC-005 [backlog]**: Pairing с admin device в wizard'е завершился успешно → `LinkRegistry.activate()` записал link → home screen рендерится с paired state.
- **SC-006 [backlog]**: Перезагрузил устройство → wizard не повторяется; HomeActivity открывается с применённой композицией.
- **SC-007 [backlog]**: Senior-safe walkthrough на эмуляторе через skill `android-emulator` — assisting проходит wizard без подсказок (manual `[hand]` AC).
- **SC-008**: Roundtrip test проходит для pool v2 JSON (CLAUDE.md rule 5).
- **SC-009**: Backward-compat test проходит — v1 pool entries (no `check` block) читаются через v2 reader без потери fields.
- **SC-010**: Fitness function tests проходят: (a) нет новых Gradle модулей dedicated для simple-launcher; (b) нет `if (presetId == "simple-launcher")` branches в business logic; (c) нет новых `ConfigKind` enum entries; (d) `CheckSpec` / `ApplySpec` sealed classes не импортируют Android types; (e) `AppCompatDelegate` не called from commonMain.
- **SC-011**: APK size delta ≤ +150 KB (JSON schema v2 + handlers + Koin wiring + strings; no new bundled documents).
- **SC-012**: Engine integration test: simulate ROLE_HOME pre-applied + tileSet pre-set → `computePending` returns only POST_NOTIFICATIONS + PairAdmin → engine.run shows only these two steps.

### Out of Scope

- **OUT-001**: Admin App profile (TASK-8 / S-2). Cloud config push зарождается в TASK-8.
- **OUT-002**: Contact tiles content (TASK-9 / S-3 — placeholder tiles в TASK-7).
- **OUT-003**: SOS configuration (TASK-10 / S-4).
- **OUT-004**: Photo upload / display (TASK-11 / S-5).
- **OUT-005**: Caregiver remote invite (TASK-31 / V-6).
- **OUT-006**: Adaptive-UX presets (TASK-19 / P-4) — sub-feature внутри профиля per Project-Specific Direction §5.
- **OUT-007**: Google Sign-In step (deferred per decision 2026-06-15-deferred-cloud/01).
- **OUT-008**: Theme editing post-wizard через separate Settings entry. Default warm light applied; banner для re-prompt — future.
- **OUT-009**: Tutorial overlays / hints. TutorialHintManager port есть, конкретные hints в MVP не нужны.
- **OUT-010**: Pool choice migration policy when pool choices change (new backlog task).
- **OUT-011**: Analytics / diagnostic events real implementation. F-3 `DiagnosticEmitter` no-op stays.
- **OUT-012**: GMS-less Huawei special path — spec 010 FR-042 inherits.
- **OUT-013**: MCP / AI agent config authoring — TASK-33 + TASK-36.
- **OUT-014**: `WizardEngine.diffPending(savedCompletedManifest, currentManifest)` — deprecated by FR-013; removal planned for TASK-22 or sooner.
- **OUT-015**: Per-platform pool documents (iOS, Android TV) — added when adapter modules ship (TASK-26 / TASK-29).
- **OUT-016**: V1 → V2 pool entry migration tool (CLI / script). Migration done by hand in TASK-7 для 6 existing entries; future entries authored directly in v2.

---

## Assumptions

### Зависимости от других задач

- **A-1**: TASK-1 (F-3 Wizard Module + Localization) — Done. Verified 2026-06-24 — все ports, engine, pool'ы, Senior UI primitives, локализация infrastructure доступны.
- **A-2**: Spec 010 setup-assistant (historical / orphan по backlog'у) — code verified working in repo. `RoleHomeCheckAdapter`, `ConfigBackedFlowRepository` (ARCH-016 closure), `GmsHardBlockActivity`, `PlayStoreFallbackActivity`. TASK-7 opira'ется без prerequisite fix-up.
- **A-3**: Spec 007 pairing-and-firebase-channel (historical / orphan) — code verified working. `PairingActivity`, `LinkRegistry`, `FirestoreLinkRegistry`, `ManagedDevicesRegistry`. TASK-7's PairAdmin step launches existing UI.
- **A-4**: Spec 003 ui-skeleton (historical / orphan) — code verified working. `HomeActivity`, `RootComponent`, `FlowRepository`, `PresetRepository`. TASK-7 wizard exit routes to existing HomeActivity unchanged.

### Архитектурные принципы

- **A-5**: Profile-as-composition per constitution Article VII §9–15. TASK-7 — первая концентрированная валидация модели + первое внедрение config-check master pattern (Article VII §14).
- **A-6**: LOCAL mode device self-sufficiency per decision 2026-06-15-deferred-cloud. Wizard работает без сети (pairing требует сети, но это **optional** step с graceful skip).
- **A-7**: Personas — assisting led, aspirational secondary path = primary user. Senior-safe baseline always.
- **A-8**: Wire-format kinds текущего поколения per Article VII §10 + schemaVersion v1→v2 для pool documents.
- **A-9**: Stability over system-level changes per Article III §7. Locale, theme, font scale — applied values persist.
- **A-10**: MVP delivers base blocks (Article II §8), polish through JSON config later.

### Технические допущения

- **A-11**: Эмулятор `pixel_5_api_34` через skill `android-emulator` — primary local test path (memory `reference_compose_ui_test_api_mismatch.md` — избегаем API 35+).
- **A-12**: Koin DI module wiring — handlers registered as `factory<CheckHandler>(named("android-role")) { AndroidRoleCheckHandler() }` etc. Discovered through registry map injected into adapter.
- **A-13**: `AppCompatDelegate.setApplicationLocales()` works on all API levels we support (API 26+ per project), AppCompat shim handles pre-API-33 path.
- **A-14**: `Lifecycle.RESUMED` cache invalidation reuses existing spec 010 FR-020a hook — no new lifecycle observer infrastructure.

---

## Local Test Path *(mandatory)*

- **Emulator / device**: `pixel_5_api_34` через skill `android-emulator`. Fresh install verification flow.
- **Fake adapters used**:
  - `FakeConfigSource` (F-3 commonTest) — заменяет `BundledConfigSource` для unit tests.
  - `FakeSystemSettingPort` (F-3 commonTest) — конструируется с `Map<settingId, SettingStatus>` для symbol replay.
  - `InMemoryCheckpointStore` (F-3) — wizard checkpoint persistence без DataStore.
  - `RecordingDiagnosticEmitter` (F-3) — captures events для assertions.
  - `FakeLocaleProvider` (F-3) — override locale для locale-switching tests.
  - `FakeLinkRegistry` (spec 007 commonTest) — для PairAdmin step без реального Firestore.
- **Fixtures / seed data**:
  - Bundled JSON в `core/src/androidMain/assets/wizard/`: updated `simple-launcher.json` (explicit steps), v2-migrated `android-pool.json`, existing `ui-pool.json` / `3x4-classic.json` / `classic-6.json`. Production-ready.
  - `core/src/commonTest/resources/fixtures/pool-v1-fixture.json` — golden fixture для backward-compat read tests.
  - `core/src/commonTest/resources/fixtures/pool-v2-fixture.json` — golden fixture для roundtrip tests.
- **Verification command**:
  - Unit / contract: `./gradlew :core:test --tests *CheckSpec*` + `./gradlew :core:test --tests *RoundtripTest*` + `./gradlew :core:test --tests *BackwardCompatTest*` + `./gradlew :core:test --tests *ComputePendingTest*`.
  - Fitness function (Konsist): `./gradlew :core:test --tests *Task7ArchitectureTest*`.
  - Android instrumented: `./gradlew :app:connectedDebugAndroidTest --tests *SimpleLauncherE2ETest*`.
  - Emulator smoke (через skill `android-emulator`): `./gradlew :app:installDebug` → launch via adb → manual walkthrough.
- **Cannot-test-locally gaps**:
  - **Real ROLE_HOME OEM-specific dialogs** (Samsung One UI confirm, Xiaomi MIUI quirks) — inline TODO(physical-device).
  - **Real elderly walkthrough** verification — `[hand]` AC через skill `android-emulator` + senior-safe checklist; не automated.
  - **System locale change persistence on physical device** — baseline на эмуляторе, edge cases inline TODO(physical-device).
  - **Real pairing flow with two physical devices** — pair'нья spec 007 в TASK-7 demos через FakeLinkRegistry; реальный Firestore round-trip — TODO(physical-device).

---

## AI Affordance *(mandatory)*

Wizard сам по себе не содержит AI-specific surfaces в TASK-7 scope, но **архитектурно ready** для будущей MCP integration (per constitution Article VII §14):

- **Exposable capabilities** (future, через Capability Registry, TASK-33 deferred): `runWizardStep(stepId)`, `applyTileSet(tileSetId)`, `setTheme(themeId)`, `setLocale(localeTag)`, `applySystemSetting(settingId)`, `checkSystemSetting(settingId)`. Все — domain verbs.
- **`CheckSpec` and `ApplySpec` sealed hierarchies** — **doubles as capability surface schema**. Future MCP agent reads `CheckSpec` variants, knows что для каждой настройки можно вызвать соответствующий check/apply. Same JSON schema, same dispatch.
- **Provider-agnostic shape**: capabilities expressed через existing F-3 domain ports, без Gemini/OpenAI/Claude/MCP types (CLAUDE.md rule 1).
- **Out of scope for this spec**: AI provider implementation, LLM prompt design, MCP server — TASK-36 / FUTURE-SPEC-AI.
- **Inline TODO**: `// TODO(capability-registry): wizard CheckSpec/ApplySpec hierarchies double as MCP capability surface — TASK-33 (Capability Registry Foundation) + TASK-36 (AI provider implementations)`.

---

## OEM Matrix *(mandatory if feature touches device behavior)*

| OEM / surface | Known divergence | Mitigation in this spec | Verification source |
|---|---|---|---|
| Stock Android (Pixel) | baseline | — | emulator `pixel_5_api_34` через skill `android-emulator` |
| Samsung One UI | ROLE_HOME picker может добавлять confirm dialog после нашего request'а | `SystemSettingPort.status()` re-check после возврата фокуса через Lifecycle.RESUMED cache invalidation; inline TODO(physical-device) | TODO(physical-device) — Samsung Galaxy newer |
| Xiaomi MIUI | autostart manager + battery optimization (но FCM out-of-scope для TASK-7); ROLE_HOME baseline | inline TODO(physical-device); `PowerManager.isIgnoringBatteryOptimizations()` может бросать SecurityException — `CheckHandler` catches и возвращает `Indeterminate` | TODO(physical-device) — Xiaomi 11T |
| Huawei EMUI (GMS-less) | spec 010 FR-042 hard-block **до** wizard'а — TASK-7 wizard не запускается | Delegation to spec 010 FR-042 + `GmsHardBlockActivity` | spec 010 FR-042 |
| Stock Android API < 13 | `POST_NOTIFICATIONS` не существует — auto-skip step через `androidMinApi: 33` в pool | F-3 engine handles | emulator `pixel_5_api_31` regression test |
| Stock Android API < 33 | `AppCompatDelegate.setApplicationLocales` имеет ограничения | AppCompat shim handles persistence через alternate path; same API call | emulator `pixel_5_api_31` |

---

## Cross-cutting concerns surfaced from clarify

1. **Config-check master (Article VII §14)**: ключевая architectural innovation в TASK-7. Заменяет F-3 linear traversal pattern на state-of-device check per step. Validates что settings applied through any path (wizard, Android Settings direct, admin push, AI agent) are equally honoured.
2. **Pool schema v2 + sealed CheckSpec/ApplySpec**: declarative dispatch reduces hardcoded `when(settingId)` to handler registry. Pre-aligned с future MCP capability surface (TASK-33).
3. **Article III §7 stability**: locale override через `AppCompatDelegate.setApplicationLocales()` — first concrete enforcement of user-applied-state-over-system-shifts principle.
4. **Multi-platform seam (Article VII §15)**: TASK-7 architecturally preserves seam — sealed CheckSpec/ApplySpec in commonMain, handlers per platform module. iOS / Android TV adapters ship later without engine changes.
5. **Pairing as Custom step**: validates что Custom step type из F-3 sealed StepType расширяемо через DI. Spec 007 UI re-used unchanged.
6. **MVP definition (Article II §8)**: TASK-7 ships base blocks (wizard, profile, config-check, locale persistence) end-to-end working. Polish (theme picker UI, multiple tileSets, additional adaptive-UX presets) — post-MVP through JSON.

---

## Затрагиваемые внешние артефакты

- **TASK-1 (F-3) bundled assets** — `core/src/androidMain/assets/wizard/wizard-manifests/simple-launcher.json` modified (autoOrder=true → explicit steps); `core/src/androidMain/assets/wizard/system-settings/android-pool.json` migrated to schemaVersion 2.
- **TASK-1 (F-3) code** — `WizardEngineImpl.run()` modified to call `computePending()` pre-flight; `WizardEngine` interface gains `computePending` method; `AndroidSystemSettingAdapter` adds handler registry; `WizardActivity` adds AppCompatDelegate.setApplicationLocales call.
- **Spec 007 PairingActivity** — no modification; launched as explicit intent from new `PairAdminStepHandler` in TASK-7.
- **`app/src/main/AndroidManifest.xml`** — no new Activities (PairingActivity already registered).
- **`docs/dev/server-roadmap.md`** — no new entries (existing TODOs sufficient).
- **`docs/product/glossary.md`** — version note may be added on §3 noting pool schema v2 evolution.

---

## TL;DR (по-русски, для новичка и для будущего AI)

**Суть.** TASK-7 = ship `simple-launcher` профиль как композицию bundled JSON-документов поверх существующего F-3 engine (TASK-1 Done), + закрыть две архитектурные дыры в F-3, которые мешают полноценной модели profile=композиция: (а) добавить config-check master pattern (engine на startup'е проверяет current device state per step, skip applied — replaces linear traversal); (б) добавить app-level locale override через `AppCompatDelegate.setApplicationLocales()` (без этого Article III §7 stability не работает); + schema bump pool v1→v2 с declarative `check` / `apply` блоками вместо hardcoded `when(settingId)`.

**Конкретика, которую стоит запомнить:**
- **3 mandatory + 1 optional steps**: (1) ROLE_HOME canSkip:false override, (2) tileSet Required, (3) POST_NOTIFICATIONS canSkip:true override, (4) PairAdmin optional silent. Language → auto-detect (no wizard step). Theme → default warm light. Other settings → Optional Silent (Settings only).
- **Pool schema v1 → v2**: добавляется `check: CheckSpec?` и `apply: ApplySpec?` блоки. Sealed `CheckSpec` в commonMain с `@JsonClassDiscriminator("kind")`. Handlers в Koin DI registry. v1 entries backward-compat читаются через legacy dispatch path.
- **Engine `computePending(manifest)` — новый метод**. Заменяет linear traversal: за каждый step queries SystemSettingPort.status() / UserPreferences / DismissedHintsStore, исключает applied. Wizard показывает только pending. Donastroika при config update — автоматически через тот же mechanism.
- **`diffPending(savedCompletedManifest, currentManifest)` — deprecated**. Snapshot-of-manifest approach отвергнут; state-of-device — единственный источник правды (constitution Article VII §14).
- **Verified 2026-06-24**: spec 010 / 007 / 003 orphan-зависимости фактически работают в коде. TASK-7 строится поверх existing functional foundation без prerequisite fix-up.
- **17 NEEDS CLARIFICATION → все resolved** в `## Clarifications` table. Plan ready.
- **Effort**: Medium+ (~2-3 weeks) — pool schema v2 + computePending + locale override + cache + pairing wiring + tests. Pairing UI уже сделана (spec 007); просто wire через DI.

**На что смотреть с осторожностью:**
- **Pool schema v1→v2 — wire-format one-way door**: backward-compat read должен работать минимум один major release. Migration test mandatory.
- **Engine `computePending` замещает linear traversal в `WizardEngineImpl.run()`** — поведение wizard'а меняется существенно: пользователь не видит уже-применённые шаги. Если SystemSettingPort.status() даёт false positive (например, OEM quirk) — wizard может silently skip step. Mitigation: `Indeterminate` treated as pending (graceful).
- **`AppCompatDelegate.setApplicationLocales()` API < 33 path** — AppCompat shim handles, но физическое поведение на разных OEM может отличаться. inline TODO(physical-device).
- **Spec 007 pairing depends on Firestore** (cloud); TASK-7 LOCAL mode policy. PairAdmin step — optional silent skip if no network. Cloud config push не работает до TASK-8 — pairing demonstrate'ит только handshake.
- **«MCP / AI agent capability» — architectural ready, not implemented**. TASK-33 / TASK-36 carry implementation. Inline TODO at CheckSpec/ApplySpec sites.
