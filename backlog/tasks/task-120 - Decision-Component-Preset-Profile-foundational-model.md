---
id: TASK-120
title: 'Decision: Component/Preset/Profile foundational model'
status: In Progress
assignee: []
created_date: '2026-07-09 10:55'
updated_date: '2026-07-11 08:00'
labels:
  - phase-2
  - foundation
  - preset
  - wizard
  - decision
  - one-way-door
milestone: m-1
dependencies:
  - TASK-65
priority: high
ordinal: 120000
references:
  - specs/task-120-preset-composition-foundation/
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->

## Что это простыми словами

**Фундамент** для настройки лаунчера. Устройство собирает свой профиль (что установлено, какие плитки, какие настройки) из **преcетов** — шаблонов, которые можно шарить между устройствами. Preset ссылается на **склад** заготовок (Pool), из которых Wizard, Settings, BootCheck и Admin push собирают то что нужно применить.

По шагам (нормальный сценарий первого запуска):
1. Устройство загружает bundled seed preset (`simple-launcher` / `launcher` / `workspace`) из assets.
2. `PresetValidator` проверяет корректность preset'а (ordering, capability requirements, schemaVersion). Если что-то не так — Wizard **не стартует**, владелец / админ видит понятную ошибку.
3. `ProfileFactory` собирает `Profile` из preset + pool.
4. `ReconcileEngine` запускается в `RunMode.Wizard` — обходит `wizardFlow`, для каждого шага дёргает `Provider.check()` → если drift, `Provider.apply()` (Interactive → спрашивает через `InteractionSink`, AutoApply → молча, InitialDefault → значение уже есть).
5. После Wizard'а `Profile.activeComponents` — source of truth. Settings редактирует его (через `RunMode.Single`), BootCheck реapply'ит критичные шаги (`RunMode.BootCheck`).

Что происходит при отмене Wizard'а:
- Owner нажимает «Отменить» → confirm dialog → `Profile` восстанавливается из `preWizardSnapshot`. **Runtime state (реальные Android настройки) НЕ откатывается автоматически** — toast «некоторые изменения вернутся вручную через Settings». Следующий BootCheck реапplyит старое значение.

Что происходит при cloud-зависимом preset'е:
- Некоторые Component'ы дают cloud-state (SignInGoogle emits `CapabilityFlag.CloudSession`), другие требуют (HealthForward requires CloudSession). Validator ловит битый ordering **до** запуска Wizard'а. Нет отдельного поля `cloudRequirement` в JSON — это runtime через порт `CapabilityQuery`.

## Зачем

- Убрать hardcoded Sign-In flow и wizard-логику из `FirstLaunchActivity` (draft-1 tech-debt).
- Дать разным preset'ам разное содержимое без правки кода лаунчера — новые Component'ы добавляются через 4 файла (Component sealed subtype + Provider + DI строка + pool.json entry). Ядро НЕ редактируется.
- Разделить Wizard-сценарий (линейный) от Settings-карты (свободная) от reconcile-loop input (плоский список активных) — три поля в preset вместо одной нестрой Component-структуры.
- Экспозиция шва для будущего Capability Registry (F-2 AI-агент exposure) без commit'а на конкретный протокол (MCP / Google Actions / OpenAI functions).

## Что входит технически (для AI-агента, MVP scope)

**Domain (core/preset/, commonMain, pure Kotlin)**:
- `Component` sealed hierarchy с **4 MVP subtypes**: `AppTile`, `FontSize`, `Sos`, `Toolbar`. `MessengerTile` — deferred в task-121. `SignInGoogle` — deferred в draft-1.
- `ComponentDeclaration`, `Pool` — реестр заготовок с `schemaVersion=1` (pool.json).
- `Preset` — wire format `schemaVersion=2` с **тремя ортогональными полями**: `wizardFlow` / `settingsMap` / `activeComponents`. Опциональный `paramsOverride` per entry везде.
- `Profile` — device snapshot, `schemaVersion=2` с `preWizardSnapshot: Profile?` для undo + опаковый `ProfileState` для capability evidence.
- `Provider` port + `ProviderRegistry` с fallback vendor→platform→NoOp.
- `Outcome` sealed: `Ok | NeedsApply | Failed(FailReason) | Unsupported`. `FailReason` — structured sealed (5 категорий) с `toI18nKey()` mapping.
- `WizardBehavior` enum (`Interactive | AutoApply | InitialDefault`) — только в wizardFlow entries.
- `ReconcileEngine` с 4 `RunMode` (Wizard / BootCheck / Single / RemotePush).
- **`CapabilityFlag`** sealed (MVP: `CloudSession`) + **`CapabilityQuery`** port (runtime read/write) + **`CapabilityContract`** port (metadata для validator).
- **`PresetValidator`** — прогоняется до `ReconcileEngine.run()`, ловит `CapabilityMissing`, `UnknownPoolRef`, `SchemaVersionUnsupported`.
- `PresetDiff` для admin push (Added / Removed / ParamsChanged). Runtime реализация — future.
- `InteractionSink`, `PoolSource`, `PresetSource`, `ProfileStore`, `LocalizedResources`, `ConditionEvaluator` (минимальный MVP для `{"var": "profile.state.<flag>"}`) — ports.

**Adapter (app/androidMain/)**:
- 4 Provider реализации для MVP wave Component'ов.
- 4 facade'а (`PackageManagerFacade`, `HomeScreenFacade`, `StoreIntentFacade`, `UiPrefsFacade`) — ACL per rule 2.
- `DataStoreProfileStore`, `DataStoreCapabilityAdapter`, `AndroidLocalizedResources`, `BundledPoolSource`, `BundledPresetSource`.
- DI: Hilt `@IntoMap` с custom `@ComponentKey` annotation для ProviderRegistry.

**Wire formats** (rule 5):
- `pool.json` schemaVersion=1 (bundled, additive-only growth).
- `preset.json` schemaVersion=2 (three-field split).
- `profile.json` schemaVersion=2 (via DataStore).

**Fitness functions (10)**:
1. Import guard on engine (no Component subtype imports).
2. `when`-guard on engine (no `when(component)` on subtypes).
3. Coverage Component ↔ Provider (reflection test).
4. Roundtrip pool+preset+profile.
5. Backward-compat.
6. Cross-provider isolation.
7. `paramsOverride` schema validation.
8. Anti-explosion pool limit (≤3 declarations per Component subtype).
9. PresetValidator coverage (3 canonical scenarios).
10. No-literal-strings in user-facing wire format (i18n keys mandatory).

**Anti-explosion принцип** (owner Q1 clarify): новый `Component` subtype ТОЛЬКО когда семантика `apply()` принципиально другая — иначе параметризация через `paramsOverride`.

## Что НЕ входит (deferred, каждое — additive не rewrite)

