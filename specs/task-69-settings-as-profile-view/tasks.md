# Tasks: Settings as Profile View (TASK-69)

Source: [spec.md](spec.md), [plan.md](plan.md), [data-model.md](data-model.md). Model: [`ecs.md`](../../docs/architecture/ecs.md) (do not restate). No new wire format → no roundtrip/backcompat tasks. `[P]` = parallel-safe.

## Phase 1 — Domain types + port (`core/preset/`, pure Kotlin)

- [x] **T069-001** [P] Add view types `SettingsView` / `SettingsSection` / `SettingRow` / `RowState` / `RowKind` / `AppOperation` in `core/preset/settings/SettingsView.kt`. Trace: FR-001, FR-004, FR-020, data-model.md. Acceptance: compiles; `RowState` is a 1:1 projection of the 5 `LifecycleState` variants.
- [x] **T069-002** [P] Add `SettingsGateway` port + `ApplyResult` in `core/preset/port/SettingsGateway.kt`. Trace: FR-008, plan §3. Acceptance: compiles; zero Android imports.
- [x] **T069-003** Add `SettingsPresentationBuilder` skeleton (`build(profile, settingsMap): SettingsView`, injected `LocalizedResources` + editability fn) in `core/preset/settings/`. Trace: FR-000, FR-002. Requires: T069-001.

## Phase 2 — Builder logic + tests (`core:test`, JVM)

- [x] **T069-004** Builder: for each `settingsMap` entry find entity by `id == poolRef` (I5), **skip if absent**, group rows by `categoryKey`. Trace: FR-002, FR-003, ecs.md I5. Requires: T069-003.
- [x] **T069-005** Builder: project value via `entity.get<T>()` (pre-formatted `valueText`) + state via `entity.get<LifecycleState>()` → `RowState`. Trace: FR-004, FR-013. Requires: T069-004.
- [x] **T069-006** Builder: derive `RowKind` (InApp / SystemDialog / ReadOnly) from provider capability — **no wire field**. Trace: FR-015, rule 4. Requires: T069-004.
- [x] **T069-007** Builder: `sensitivity` read but **not** gating (all rows shown in MVP). Trace: FR-016. Requires: T069-004.
- [x] **T069-008** Builder unit tests (fixtures with filled `settingsMap`): skip-missing, grouping, value, state, derived editability, sensitivity-inert, deterministic output. Trace: US1, SC-001, SC-010. Requires: T069-005, T069-006, T069-007.

## Phase 3 — Fake + adapter + DI

- [x] **T069-009** [P] `FakeSettingsGateway` (in-memory `SettingsView` + recorded `apply`) for UI/VM tests (rule 6). Trace: plan §7. Requires: T069-002.
- [x] **T069-010** `EngineSettingsGateway : SettingsGateway` (adapter): `observe()` = combine(`ProfileStore.observe()`, active `Preset` via `PresetSource`) → `SettingsPresentationBuilder.build()`. Trace: FR-005, FR-008, I2. Requires: T069-003, T069-002.
- [x] **T069-011** `EngineSettingsGateway.apply()` → `ReconcileEngine.run(Single)`; Ok → persist new value; Failed → keep prior + `Failed` state; system-dialog component → `NeedsSystemDialog`. Trace: FR-009, FR-010, FR-011. Requires: T069-010.
- [x] **T069-012** DI (Hilt): bind `SettingsGateway` → `EngineSettingsGateway`. Trace: plan §3, rule 6. Requires: T069-010.
- [x] **T069-013** Gateway contract test on `FakeProvider`/`FakeProfileStore`/`FakePresetSource`: apply → engine → `Provider.apply` + `ProfileStore.save`; failure keeps prior value. Trace: SC-008, FR-010. Requires: T069-011.

## Phase 4 — ViewModel + Screen (`app/`, Compose)

- [x] **T069-014** `SettingsViewModel` depends **only** on `SettingsGateway`: `observe()` → `SettingsUiState`; `onChange` → `apply`; app-op callbacks → navigation. Trace: FR-005, FR-007. Requires: T069-002.
- [x] **T069-015** `SettingsScreen` composable renders `SettingsView` (sections → rows → actions). **No `when(component)`, no Android calls.** Trace: FR-001, SC-006. Requires: T069-001, T069-014.
- [x] **T069-016** Row UI: value + state badge; edit control **only** for `RowKind.InApp`; `ReadOnly` shows value+state without edit. Trace: FR-004, FR-015. Requires: T069-015.
- [x] **T069-017** System-dialog row (`RowKind.SystemDialog`, `LauncherRole`/`StatusBarPolicy`): tap → one-step flow (not full wizard) → return to Settings; cancel leaves state intact. Trace: US3, FR-011, FR-012, SEQ-3. Requires: T069-016, T069-011.
- [x] **T069-018** Accessibility: interactive rows/buttons tap target ≥ 56dp, contrast ≥ 4.5:1, TalkBack semantics from i18n keys; `Failed`/`Skipped` rows render as status, not tappable. Trace: SC-011, Article VIII §7. Requires: T069-016.
- [x] **T069-019** Language change from Settings recreates Activity; screen restores from Profile (I1). Trace: Article IV §5, edge case. Requires: T069-015.

## Phase 5 — Legacy absorption + app-operations

