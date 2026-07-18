# Implementation Plan: Settings as Profile View (TASK-69)

## 1. Overview

Turn the Settings screen into a **second projection of the ECS `Profile`** (Wizard is the first): render one data-driven list from `Profile.entities` + the active `Preset.settingsMap`, let the user change in-app-applied settings through the reconcile engine, and absorb the legacy `FlowPreset` settings screen into one screen. See [spec.md](spec.md). Model is defined once in [`docs/architecture/ecs.md`](../../docs/architecture/ecs.md) — this plan cites it, it does not restate it.

## 2. Technical Context

- **Language/stack**: Kotlin, `core/preset/` (commonMain, pure domain) + `app/` (Android, Compose, Hilt).
- **Model authority**: `ecs.md` invariants **I1–I3** (Profile self-contained for behaviour+home; presentation `settingsMap` lives on the Preset and is read at runtime), **§10** (ports-only UI, gateway seam).
- **Scope decided** (spec Clarifications + mentor 2026-07-18): purpose-shaped `SettingsGateway` port with `ReconcileEngine` behind it; `SettingsPresentationBuilder` produces a `SettingsView`; editability **derived** (no wire field); legacy screen **absorbed** (FR-017/020/021); JSON-driven render **deferred to TASK-133**.

## 3. Architecture

Layers (arrows down only — rule 1 fitness):

```
SettingsScreen (Compose)                     app/  — dumb render of SettingsView, no when(component), no Android calls
   ↓
SettingsViewModel                            app/  — depends ONLY on SettingsGateway
   ↓ port
SettingsGateway  (observe(): Flow<SettingsView>, apply(poolRef, params): ApplyResult)   core/preset/port/
   ↑ adapter
EngineSettingsGateway                        app/ (or core adapter) — wires the pieces below
   ├─ SettingsPresentationBuilder            core/preset/settings/ — Profile + Preset.settingsMap (+ i18n) → SettingsView
   ├─ ReconcileEngine (RunMode.Single)       core/preset/engine/ (reused) — apply one component
   ├─ ProfileStore (reused port)             observe/persist Profile
   └─ PresetSource (reused port)             load active Preset for settingsMap (I2)
```

- **`SettingsPresentationBuilder`** is written in the shape of a future **shared Home+Settings presentation layer** (same builder pattern the Home projection will adopt), but **Home is NOT touched** here — unification is a later additive step (rule 4).
- **App-operations** (FR-020): the non-profile legacy items (preset switch, pairing-QR, admin-devices, data-reset) become `AppOperation` entries in `SettingsView.actions`, rendered generically and wired to existing navigation/actions — their internals are re-hosted, not rewritten.
- **Legacy removal**: `core/…/ui/screens/SettingsScreen.kt` (FlowPreset) + its `SettingsComponent` entry deleted after a dangling-ref audit (CHK012); the ECS-era `app/…/settings/SettingsActivity.kt` becomes the single host.

## 4. Data Model

New **runtime** (non-persisted) domain types — see [data-model.md](data-model.md): `SettingsView`, `SettingsSection`, `SettingRow` (with derived `editable` + `RowKind` = InApp / SystemDialog / ReadOnly + projected `LifecycleState`), `AppOperation` (sealed). `SettingsGateway` port, `SettingsPresentationBuilder` service, `ApplyResult`. `SettingsView` is designed serializable (for TASK-133) but is **not** a persisted/wire type in this task.

## 5. Wire Formats

**None new, none changed.** `Preset.settingsMap` (`SettingsMapEntry`) is unchanged — editability is derived, not a field (rule 4). `Preset`/`Profile` `schemaVersion=2` unchanged. No migrator, no roundtrip delta. (SC-010: builder is deterministic on fixed input; existing wire roundtrips stay green.) No `contracts/` file needed.

## 6. Dependency Impact

**No new gradle dependencies.** Reuses existing `core/preset` domain, Compose, Hilt, DataStore, kotlinx.serialization. (Article XIII: nothing to justify.)