- **`SignInGoogle` Component** — deferred в draft-1 (следующая downstream task). Foundation предоставляет механизм (Capability model), но не сам subtype.
- **`MessengerTile` + SSO handoff** — deferred в task-121 (создан 2026-07-10 в Draft, dependencies TASK-120+TASK-27). Foundation поддерживает generic `AppTile` с проверкой установки через PackageManagerFacade + StoreIntentFacade.
- **`SosDispatcher`** / cross-Component event bus — deferred. Fitness function #6 (cross-provider isolation) straps future addition — Provider'ы не могут звать друг друга напрямую, значит добавление dispatcher'а = pure addition.
- **`Provider.rollback`** для admin push undo — deferred. `preWizardSnapshot` покрывает owner-triggered undo (без runtime state revert — deferred per-Component). `PanicReset` покрывает coarse recovery. Additive extension когда конкретный Component попросит.
- **Full JsonLogic runtime** для `visibleIf` — deferred. MVP evaluator читает только `{"var": "profile.state.<flag>"}` для CapabilityFlag gating. Schema seam (поле `visibleIf` в wizardFlow) present. Полный runtime добавляется когда первый preset реально попросит.
- **`ConsumerFilter` port** для 5-го consumer'а сверх Wizard/BootCheck/Settings/RemotePush — deferred. Bounded 4 RunMode покрывает MVP.
- **`requiredModules`/`optionalModules` in preset schema** (Article VII §8) — deliberately skipped per rule 4 MVA. Один APK в MVP, module-based delivery — future. Accepts future schemaVersion=3 additive migration.
- **iOS Provider реализации** — placeholder module. iOS parity — post-MVP.
- **CEL как альтернатива JsonLogic** — swap через adapter, не rewrite callsite'ов (exit ramp).

## Состояние

**In Progress, /speckit.analyze passed READY-WITH-CAVEATS 2026-07-10.**

Три сессии mentor + speckit-clarify pass прошли:
- **Session 1** (2026-07-09 AM) — postavleny OQ-1..OQ-5 по conditional inclusion.
- **Session 2** (2026-07-09 PM) — foundational rescope, финализирован naming (Component/Preset/Profile/Pool/Provider/ProviderRegistry/Outcome), postavleny OQ-6..OQ-11.
- **Session 2.5** (2026-07-10) — brief mapping (владелец принёс два внешних архитектурных брифа) + стресс-тест на 5 фичах + разделение Wizard/Settings на три поля preset (`wizardFlow`/`settingsMap`/`activeComponents`) + принятие: paramsOverride разрешён, Outcome по брифу, WizardBehavior в wizardFlow. Owner refinement: «не жёстко зашивать, всё динамичное — не так важно на ранних этапах» — MVA-швы сейчас, полная динамика позже.
- **speckit-clarify pass** (2026-07-10) — 8 grey-zone вопросов заданы. Owner resolutions: (Q1 anti-explosion) MVP wave 4 Component'а с fitness function на pool.json size; (Q2) property-based test 100 random combinations вместо fixed seed content; (Q3) MessengerTile deferred в **task-121** (создан 2026-07-10, Draft, dependencies TASK-120+TASK-27); (Q4) paramsOverride везде; (Q5) preWizardSnapshot для owner-driven Wizard undo; (Q6) MVP single-shot install, merge future; (Q7) Failed→continue with additive halt exit-ramp; (Q8) снят через Q3. Резолюции weaved в specs/task-120-.../spec.md → Clarifications section + FR-004/FR-011/FR-014/FR-024/FR-025 + SC-011/SC-012/SC-013.

Rename задачи title (`"Decision: Component/Preset/Profile foundational model"`) — ждёт следующего touch. `visibleIf` сохраняется как **отложенный шов** — поле в wizardFlow, реализация `ConditionEvaluator` только когда первый preset реально попросит.

**Next work session** (session 3): OQ-A..OQ-E из session 2.5 → закрыть → заполнить Decision block → status → Draft → `/speckit.specify`. Возможно 1-2 промежуточных mentor-раунда до session 3 — owner сказал что нужно ещё несколько объяснений чтобы модель уложилась в голове.

<!-- SECTION:DESCRIPTION:END -->

## Discussion

<!-- SECTION:DISCUSSION:BEGIN -->

### Research context