- [x] **T069-020** Dangling-ref audit of legacy `SettingsScreen` + **navigation-stack reconciliation**. Confirmed call sites: `RootContent.kt` (renders it), `RootChild.kt` / `RootComponent.kt` / `SettingsComponent.kt` (Decompose nav graph), `SettingsScreenTest.kt`, spec-009 admin-mode entry. **Decide the single host**: legacy is on the **Decompose** nav (`RootComponent`→`SettingsComponent`), the ECS-era screen is an **Activity** (`SettingsActivity`) — plan §3 makes `SettingsActivity` the single host, so the Decompose `SettingsComponent` + `RootChild` entry are torn down and app-operations nav re-wired to the Activity host. Output: migration list + the teardown plan. Trace: FR-017, CHK012. Requires: —.
- [x] **T069-021** Re-host app-operations as `AppOperation` actions (preset switch, pairing-QR, admin-devices, data-reset) wired to **existing** navigation/actions (internals not rewritten). Trace: FR-020, SEQ-4. Requires: T069-015, T069-020.
- [x] **T069-022** «Язык» from legacy → profile-projection row (`Language` component), not an app-operation. Trace: FR-021. Requires: T069-016.
- [x] **T069-023** Delete legacy `core/…/ui/screens/SettingsScreen.kt` + its `SettingsComponent` entry. Trace: FR-017, SC-009. Requires: T069-021, T069-022.
- [x] **T069-024** Grep-verify no dangling references to the deleted screen remain (build green). Trace: FR-017, CHK012. Requires: T069-023.
- [x] **T069-025** Navigation test: single settings screen; legacy entry gone; all app-operations reachable. Trace: SC-009. Requires: T069-023.

## Phase 6 — i18n + fitness

- [ ] **T069-026** Externalize absorbed legacy hardcoded strings («Настройки», «Язык», «Пресет», «Удалённое управление», «Сопряжённые устройства», «Сбросить данные», dialogs) to i18n keys (EN+RU). Trace: SC-007, FR-020. Requires: T069-021.
- [ ] **T069-027** Extend `PoolI18nCoverageTest` to `categoryKey` + new keys (EN+RU coverage). Trace: SC-007. Requires: T069-026.
- [ ] **T069-028** [P] Fitness: no `when(Component subtype)` in settings UI. Trace: SC-006, rule 1. Requires: T069-015.
- [ ] **T069-029** [P] Fitness: no Android imports in `SettingsPresentationBuilder` / `SettingsGateway`. Trace: rule 1, §10. Requires: T069-003, T069-002.
- [ ] **T069-030** [P] Fitness: `SettingsViewModel` has no `ReconcileEngine` reference (only the port). Trace: §10, FR-008. Requires: T069-014.

## Phase 7 — Emulator / device verification (deferred)

> **[deferred-local-emulator]** T069-031–033 deferred until an AVD the session can drive (≤ API 34) is available; the AI session does not visually verify a running screen (memory `reference_compose_ui_test_api_mismatch`).
> **[deferred-physical-device]** T069-034 needs a real OEM device (Xiaomi 11T) — memory `reference_testing_environment`.

- [ ] **T069-031** [deferred-local-emulator] Emulator smoke US3: launcher-role change opens **one** system-dialog step and returns to Settings. Trace: US3, SC-003.
- [ ] **T069-032** [deferred-local-emulator] Emulator: language change recreates Activity and restores the screen. Trace: Article IV §5.
- [ ] **T069-033** [deferred-local-emulator] Manual TalkBack pass on emulator (semantics reachable, no dead buttons). Trace: SC-011.
- [ ] **T069-034** [deferred-physical-device] OEM launcher-role / status-bar on Xiaomi 11T → honest `Unverifiable` + re-apply, not false «настроено». `TODO(physical-device)` in code → aggregated in TASK-128. Trace: FR-013, OEM Matrix.

---

<!-- NOVICE-SUMMARY:BEGIN -->
## Коротко для владельца

34 задачи, 7 фаз, снизу вверх — сначала «кирпичи», потом экран, потом уборка старого:

1. **Домен** (T001-003): типы данных + «пульт» `SettingsGateway` + сборщик.
2. **Сборщик + тесты** (T004-008): превратить профиль+пресет в готовый список; проверить на фейках (пропуск отсутствующих, группировка, значение, статус, «можно ли менять»).
3. **Адаптер + DI** (T009-013): за «пультом» подключить реальный движок; фейк для тестов; контрактный тест.
4. **Экран** (T014-019): ViewModel + Compose-экран (тупой рендер), правка in-app настроек, системный диалог одним шагом, крупные кнопки/TalkBack, смена языка.
5. **Поглощение старого экрана** (T020-025): аудит ссылок → перенести действия (сброс/QR/сопряжённые) → удалить старый экран → проверить, что ничего не сломалось.
6. **Локализация + фитнес** (T026-030): вынести хардкод-строки в EN+RU; авто-проверки чистоты (нет Android в домене, нет логики по типу в экране).
7. **Эмулятор/устройство** (T031-034, **отложены**): визуальные прогоны (системный диалог, смена языка, TalkBack) и OEM-проверка на Xiaomi — их AI-сессия не делает, помечены `[deferred-*]`, закроются вручную/на железе.

**Ничего в сохранённом формате не меняется.** Код — в отдельной сессии (по твоему правилу), задачи готовы к исполнению.
<!-- NOVICE-SUMMARY:END -->