## 7. Test Strategy

- **Fake-adapter (rule 6)**: `FakeSettingsGateway` drives `SettingsViewModel` + `SettingsScreen` tests (no engine). Existing `FakeProfileStore`/`FakeProvider`/`FakePresetSource`/`FakeLocalizedResources` reused.
- **Builder unit tests** (`core:test`, JVM): `SettingsPresentationBuilder` on `Profile + settingsMap` fixtures → asserts rows grouped by `categoryKey`, value from `get<T>()`, state from `get<LifecycleState>()`, **missing `poolRef` skipped** (US1-4), **derived editability** (InApp vs SystemDialog vs ReadOnly), `sensitivity` inert (all rows shown).
- **Gateway contract test**: `EngineSettingsGateway.apply()` → `ReconcileEngine.run(Single)` → `Provider.apply` + `ProfileStore.save`; failure path keeps prior value + `Failed` state (SC-008, FR-010).
- **Fitness (rule 7)**: no `when(Component subtype)` in settings UI; no Android imports in `SettingsPresentationBuilder`/`SettingsGateway`; VM has no `ReconcileEngine` reference (SC-006, §10).
- **Navigation test**: one settings screen; legacy `SettingsScreen` + entry removed; app-operations reachable (SC-009).
- **i18n coverage (SC-007)**: `PoolI18nCoverageTest` extended to `categoryKey` + absorbed legacy strings (EN+RU).
- **Accessibility (SC-011)**: UI-test asserts each row/button has TalkBack semantics from i18n keys + tap target ≥ 56dp; `Failed`/`Skipped` rows render as status, not tappable actions (no dead buttons). Manual TalkBack pass on emulator (deferred-local-emulator).
- **Emulator (deferred-local-emulator)**: US3 system-dialog + language-change Activity recreation on `pixel_5_api_34`.
- **`TODO(physical-device)` → TASK-128**: OEM launcher-role / status-bar (Xiaomi/Samsung) — `Unverifiable` honesty.

## 8. Risks

| Risk | Mitigation |
|---|---|
| Legacy removal breaks dangling refs (`SettingsComponent` nav, spec-009 admin-mode entry) | **CHK012 audit task** before deletion (Phase in tasks.md) |
| OEM launcher-role/status-bar unreadable | `LifecycleState.Unverifiable` shown honestly, re-apply offered (FR-013); `TODO(physical-device)` → TASK-128 |
| Language change recreates Activity mid-screen | Restore from Profile (Profile-only, I1); precedent `WizardLocaleChangeTest` |
| Edit-during-apply race (double change) | Serialize applies through the gateway; last-write via reconcile result; test the double-change edge |
| App-operations pull in pairing/admin surface | Re-host existing entry points only (FR-020); no new remote-management functionality (FR-019) |

## 9. Required Context Review

- [`ecs.md`](../../docs/architecture/ecs.md) — I1–I3 (self-containment / presentation-on-preset), §10 (gateway seam, fitness). **Primary.**
- [ADR-013](../../docs/adr/ADR-013-canonical-ecs.md) — canonical ECS.
- Constitution: Article VII (profile-driven config), Article IX (perf — settings render), Article IV §5 (state survives recreation), **Article VIII §7 (elderly-safe: tap ≥56dp, contrast ≥4.5:1, TalkBack, no dead buttons — SC-011)**.
- [`docs/compliance/permissions-and-resource-budget.md`](../../docs/compliance/permissions-and-resource-budget.md) — US3 re-hosts the `ROLE_HOME` / status-bar request via Settings (existing capability, no **new** permission added).
- CLAUDE.md rules: 1 (domain isolation), 2 (ACL — RoleManager facade), 4 (MVA — derived editability, builder shaped-not-built for Home), 9 (settingsMap shareable, unchanged).

## 10. Constitution Check