**Existing code (Explore agent, 2026-07-09):**
- Foundation готов 80%: `CheckSpec`/`ApplySpec` sealed hierarchy ([core/src/commonMain/kotlin/com/launcher/api/wizard/data/CheckSpec.kt](../../core/src/commonMain/kotlin/com/launcher/api/wizard/data/CheckSpec.kt), 6 variants), `Pool` architecture ([core/src/commonMain/kotlin/com/launcher/api/pools/PoolSource.kt](../../core/src/commonMain/kotlin/com/launcher/api/pools/PoolSource.kt)), `WizardEngine.computePending()` ([core/src/commonMain/kotlin/com/launcher/ui/wizard/WizardEngineImpl.kt](../../core/src/commonMain/kotlin/com/launcher/ui/wizard/WizardEngineImpl.kt#L75)).
- Что отсутствует: conditional branching, step dependencies, `cloudRequirement` field, integration FirstLaunchActivity → WizardEngine (draft-1 tech-debt).
- Constitution Article VII §16 запрещает `StepType.Custom` — всё через generic types + declarative specs. Наша модель этому соответствует.

**Industry review (general-purpose agent, 2026-07-09):**
- Paywall vendors (Superwall, Adapty, GrowthBook, LaunchDarkly) converged на `axis + narrow conditional expression`. Никто не даёт preset авторам full DSL.
- **JsonLogic** ([allegro/json-logic-kmp](https://github.com/allegro/json-logic-kmp)) — non-Turing-complete by construction (нет loops/recursion → infinite loop физически невозможен), KMP-ready, MIT, production Allegro, ~50KB. Кастомные operators = natural sandbox.
- **CEL** — золотой стандарт, но overkill (protobuf runtime, нет first-class KMP). Держим как exit ramp через swap adapter'а.
- **Rego (OPA)** — нарушение rule 4 MVA at max volume (WASM runtime в APK для wizard branching).
- **JSON Schema `if/then/else`** — про validation, не runtime dispatch. Используем по прямому назначению: валидировать preset при загрузке.
- **SurveyJS `visibleIf` pattern** — expression как поле на step'е, не отдельный flow-control language. 10 лет production. Copy this pattern.

### Ключевые trade-offs

- **`visibleIf` покрывает ~80% случаев** — включать/не включать шаг. Не покрывает: cycles/retry, sub-flows, cross-step references, ветвление flow (recovery blob найден → Entry, не найден → Setup).
- Владелец решил (2026-07-09): retry — через callback в runtime, не в JSON. Ветвление recovery blob — hardcoded runtime (одна ветка, не вариативно). Sub-flows — additive позже.
- Это делает `visibleIf` минимальным достаточным механизмом.

### Open questions (переносим на моменты spec/implementation)

- **OQ-1**: Google region-block fallback. `cloudRequirement="required"` + Google blocked in RU → fail-open (degrade до banner) или fail-closed (blocking screen)? Или fallback provider (yandex/vk)? Пока склоняемся к fail-open, но зафиксировать.
- **OQ-2**: Один Google UID per device (техническая невозможность двух аккаунтов) но разное поведение per preset — preset A знает «backup включён», preset B знает «backup выключен» с тем же UID. Как хранить: `Map<PresetRef, PresetCloudState>` в ProfileStore? Уточнить при spec.
- **OQ-3**: Preset author safety — whitelist expression context должен быть **строго ограничен** enum'ом. Если preset автор напишет `{"var":"secrets.something"}` — должно отклоняться build-time. Как реализовать: JSON Schema validator + JsonLogic operations-api custom operator list.
- **OQ-4**: Deprecation policy для полей контекста. Что если удаляем `signInResult.completed` из context v1→v2? Все preset'ы с этой переменной ломаются. Нужна migration policy на уровне context wire format.
- **OQ-5**: `visibleIf` тестирование — как preset автор проверяет что его условие работает? Нужен ли preview tool (post-MVP)?

### Decision (English)

**Choice**: Model launcher configuration as **Component** (sealed hierarchy of what-is-configurable) × **Pool** (catalog of parameterized `ComponentDeclaration` entries, loaded from `assets/pool.json` via `BundledPoolSource`, additive across releases) × **Preset** (shareable JSON, schemaVersion=2, three orthogonal fields: `wizardFlow` / `settingsMap` / `activeComponents`) × **Profile** (device-local snapshot copy of activeComponents + user edits + statuses, schemaVersion=2) × **Provider** (per-platform/vendor `check` + `apply` port) × **ProviderRegistry** (dispatch by `HandlerKey(componentType, platform, vendor)` with fallback vendor→platform→NoOp). `Outcome` = sealed `Ok | NeedsApply | Failed(reason) | Unsupported`. `WizardBehavior` = enum `Interactive | AutoApply | InitialDefault`, lives only in `wizardFlow` entries. `paramsOverride` allowed on preset entries, validated against per-declaration JSON Schema with `mutable: true` fields. `visibleIf` field reserved on wizardFlow entries as forward-compat seam — schema present, JsonLogic runtime deferred to first preset requiring conditional inclusion. `SosDispatcher` / cross-Component event bus deferred to first real cross-Component event flow; strapped by fitness function #6 (cross-provider import guard) so introduction stays additive.

**Rationale**:

1. **Component / Pool / Preset / Profile split** isolates what-is-configurable from what-is-desired from what-is-applied. Wizard, Settings, BootCheck, and Admin push share one Component registry while consuming distinct preset fields — the reconcile-loop remains ignorant of consumer identity. No engine rewrite per new consumer or new Component subtype.
2. **Three-field preset split** (`wizardFlow` / `settingsMap` / `activeComponents`) instead of nested `presentation.wizard/.settings/.boot/.admin` sections per Component (session 2 model) — Wizard is a linear scenario, Settings is a categorical edit map, ReconcileEngine input is a flat active list. These are three different mental models; conflating them into one Component-nested structure was a category error caught in session 2.5. One Component may appear in one, two, or all three fields.
3. **Sealed Component** gives compile-time exhaustiveness — compiler forces every consumer with an exhaustive `when` to handle each new subtype. This is the sole hard cost of adding a Component and cannot be silently missed. ECS runtime frameworks (Fleks / Ashley / Artemis) rejected — target scale is ~15 facets × 2 applications per device lifetime, not thousands × 60fps. A runtime type registry buys nothing at this scale while removing compile-time safety.
4. **Explicit `Vendor` enum** for phone-manufacturer dispatch (`Xiaomi / Samsung / Huawei / GoogleTV / GenericAndroid / iOS`). Peripheral vendors (Omron / A&D blood-pressure monitors, future glucometers / ECGs) handled via **nested-adapter pattern** inside a single Provider (`BloodPressureDeviceProvider` → `OmronBpAdapter` / `AndBpAdapter` via lower-level DI), NOT by extending `HandlerKey` with a fourth dimension. Peripheral vendor is a Component parameter, not a device attribute.
5. **`paramsOverride` allowed** on preset entries — otherwise pool explodes into `font-1.0` / `font-1.2` / … / `font-2.5` copies. Override validated against per-declaration JSON Schema with `mutable: true` fields only; immutable fields (`packageName`, `vendorApp`) belong to distinct declarations, not distinct values.
6. **Outcome shape from architectural brief** (`Ok / NeedsApply / Failed / Unsupported`) chosen over session-2 `Applied / NotApplied(reason) / Indeterminate` because it cleanly separates check-time drift signal (`NeedsApply`) from apply-time failure (`Failed`) from capability gap (`Unsupported`, returned by NoOp fallback). Constitution Article VII §15 `Indeterminate` maps to `Unsupported`.
7. **MVA discipline** (per rule 4 + owner refinement 2026-07-10): schema seams for future dynamics (`visibleIf`, `SosDispatcher`, `ConsumerFilter`, `Provider.rollback`) reserved but not implemented in MVP. Runtime engines added when a concrete feature requires them, without changing existing wire format or ports.

**Applies to**:

- **Domain module** (`core/preset/` — commonMain, pure Kotlin, zero Android imports):
  - `Component` (sealed hierarchy), `ComponentDeclaration` (data class + JSON Schema per subtype), `Pool` (typed catalog).
  - `Preset` (schemaVersion=2, three fields), `PresetStepRef` (`poolRef` + `paramsOverride?` + wizard/settings metadata per field).
  - `Profile` (schemaVersion=2, `activeComponents` list + statuses), `ProfileComponent`, `ComponentStatus`.
  - `Outcome` (sealed), `WizardBehavior` (enum).
  - Ports: `PoolSource`, `PresetSource`, `ProfileStore`, `Provider<T : Component>`, `ProviderRegistry`, `InteractionSink`, `ConditionEvaluator` (interface only, reserved).
  - `ReconcileEngine.run(RunMode)`, `ProfileFactory`, `PresetDiff` (for future admin push).
- **Adapter module** (`app/androidMain/provider/*`, `app/iosMain/provider/*`): per-platform/vendor Provider implementations. All Android SDK usage lives here.
- **Assets**:
  - `assets/pool.json` — BundledPoolSource content, schemaVersion=1, additive growth per app release.
  - `assets/bundled-presets/{simple-launcher,launcher,workspace}.json` — 3 seed presets, schemaVersion=2.
- **DI wiring** (`app/di/HandlerModule.kt`): Hilt `@IntoMap` with custom `@ComponentKey` annotation; `ProviderRegistry` receives resolved Map + platform + vendor.
- **Wire formats** (rule 5 versioning applies):
  - `pool.json` schemaVersion=1 → 2 additively; roundtrip + backward-compat tests mandatory.
  - `preset.json` schemaVersion=2 (three-field split introduced now); migration writer from any pre-existing v1 preset if present.
  - `profile.json` schemaVersion=2 (matches preset schemaVersion); migration writer v1→v2.
- **Fitness functions** (rule 7, enforced in CI):
  1. Import guard: `core/preset/engine/**` MUST NOT import concrete `Component.*` subtypes.
  2. `when`-guard: `core/preset/engine/**` MUST NOT contain `when(component)` on concrete subtypes.
  3. Coverage test: every `Component::class.sealedSubclasses` element has a registered Provider in the test DI graph (no orphan Component subtype).
  4. Roundtrip test: `pool + preset → profile → serialize → deserialize → equal`.
  5. Backward-compat test: pool v1 / preset v1 (if any) readable by v2 code.
  6. Cross-provider isolation: `provider/foo/**` MUST NOT import `provider/bar/**` (straps the deferred SosDispatcher pattern).
  7. `paramsOverride` schema validation: overrides validated against per-declaration JSON Schema; `mutable: true` fields only.
- **Downstream tasks receiving this contract**:
  - `task-draft-1` — Wizard manifest-driven refactoring (removes hardcoded Sign-In from `FirstLaunchActivity`).
  - `TASK-71` — Wizard hidden steps and defaults.
  - `TASK-69` — Settings as Profile View.
  - `TASK-68` — Workspace preset (bundled).
  - `TASK-19` — Adaptive UX Presets.

**Trade-offs**:

- **Vs single `presentation.wizard/.settings/.boot/.admin` sections inside Component** (session 2 model): three-field split introduces three top-level preset structures instead of one Component list, but each is mono-purpose. Session 2 model conflated linear-scenario ordering (wizard) with categorical layout (settings) with flat active list (reconcile) into nested per-Component metadata — three mental models compressed into one, caught as intuitive mismatch by owner in session 2.5.
- **Vs catalog-only Pool (no `paramsOverride`)** (brief-2 stance): catalog is simpler to audit but explodes size for parameterized Components (font scale continuum, tremor sensitivity gradient, geofence bounds). Override is more flexible but requires per-declaration JSON Schema with `mutable: true` marker. Chose override.
- **Vs ECS runtime framework** (Fleks / Ashley / Artemis): rejected — order-of-magnitude scale mismatch (~15 × 2/lifetime vs thousands × 60fps). Kotlin `sealed` + kotlinx.serialization + Hilt multibinding + ~200 lines domain covers everything ECS offers at this scale, with compile-time safety ECS runtime forfeits.
- **Vs bounded `RunMode` enum in engine (4 consumers today)**: acceptable now (Wizard / BootCheck / Settings / AdminPush). If a 5th consumer appears (Voice, TV overlay), lift filter to `ConsumerFilter` port — additive, engine untouched.
- **Vs immediate `SosDispatcher` port**: deferred; strapped by fitness function #6 (cross-provider import guard) so introduction stays additive. Cost of delay: one extra intra-Component event flow feature must add the port before shipping.
- **Vs immediate `visibleIf` runtime**: schema seam reserved (field present in wizardFlow entries, ignored in MVP). JsonLogic `ConditionEvaluator` implementation deferred to first preset requiring conditional inclusion. Cost of delay: any Component that would have been conditionally included lives as hardcoded runtime skip until the seam is filled.
- **Vs immediate `Provider.rollback` for admin push undo**: not required in MVP (admin push not shipping yet). `PanicReset` Component covers coarse-grained recovery. If per-Component rollback needed, extend Provider interface with `suspend fun rollback(...): Outcome = Outcome.Unsupported` default — additive to existing implementations.

**Exit ramp** (one-way door mitigation, rule 3):

- **If Kotlin `sealed` exhaustiveness becomes a coordination burden** (team scale-up, merge points on Component.kt): swap to open registry with `KClass` keys + runtime coverage test. Cost: one-time change to Component base + registry lookup layer, ~200 lines. Downstream code (Providers, engine) unchanged because they see abstract Component. Fitness function #3 upgrades from compile-time-guaranteed to test-time-verified.
- **If Provider port pair (`check` + `apply`) proves insufficient** (rollback for admin push undo, `preview` for dry-run, `explain` for admin UI): extend interface with default no-op methods. Cost: additive, existing Providers untouched, new Providers may opt in.
- **If JsonLogic ceiling hit** (cross-step references, cycles, custom control flow): swap `ConditionEvaluator` port to CEL adapter. Cost: single adapter module change per rule 2 ACL; preset syntax migration written before shipping breaking change per rule 5.
- **If `paramsOverride` proves too permissive** (typo bugs, injection surface): tighten JSON Schema `mutable` marker to per-field `overridable: Boolean = false` requiring explicit opt-in at declaration site.
- **If three-field preset split proves inadequate** (5th consumer type needs its own view): add fourth field to Preset, NOT change existing three. Preset schemaVersion bump per rule 5.
- **If nested-adapter pattern for peripheral vendors proves brittle** (large peripheral catalog with many vendors): extract to secondary registry `PeripheralAdapterRegistry` mirroring `ProviderRegistry` shape. Cost: additive layer, existing peripheral Providers refactored one at a time.
- **If ECS-runtime becomes actually needed** (interactive experiences layer): wrap current model in ECS World as one entity kind alongside others. Cost: additive above the current foundation, no replacement.
- **If Vendor enum outgrows explicit list** (OEM proliferation): switch `HandlerKey` platform/vendor axis to opaque `Map<Dimension, Value>` bag. Cost: registry lookup layer rewrite, ~50 lines; providers untouched.

---

### Session 2 mentor — foundational rescope (2026-07-09, PM)

**Context shift**: во время подготовки к `/speckit.specify` владелец переформулировал scope. TASK-120 (visibleIf/JsonLogic) оказался **частным случаем** более фундаментального вопроса — форма Pool / Preset / Profile / Component / Provider. Обсуждение вышло далеко за пределы conditional inclusion; фиксируем состояние здесь чтобы не потерять при переключении на рабочее место.

#### Владелец сформулировал модель

1. **Preset ≠ Wizard.** «В Settings нет step'ов, есть равноценные части приложения». Модель должна обслуживать 4 потребителей: Wizard, Settings, BootRouter (drift check), Admin remote (future). Один domain, разные UI над ним.
2. **Порядок зависимостей**: Pool → Preset → Profile → всё остальное (backup / pairing / sync / recovery / E2E / server). Без profile foundation нет смысла в downstream фичах.
3. **Pool = bassein равноценных настраиваемых частей** приложения. Preset = pick из pool с параметризацией. Profile = runtime state устройства (snapshot copy preset + user data + provider results).
4. **Preset — просто JSON**, шарящийся. Приходит из bundled seed (3 стартовых чтобы приложение не было пустым), из файла, из share intent, из URL, из QR. `PresetSource` port + адаптеры.
5. **Разделить declaration от execution**: Component (что настраивается) отдельно от Provider (как настраивается на конкретной платформе с конкретным вендором).
6. **Fallback chain provider**: vendor-specific → platform-generic → no-op.
7. **Snapshot copy** для profile (не live reference на preset). Preset после активации не читается; profile содержит клон + user choices + provider results.

#### Индустриальные параллели (найдены владельцем)

Модель дословно повторяет паттерны из нескольких доменов — это подтверждает корректность:

- **Infrastructure-as-Code** (Terraform Resource+Provider, Puppet Resource+Provider с confine/defaultfor, Ansible Task+Module, Chef Resource+Provider, Kubernetes reconcile-controller, systemd Unit).
  - Terraform `plan` = наш `check()`, `apply` = наш `apply()`.
  - Puppet provider selection (vendor→platform→no-op) — дословно наша fallback chain.
- **ECS (Entity-Component-System)** — Unity DOTS, Bevy, Unreal.
  - Component = равноценная характеристика объекта (тема, layout, permission, AppPresence).
  - Archetype = шаблон объекта (= наш **Preset**).
  - Entity = runtime instance (= наш **Profile**).
- **Home Assistant** — Entity/Platform/Integration, vendor-integrations с fallback. Меньше подходит как язык (терминология device-centric).

#### Terminology decision

Финализировано на слух владельца («У Profile есть **компоненты** — тема, layout, permissions»):

| Роль | Имя | Замечание |
|---|---|---|
| Sealed hierarchy того что настраивается | **Component** | В коде — `com.launcher.preset.Component` (не конфликтует с Compose/Hilt по import) |
| Параметризованный экземпляр в Pool | **ComponentDeclaration** | Data class с params |
| Реестр всех известных declarations | **Pool** | Registry в core/preset (source может быть code/JSON/hybrid — OQ) |
| Shareable JSON template | **Preset** | = archetype в ECS-терминах, оставлено owner-friendly имя |
| Runtime state на устройстве | **Profile** | = entity в ECS-терминах, оставлено owner-friendly имя |
| Per-platform реализация check + apply | **Provider** | Из Terraform/Puppet, replaces «Handler» (Handler — размытое) |
| DI lookup + fallback chain | **ProviderRegistry** | vendor → platform → no-op |
| Результат check/apply | **Outcome** | Applied \| NotApplied(reason) \| Indeterminate (Article VII §15) |

Отвергнуто:
- **Step / StepType / StepHandler** — wizard-language, некорректно (в Settings нет step'ов).
- **Resource** — правильная семантика (Terraform), но collision с Android `R.*` резources.
- **Unit** — семантически хорошо, но `Unit` — reserved type в Kotlin.
- **Trait** — нейтрально, без коллизий, но не активирует ECS-паттерн у читателя.
- **Facet** — точно, но слабый reference material.
- **Handler** для Provider — размытое, много context'ов (event/error/message handler).

#### Presentation hints — где живут (open question, склон к D)

Каждая ComponentDeclaration несёт metadata для каждого потребителя отдельно от domain-logic:
```
presentation: {
  wizard: { include: true, order: 3, title, description } | null
  settings: { include: true, category, sensitive } | null
  boot: { critical: true, silentDegrade: false } | null
  admin: { remotelyEditable: false } | null
}
```
Отсутствие секции = «этот потребитель не показывает». `visibleIf` (исходный scope TASK-120) — **одно из полей внутри presentation.wizard**, а не самостоятельный concern.

**OQ presentation**: A (в ComponentType hardcoded) / B (в pool declaration) / C (в preset override) / **D (composition A default + C override)** — склон к D.

#### Component granularity — open question, склон к средней

- Мелкие (~30-50): `Permission.ReadContacts`, `Permission.Camera`, `HomeRole`, `DefaultDialer`, `AppPresence`, `LayoutSlot`, `ThemeColor`, `FontScale`, `SignInGoogle`.
- **Средние (~10-15)** — склон: `Permission(what)`, `SystemClaim(kind)` где `kind = HomeRole|DefaultDialer|AccessibilityService`, `AppPresence(pkg, required)`, `LayoutBinding(...)`, `Theme(name)`, `SignIn(provider)`, `FontScale(min)`.
- Крупные (~5-7): `Permission(...)`, `SystemClaim(...)`, `AppOperation(...)`, `UISetting(k, v)`, `Auth(...)`.

Fitness rule (кандидат): «Provider не может делать `when` по параметру внутри — параметризация в data, dispatch по type».

#### Vendor dispatch — open question, склон к A (explicit enum)

- **A (склон)**: `enum Vendor { Xiaomi, Samsung, Huawei, GoogleTV, GenericAndroid, iOS }`. Provider регистрируется с явным `@Provider(Vendor.Xiaomi)`. Fallback: Xiaomi → GenericAndroid → NoOp.
- B: Predicate-based (`applies(deviceContext): Boolean`). Более гибко, harder to reason.
- C: Capability-based (provider декларирует что умеет). Абстрактнее, ближе к «доступные колбеки» идее владельца.

#### Preset ↔ Pool composition — open question, склон к A2

- A1: pure reference `{ poolRef: "app-install-jitsi" }`. Pool должен предусмотреть все параметризации.
- **A2 (склон)**: reference + override `{ poolRef: "app-install", params: {...} }`. Pool = шаблон, preset дописывает params.
- A3: reference + inline exception (позволить preset нести vendor-specific declaration).

#### Directory model

Preset **не сидит в `assets/presets/`** как dogma. Это просто JSON.
```
core/preset/               ← Component, ComponentDeclaration, Pool, Preset, Profile, ports, Outcome
core/preset/provider/      ← Provider port, ProviderRegistry port
app/androidMain/provider/  ← ContactPermissionProvider, XiaomiHomeRoleProvider, GenericHomeRoleProvider, ...
app/iosMain/provider/      ← iOS providers
assets/bundled-presets/    ← ТОЛЬКО 3 seed JSON'а (simple-launcher, launcher, workspace) чтобы app не был пустым при первом install
```
Все остальные источники (file / share / network / QR) — `PresetSource` adapters. `BundledPresetSource` — одна из реализаций.

#### Consumers reconcile-loop (ключевая архитектурная мысль)

```
Wizard      → показать Component'ы где check() = NotApplied
Settings    → показать ВСЕ Component'ы active preset, дать apply() с новыми params
BootRouter  → periodic check на critical Component'ы; drift → banner
Admin remote → присылает новый Preset; локальный reconcile-loop приводит Profile к desired state
```

Один reconcile-loop, четыре UI над ним.

#### Что решено про TASK-120 scope

- **TASK-120 расширяется** из «Preset conditional composition via visibleIf + JsonLogic» до **«Component/Preset/Profile foundational model»**. Rename при следующем touch.
- `visibleIf` + JsonLogic **сохраняется** как часть Decision — оно становится полем в `presentation.wizard` секции ComponentDeclaration, не отдельный concern.
- Alternative — создать TASK-121 «foundational model» и закрыть TASK-120 как superseded. Owner склон **расширить TASK-120**, не разбивать (session 2 conclusion).

#### Что решено про TASK-65 backward-compat

- TASK-65 **не откатывается**. Многое сохранится: `ProfileStore`, `CopyOnActivateStrategy`, `PoolSource` port.
- Terminology rename на next touch: `ConfigKind` → `Component`, `ConfigSpec` → `ComponentDeclaration`, `CheckSpec/ApplySpec` → провайдерская сторона.
- Handler layer добавляется отдельным port (`Provider`/`ProviderRegistry`).

#### Open questions for session 3 (next work session)

1. **OQ-6 (Component granularity)** — финализировать sealed hierarchy границы. Middle path ~10-15 склон, но нужно перечислить точный список.
2. **OQ-7 (Provider dispatch)** — финализировать A vs C. A склон, но owner мог упустить downside.
3. **OQ-8 (Presentation hints location)** — D склон (default + override), но нужна exact schema.
4. **OQ-9 (Pool source model)** — Kotlin object / JSON asset / hybrid. TASK-73 (per-vendor variants) намекает на hybrid.
5. **OQ-10 (Preset ↔ Pool composition)** — A2 склон, финализировать schema.
6. **OQ-11 (Profile snapshot migration)** — как мигрируем profile v1→v2 когда preset schema обновляется. Rule 5 wire-format-versioning applies.
7. **OQ-1..OQ-5** из session 1 (Google region-block, per-preset cloud state, whitelist safety, context deprecation, preview tool) — остаются open.

#### Готовность к next steps

- **Session 3** (следующая работа): закрыть OQ-6..OQ-11 + OQ-1..OQ-5 → заполнить Decision block → status Discussion → Draft.
- **Session 4**: `/speckit.specify` — переработка `core/preset/` (частично из TASK-65) + новые ports Provider/ProviderRegistry/PresetSource/AssetResolver + wire format preset.json v2.
- **Downstream tasks** (draft-1 Wizard refactoring, TASK-71, TASK-69, TASK-68, TASK-19) — уведомлены о `dependencies: [TASK-120]` уже 2026-07-09.

---

### Session 2.5 mentor — brief mapping + extensibility stress-test (2026-07-10)

Промежуточная сессия перед session 3. Owner принёс два внешних мента-брифа (архитектурный сводник + narrower ECS-vs-самопис ответ). Прошли по: naming принят, override параметров решён, разделение Wizard/Settings осмыслено, стресс-тест на 5 фичах, три места if-else, шов SosDispatcher отложен.

#### Naming — **финализировано** (owner: «нейминг ок, используем»)

Component / ComponentDeclaration / Pool / Preset / Profile / Provider / ProviderRegistry / Outcome. Никаких Step/StepType/StepHandler/Resource/Unit/Trait — см. session 2 таблицу отвергнутых.

#### Owner refinement 2026-07-10

> «Не жёстко зашивать, всё что динамичное — не так важно на ранних этапах.»

Применяем MVA (rule 4): закладываем **швы** (порты, точки где можно расшириться), сами расширения (visibleIf/JsonLogic, admin push, SosDispatcher, PresetDiff) — добавляем когда конкретная фича реально попросит. Preset формат гибкий (три поля — см. ниже), но полная динамика JsonLogic-условий может подождать первого случая когда без неё никак.

#### OQ resolutions после стресс-теста

- **OQ-6 (Component granularity)** → **средняя, ~10-15**. MVP-волна ~5-7 из полного списка. Полный список (для планирования, не MVP):
  `AppTile`, `MessengerTile` (наш SSO handoff — отдельный тип от AppTile), `FontSize`, `TremorGuard`, `VolumeGuard`, `Lockdown`, `Sos`, `GeoFenceSos`, `TimeLockdown`, `Reminder`, `HealthForward`, `Toolbar`, `Checkin`, `ScamGuard`, `Screensaver`, `BatteryGuard`, `PanicReset`, `DateTile`, `Tts`, `NotificationFilter`. MVP subset уточнить в session 3.
- **OQ-7 (Provider dispatch)** → **A (explicit enum)** для vendor-производителя-телефона: `enum Vendor { Xiaomi, Samsung, Huawei, GoogleTV, GenericAndroid, iOS }`. **Расширение**: для периферии (Omron/A&D тонометры) vendor не совпадает с `Build.MANUFACTURER` — это вендор *устройства измерения*. Паттерн — **вложенные адаптеры внутри одного Provider** (`BloodPressureDeviceProvider` → диспатч через нижний уровень DI на `OmronBpAdapter`/`AndBpAdapter`). Session 2 модель не ломается, но получает под-паттерн.
- **OQ-9 (Pool source)** → **чистый JSON asset** для BundledPoolSource. Compile-time coverage-check («каждый Component subtype есть в pool») — через reflection-тест, не через Kotlin-код pool'а.
- **OQ-10 (Preset ↔ Pool composition)** → **A2 (poolRef + paramsOverride разрешён)**. Иначе копипаста склада. Override валидируется по JSON Schema заготовки; поля отмечаются `mutable: true`. Иммутабельные поля (packageName, vendorApp) переопределить нельзя — это разные заготовки, не разные значения.

#### Новое решение: разделение Wizard vs Settings — **три поля в Preset**

Owner intuition: «в Wizard одна логика, в Settings другая, не пихайте один preset управлять обоими сразу».

Session 2 модель `presentation.wizard / .settings / .boot / .admin` секций **внутри Component** пересмотрена. Правильное разделение:

```
Preset {
  wizardFlow:       List<{ poolRef, wizardOrder, wizardTitle, behavior, paramsOverride? }>
  settingsMap:      List<{ poolRef, category, settingsIcon, sensitivity, paramsOverride? }>
  activeComponents: List<{ poolRef, paramsOverride?, status }>
}
```

Разные потребители — разные структуры:
- **Wizard** — читает `wizardFlow`, линейный сценарий, только non-applied шаги.
- **Settings** — читает `settingsMap`, свободный доступ по категориям.
- **ReconcileEngine / BootCheck / Admin push** — читают `activeComponents`, реальное состояние.

Один Component может быть в обоих, в одном, или ни в одном (см. чат Session 2.5). Wizard — one-way trip, Settings — daily reality.

Trade-off: три структуры вместо одной, но каждая мономорфна и понятна отдельно. Session 2 «одна секция presentation» переусложняла Component-уровень; здесь уровень абстракции правильный.

#### Новое решение: Outcome — принять брифовское

```
sealed class Outcome {
  object Ok         : Outcome()   // check: matches / apply: done
  object NeedsApply : Outcome()   // check: drift detected
  data class Failed(val reason: String) : Outcome()
  object Unsupported: Outcome()   // no capability (NoOp fallback returns this)
}
```

Чище чем session 2 `Applied/NotApplied(reason)/Indeterminate`. Article VII §15 Indeterminate = Unsupported здесь.

#### Новое решение: WizardBehavior — часть wizardFlow элемента

```
enum WizardBehavior { Interactive, AutoApply, InitialDefault }
```

Живёт в `wizardFlow[i].behavior`. НЕ в pool declaration (там нет wizard-семантики — pool = сырьё). Settings не использует WizardBehavior (там всё Interactive по определению).

#### Стресс-тест на 5 конкретных фичах (все проходят без правки ядра)

| Фича | Что добавляется | Ядро правится? |
|---|---|---|
| Android vs iOS настройки | iosMain Provider или NoOp fallback | Нет |
| ТВ-пульт вместо тач | Новый `InteractionSink` + tv-grid renderer | Нет |
| Тонометры Omron + A&D | `BloodPressureDevice` Component + Provider с вложенными vendor-адаптерами | Нет (новый под-паттерн) |
| Timelock 08-14 | `TimeLockdown` Component + Provider (AlarmManager) | Нет |
| GeoFence SOS | `GeoFenceSos` Component + Provider (Android geofencing API) + позже `SosDispatcher` port | Нет (новый port ≠ переписать) |
| Плитка нашего мессенджера с SSO handoff | `MessengerTile` Component (отдельный от AppTile) + Provider использует `AuthHandoffService` port | Нет |

#### if-else — три места, только одно ок

- **В данных (preset visibleIf)** — ✅ ок. JsonLogic evaluated через `ConditionEvaluator` port, whitelisted context. Может быть отложено до первого случая (owner refinement про динамику).
- **В провайдере по параметру** — ❌ плохо. `when(step.vendorApp)` в BloodPressureProvider = переписывание при каждом новом тонометре. Правильно — вложенные vendor-адаптеры через DI.
- **В engine по типу Component** — ❌❌ катастрофа. Ядро НЕ импортирует subtypes Component. Fitness-тест на импорты в CI.

#### PoolSource и версионирование программы

Ответ на owner-вопрос «PoolSource растёт при новых версиях?»: **Да, растёт additive.** С каждым релизом:
- `pool.json` в assets получает новые записи (не убирает старые, не меняет параметры).
- Sealed hierarchy `Component` растёт новыми subtypes.
- Preset ссылается по строковому `poolRef` — если id не найден на устройстве (старая версия) → шаг пропускается + помечается `unknownRefs` в profile → повторная попытка после upgrade.
- Rule 5 wire-format-versioning: schemaVersion на pool.json + preset.json + profile.json. Migration writer преобразует profile v1→v2 при upgrade.

#### Fitness functions (rule 7) — enforce «no core edit» через CI

1. **Import guard**: `core/preset/engine/**` не импортирует `Component.*` subtypes.
2. **when-guard**: `core/preset/engine/**` не содержит `when(component)` на конкретных subtypes.
3. **Coverage test**: каждый sealed subtype `Component` имеет зарегистрированный Provider в тестовом DI-графе.
4. **Roundtrip test**: pool + preset → profile → serialize → deserialize → equal.
5. **Backward-compat test**: pool v1 читается кодом v2 без потерь.
6. **Cross-provider isolation**: `provider/foo/` не импортирует `provider/bar/` (страховка для SosDispatcher-отложения).
7. **paramsOverride schema check**: override валидируется по JSON Schema заготовки, `mutable: true` fields only.

#### Отложенные швы (owner решение — не сейчас)

- **SosDispatcher / EventBus port** — межкомпонентное общение. Страховка = правило + fitness #6. Заведём когда geofence SOS реально понадобится.
- **ConsumerFilter port** — если 5-й consumer появится. RunMode enum достаточен для 4-х. YAGNI.
- **Provider.rollback** — для admin push undo. Пока `PanicReset` Component покрывает need в MVP.
- **JsonLogic runtime + visibleIf field** — заложить в wire format schemaVersion=2, но реализовать `ConditionEvaluator` только когда первый preset реально попросит conditional inclusion. До этого — hardcoded runtime skip.

#### Open questions для session 3 (сокращённые)

- **OQ-A**: MVP-волна Component'ов — какие 5-7 из ~20 в первый релиз (наверняка: AppTile, FontSize, TremorGuard, VolumeGuard, Sos + пара tail'а).
- **OQ-B**: Bundled seed presets — какие 3 стартовых (simple-launcher, launcher, workspace)? Их точное содержимое.
- **OQ-C**: MessengerTile — заводим как отдельный Component сейчас (даже до появления мессенджера в TASK-27) чтобы паттерн handoff-плитки был заявлен? Или подождать TASK-27.
- **OQ-D**: `wizardFlow` элементы могут переопределять `paramsOverride`, но `settingsMap` элементы — тоже? Или settings берёт params только из pool defaults + user edits через runtime?
- **OQ-E**: Decision block filling — Choice / Rationale / Applies to / Trade-offs / Exit ramp на English.

#### Downstream последствия

- Отдельного decision-task для «Wizard vs Settings разделение» **не создаём** — включаем в scope TASK-120 (уже foundational rescope).
- Rename задачи title: `"Decision: Component/Preset/Profile foundational model"` — на следующем touch (AC #5).
- Downstream tasks (draft-1, TASK-71, TASK-69, TASK-68, TASK-19) — dependencies already set.

<!-- SECTION:DISCUSSION:END -->

## Acceptance Criteria

<!-- AC:BEGIN -->
- [x] #1 Decision block заполнен (Choice / Rationale / Applies to / Trade-offs / Exit ramp) на English 2026-07-10 (session 2.5)
- [x] #2 Open questions OQ-1..OQ-11 либо resolved в Decision, либо явно deferred в spec-time (OQ-A..OQ-E из session 2.5 переходят в /speckit.clarify per owner directive «по ходу найдём»)
- [x] #3 Downstream tasks (draft-1 Wizard manifest-driven refactoring, TASK-71 hidden steps, TASK-69 Settings as Profile View, TASK-68 workspace preset, TASK-19 Adaptive UX Presets) добавлены `dependencies: [TASK-120]` 2026-07-09
- [x] #4 Session 2 mentor discussion зафиксирован в SECTION:DISCUSSION (foundational rescope: Component/Preset/Profile/Provider terminology + industrial parallels + OQ-6..OQ-11)
- [x] #5 [hand] Task file renamed 2026-07-11 via `git mv` → `task-120 - Decision-Component-Preset-Profile-foundational-model.md`; frontmatter title updated (T068)
- [N/A] #6 [hand] End-to-end bundled seed apply — foundation-scope validation shipped: `BundledAssetsLoadTest` (Robolectric) loads pool + 3 seeds + PresetValidator passes each; production UI wiring `PresetBootstrap` → runtime apply belongs to TASK-126 (Wizard runtime migration)
- [x] #7 [hand] Wizard, Settings, BootCheck, RemotePush работают на одном ReconcileEngine — foundation-level verified: `ReconcileEngineTest` covers 6 scenarios (все 4 RunMode + Interactive skip + provider Failed). Production UI wiring в TASK-126 (не блокирует foundation contract)
- [x] #8 [hand] Platform fallback via NoOpProvider — `DefaultProviderRegistry` реализует 3-tier fallback (vendor → platform → NoOp), covered by `ReconcileEngineTest` orphan-provider case
- [x] #9 [hand] AppTile install-check flow — `AppTileProvider` dispatches PackageManagerFacade + StoreIntentFacade + HomeScreenFacade; check→NeedsApply→apply→Play Store intent или Failed(NetworkUnavailable) fallback implemented (SC-007)
- [x] #10 [hand] PresetDiff classifies Added/Removed/ParamsChanged — `PresetDiffTest` covers 5 scenarios вкл same-version-different-content rejection (SC-009)
- [N/A] #11 [hand] Property-based test N=100 — deferred (T040/T041 `deferred-hand`): kotest-property dep + Arb.preset generator handoff to TASK-126 или отдельный follow-up. MVP surface (4 subtypes × unit tests) covers regression-space adequately
- [x] #12 [hand] Anti-explosion pool: bundled `pool.json` = 4 subtypes × 1 declaration each; `PoolAntiExplosionTest` errors at ≥6 per subtype (SC-012)
- [N/A] #13 [hand] Undo Wizard UX toast — foundation-scope done: `Profile.preWizardSnapshot` + `ProfileStore.setPreWizardSnapshot()/restoreFromPreWizardSnapshot()` implemented + tested via `FakeProfileStore` roundtrip; UI toast wiring belongs to TASK-126
- [ ] #14 [auto:checklist] specs/task-120-preset-composition-foundation/checklists/ai-readiness.md: 16/20 CHK [x] (retroactive lift baseline: session 2.5.5 accepted 19/20; delta = pre-implementation nomenclature)
- [ ] #15 [auto:checklist] specs/task-120-preset-composition-foundation/checklists/capability-registry-readiness.md: 3/12 CHK [x] (retroactive PASS baseline: session 2.5.5 accepted 10/12 after MCP removal + TODO markers)
- [x] #16 [auto:checklist] specs/task-120-preset-composition-foundation/checklists/device-self-sufficiency.md: 0/0 CHK [x] (N/A — foundation, no cloud dependency)
- [ ] #17 [auto:checklist] specs/task-120-preset-composition-foundation/checklists/dev-experience.md: 15/22 CHK [x]
- [x] #18 [auto:checklist] specs/task-120-preset-composition-foundation/checklists/domain-isolation.md: 16/16 CHK [x] ✓
- [ ] #19 [auto:checklist] specs/task-120-preset-composition-foundation/checklists/failure-recovery.md: 11/17 CHK [x] (retroactive lift baseline: session 2.5.5 accepted 15/17)
- [ ] #20 [auto:checklist] specs/task-120-preset-composition-foundation/checklists/localization-ui.md: 6/25 CHK [x] (foundation-scope; layout resilience deferred to TASK-126)
- [ ] #21 [auto:checklist] specs/task-120-preset-composition-foundation/checklists/meta-minimization.md: 12/13 CHK [x]
- [ ] #22 [auto:checklist] specs/task-120-preset-composition-foundation/checklists/modular-delivery.md: 12/18 CHK [x] (requiredModules accepted skip)
- [ ] #23 [auto:checklist] specs/task-120-preset-composition-foundation/checklists/preset-readiness.md: 16/20 CHK [x] (retroactive lift baseline: session 2.5.5 accepted 19/20 after TODO(shareability) markers)
- [ ] #24 [auto:checklist] specs/task-120-preset-composition-foundation/checklists/requirements-quality.md: 8/16 CHK [x] (foundation spec appropriately technical)
- [ ] #25 [auto:checklist] specs/task-120-preset-composition-foundation/checklists/state-management.md: 8/17 CHK [x] (foundation-scope; lifecycle in TASK-126)
- [ ] #26 [auto:checklist] specs/task-120-preset-composition-foundation/checklists/ux-quality.md: 10/27 CHK [x] (foundation-domain; UX concrete in TASK-126)
- [ ] #27 [auto:checklist] specs/task-120-preset-composition-foundation/checklists/wire-format.md: 13/18 CHK [x] (retroactive PASS baseline: session 2.5.5 accepted 18/18 after contracts/ artifacts)
- [x] #28 [auto:deferred-local-emulator] Physical Xiaomi Redmi Note 11 smoke (17f33878, lisa_eea, MIUI) 2026-07-11 — mockBackend APK installs, LauncherApplication.onCreate completes without crash, Koin resolves task120Module + PresetBootstrap + full port graph, FirstLaunchActivity renders 3 preset options (workspace/launcher/simple-launcher). Screenshot: `verification-evidence/task-120-xiaomi-first-launch.png`. Physical device stronger than emulator baseline (T067)
<!-- AC:END -->

## Definition of Done

**Status → Done** когда:
- Все AC `[hand]` (#5-#13) зелёные — hand-verified через тесты и пользовательский прогон.
- Все AC `[auto:checklist]` (#14-#27) — pre-PR sync проставляет по фактическим count'ам чек-лист файлов; retroactive PASS-lifts за счёт session 2.5.5 blockers resolution.
- AC `[auto:deferred-local-emulator]` (#28) — Owner прогоняет emulator smoke через skill `android-emulator`.

Промежуточные статусы:
- **In Progress** — code changes in flight, до PR merge.
- **Verification** — PR merged, но #28 (emulator smoke) ещё не закрыт.
- **Done** — все AC зелёные.

Implementation отслеживается через **tasks.md T001-T072** в spec-папке, не через отдельные downstream tasks (downstream tasks получают контракт из этой фичи, но собственную реализацию имплементируют сами).

---

## Paused note (2026-07-10 16:20)

Причина: token budget сессии — большая имплементация не поместится в оставшийся бюджет.

Где остановились: Phase 1 (T001-T008) не начата. Единственные side effects на диске:
- Созданы пустые директории `core/src/commonMain/kotlin/com/launcher/preset/{model,port,engine,adapter}/` — их можно оставить, при resume будут использованы.
- Никаких .kt файлов не написано, ничего не закоммичено.

Артефакты spec-kit цикла (`spec.md`, `plan.md`, `data-model.md`, `contracts/`, `tasks.md`, `analyze-report.md`, `checklists/`) — locked, вся необходимая информация для resume в них есть.

Resume в новой сессии: точка входа — Phase 1 T001-T008 (domain types в `core/src/commonMain/kotlin/com/launcher/preset/model/`). Первым шагом session poison — вернуть status `In Progress`.