Per Article XVI (run 2026-07-18):

| Gate | Verdict | Note |
|---|---|---|
| 1 Architecture | PASS | Layered UI→port→adapter; no new module; `SettingsGateway`/builder purpose-shaped. |
| 2 Core/System Integration | PASS | System dialog via `RoleManager` facade (rule 2); no BroadcastReceiver/boot; no raw Android callback in feature. |
| 3 Configuration | PASS | No wire-format change (settingsMap unchanged, editability derived); no schemaVersion bump/migrator. |
| 4 Required Context Review | PASS | Links ecs.md, ADR-013, constitution articles, permissions-budget; no new permission (ROLE_HOME re-hosted). |
| 5 Accessibility | PASS (after fix) | **SC-011 added**: tap ≥56dp, contrast ≥4.5:1, TalkBack from i18n, no dead buttons. |
| 6 Battery/Performance | PASS | Reactive `observe` (no polling), no background/boot work, ~20-40-entity linear scan, on-demand screen. |
| 7 Testing | PASS | Port has fake + real (`EngineSettingsGateway`) + DI; builder/gateway/fitness/nav/i18n/a11y tests; no wire change → no roundtrip. |
| 8 Simplicity | PASS | `SettingsGateway` (fake + engine-swap seam) and builder (real Profile→SettingsView transform) justified; "shaped for Home" has a current consumer (Settings), Home not built. |

**OVERALL: 8/8 PASS** (Gate 5 fixed via SC-011). Plan is complete.

## 11. Rollout / Verification

- JVM: `./gradlew :core:test --tests "*Settings*"` + `:app:testDebugUnitTest --tests "*Settings*"` green (builder, gateway, VM, fitness).
- Emulator: US3 dialog + language recreation on `pixel_5_api_34` (deferred-local-emulator AC).
- CI fitness: no-`when(component)` / no-Android-in-domain guards pass.
- Definition of done: single settings screen live, legacy removed, in-app edits persist, failed/unverifiable shown honestly, all `[hand]` AC green; physical-device OEM checks → TASK-128.

---

<!-- NOVICE-SUMMARY:BEGIN -->
## Коротко для владельца

**Что планируем построить.** Экран «Настройки» станет зеркалом-проекцией профиля (что реально настроено), собранной из данных, а не свёрстанной руками под каждый пресет.

**Как устроено по слоям (сверху вниз, стрелки только вниз):**
1. **Экран** (Compose) — тупой: рисует готовый список `SettingsView`, сам ничего не решает, в Android не лезет.
2. **ViewModel** — знает только один «пульт» — порт `SettingsGateway` (показать настройки / применить одну).
3. **Порт `SettingsGateway`** — за ним прячется движок применения (`ReconcileEngine`). Заменим движок — экран и VM не трогаем.
4. **Сборщик `SettingsPresentationBuilder`** — превращает `профиль + settingsMap пресета` в готовый список. Сделан по форме будущего общего слоя для Home+Settings, но **Home сейчас не трогаем**.

**Ключевые решения (уже приняты):**
- Порт вместо прямого дёрганья движка (заменяемость).
- Признак «можно менять» не хранится в формате — **выводится** (есть ли in-app провайдер). Ничего в wire-формате не меняется.
- Старый экран настроек **поглощается** — один экран; его не-профильные пункты (смена пресета, QR, сопряжённые устройства, сброс данных) переносятся как отдельные действия, не как настройки профиля.
- Рендер из JSON — **отложен в TASK-133**.

**Проверки перед кодом пройдены:** конституция 8/8, домен изолирован, формат не меняется, лишних абстракций нет, senior-safe критерии (крупные кнопки, контраст, TalkBack, нет «мёртвых» кнопок) добавлены.

**Что дальше:** `/speckit.tasks` (разбивка на конкретные задачи) → `/speckit.analyze` (финальная сверка) → код в отдельной сессии.
<!-- NOVICE-SUMMARY:END -->
