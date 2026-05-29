# Tasks: Tile Editing — Admin and Senior Profiles (F-014.0)

**Branch**: `014-tile-editing-admin-senior-profiles` | **Date**: 2026-05-29
**Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Research**: [research.md](./research.md) | **Data model**: [data-model.md](./data-model.md) | **Contracts**: [contracts/](./contracts/) | **Quickstart**: [quickstart.md](./quickstart.md)

**Scope**: F-014.0 phase only (local-only DataStore, no server backup, no Google Sign-In). F-014.1 / F-014.2 — separate tasks.md rounds позже.

---

## Overview

**56 задач в 10 фазах**. Каждая трассируется к FR/US/Plan section.

**Маркеры**:
- `[P]` — параллельно с другими `[P]` в той же фазе (разные файлы, нет shared state).
- `[M]` — manual step (требует ручного действия владельца проекта).
- `[CRIT]` — gate перед следующей фазой.
- `[US-N]` — task закрывает User Story N в спеке.

**Push policy** (per CLAUDE.md §Branching): push после каждой фазы. PR открывается после Phase 1.

**Test-first**: для tasks помеченных `[TEST-FIRST]` — написать тест, убедиться что он FAIL, потом писать impl.

---

## Phase 0 — Foundation & DI scaffolding

**Goal**: подготовить инфраструктуру без новой бизнес-логики.

- [ ] **T001** [P] Создать пустые packages `core/commonMain/kotlin/com/launcher/api/edit/` и `core/commonTest/kotlin/com/launcher/api/edit/`. Trace: Plan §3.1.
- [ ] **T002** [P] Создать пустые packages `data/src/main/kotlin/com/launcher/adapter/edit/` и `data/src/test/kotlin/com/launcher/adapter/edit/`. Trace: Plan §3.1.
- [ ] **T003** [P] Создать пустые packages `app/src/main/kotlin/com/launcher/ui/edit/` и `app/src/main/kotlin/com/launcher/ui/edit/integration/`. Trace: Plan §3.1.
- [ ] **T004** Добавить DI module `EditModule` (Koin / Hilt — по existing проектному паттерну) в `app/.../di/` с пустой `bind<NamedConfigsLocalStore>` placeholder. Trace: Plan §6, CLAUDE.md rule §6 (DI wiring).
- [ ] **T005** [P] [CRIT] Добавить Konsist test class skeleton `core/commonTest/.../KonsistDomainIsolationTest.kt` (для Phase 8 fitness functions). Verifies test infra работает. Acceptance: `./gradlew :core:test --tests "*KonsistDomainIsolation*"` runs (zero rules — passes trivially).

**Checkpoint**: package structure + DI module + Konsist infra готовы. **Push**: `feat(014): scaffold edit module structure`.

---

## Phase 1 — Domain types (commonMain pure Kotlin) [CRIT BLOCKING]

**Goal**: все domain types + ports per data-model.md. Никакой UI, никаких adapters. **Полностью JVM-testable.**

### Sealed classes / enums / value types

- [ ] **T010** [P] Создать `EditUiProfile` sealed class в `core/commonMain/.../api/edit/EditUiProfile.kt`. AdminProfile + SeniorProfile objects. Trace: data-model §1, FR-008.
- [ ] **T011** [P] Создать `PickerType` enum в `.../api/edit/PickerType.kt` — 5 variants. Trace: data-model §6, FR-018.
- [ ] **T012** [P] Создать `TargetIdentity` data class в `.../api/edit/TargetIdentity.kt` с invariants (linkId/presetId non-blank, isSelf derived). Trace: data-model §2, FR-009.
- [ ] **T013** [P] Создать `EditError` sealed class в `.../api/edit/EditError.kt` с 6 variants (включая `ProfileSelectionRequiresCapabilityRegistry`). Trace: data-model §5, FR-008b.
- [ ] **T014** [P] Создать `TileEditOperation` sealed class в `.../api/edit/TileEditOperation.kt` — Add/Move/Remove/Replace data classes. Trace: data-model §4, FR-001.
- [ ] **T015** [P] Создать `EditMode` data class в `.../api/edit/EditMode.kt` (active + target + profile, profile derived at construction). Trace: data-model §3.

### Pure function selector

- [ ] **T020** [TEST-FIRST] [P] Написать `EditUiProfileSelectorTest` в `core/commonTest/.../EditUiProfileSelectorTest.kt`. Cases: Workspace→Admin, SimpleLauncher→Senior, unknown built-in→Admin fallback, custom preset→`ProfileSelectionRequiresCapabilityRegistry`. Acceptance: тесты FAIL до T021. Trace: SC-005, FR-008, FR-008a, FR-008b. **Requires**: T010, T013.
- [ ] **T021** Реализовать `EditUiProfileSelector` pure function в `.../api/edit/EditUiProfileSelector.kt` с hardcoded `when` mapping per spec FR-008. Acceptance: T020 PASS. Trace: FR-008, Plan §3.2. **Requires**: T020.

### Tile edit operations (domain verbs)

- [ ] **T025** [TEST-FIRST] Написать `TileEditOperationsTest` в `core/commonTest/.../TileEditOperationsTest.kt`. Cases: add valid/invalid position/flow not found, remove valid/slot not found, move valid/invalid position, replace valid/slot not found. Acceptance: тесты FAIL до T026. Trace: FR-001. **Requires**: T014, T013.
- [ ] **T026** Реализовать `TileEditOperations.apply(op, config): Outcome<ConfigDocument, EditError>` в `.../api/edit/TileEditOperations.kt`. Pure function над `ConfigDocument` (existing спека 008). Acceptance: T025 PASS. Trace: FR-001, FR-002. **Requires**: T025.

### NamedConfig domain

- [ ] **T030** [P] Создать `NamedConfig` data class в `.../api/edit/NamedConfig.kt` с `@Serializable`, `schemaVersion = 1`, companion с constants (MAX_CONFIG_NAME_LENGTH=32, MAX_DESCRIPTION_LENGTH=200, CURRENT_SCHEMA_VERSION=1). Trace: data-model §7, contracts/named-config-local.md.
- [ ] **T031** [P] Создать `NamedConfigsLocalStore` port interface в `.../api/edit/NamedConfigsLocalStore.kt` (configs Flow + 5 suspend ops). Trace: data-model §8, Plan §3.2.
- [ ] **T032** [P] Создать `StoreError` sealed class в `.../api/edit/StoreError.kt` — 5 variants (LimitReached, InvalidName, NameAlreadyExists, NotFound, DefaultMustExist, UnsupportedSchemaVersion). Trace: data-model §8, contracts §Invariants.

### configName validation

- [ ] **T035** [TEST-FIRST] [P] Написать `ConfigNameValidatorTest` в `core/commonTest/.../ConfigNameValidatorTest.kt`. Cases: empty → InvalidName.EmptyName; >32 chars → TooLong; emoji → InvalidChars; valid Cyrillic "Дом" → Valid; valid "home-job 2" → Valid; NFC normalization of "ё" combining → equal. Acceptance: тесты FAIL до T036. Trace: contracts §Validation rules, R6.
- [ ] **T036** [P] Реализовать `ConfigNameValidator` в `.../api/edit/ConfigNameValidator.kt`: NFC normalize, regex `^[\p{L}\p{N} -]+$`, length 1..32. Acceptance: T035 PASS. **Requires**: T035.

### Defensive — EditError exhaustiveness

- [ ] **T038** [P] Написать `EditErrorExhaustivenessTest` (kotlin.test) — `when` over all 6 variants без `else`, compile gate. Acceptance: компилируется без warnings. Trace: failure-recovery.md CHK001.

**Checkpoint**: 17 файлов в `api/edit/`, ~6 unit tests, всё JVM-only. **Push**: `feat(014): domain types + EditUiProfileSelector + TileEditOperations`.

**Phase 1 gate** [CRIT]: `./gradlew :core:test --tests "com.launcher.api.edit.*"` green.

---

## Phase 2 — Wire format & contract tests

**Goal**: NamedConfig wire format roundtrip + invariants documented в contracts/.

- [ ] **T040** [TEST-FIRST] [P] Написать `NamedConfigRoundtripTest` в `core/commonTest/.../NamedConfigRoundtripTest.kt`. Cases: serialize → deserialize → assertEquals для (default config), (config с description), (orphan config с orphanedAt). Trace: contracts/named-config-local.md §Tests, wire-format.md CHK010. **Requires**: T030.
- [ ] **T041** [TEST-FIRST] [P] Написать `NamedConfigSchemaVersionTest`: inject JSON со `schemaVersion: 99` → assert deserialization fails fast (fail-closed policy). Trace: contracts §Forward compatibility, wire-format.md CHK008.
- [ ] **T042** [TEST-FIRST] [P] Написать `NamedConfigDefaultsTest`: parse JSON с missing optional fields (`description`, `orphanedAt`) → assert defaults applied (`""`, `null`). Trace: wire-format.md CHK005.
- [ ] **T043** [P] Сохранить fixture file `core/src/commonTest/resources/fixtures/named-config-default.json` (single default config). Trace: dev-experience.md CHK009.
- [ ] **T044** [P] Сохранить fixture file `core/src/commonTest/resources/fixtures/named-config-three-mix.json` (3 configs: 1 default + 1 active + 1 orphan). Trace: dev-experience.md CHK009.

**Checkpoint**: roundtrip + schema-version + defaults verified. **Push**: `test(014): NamedConfig wire-format contract tests`.

---

## Phase 3 — Adapters (DataStore real + Fake)

**Goal**: реализовать `NamedConfigsLocalStore` port — два adapter'а (Fake + DataStore).

### Fake adapter (для tests)

- [ ] **T050** [TEST-FIRST] [P] Написать `FakeNamedConfigsLocalStoreContractTest` в `data/src/test/.../FakeNamedConfigsLocalStoreContractTest.kt`. Cases per contracts/named-config-local.md §Tests: invariant 1-5 (size limit, default invariant, uniqueness, lifecycle, unsupported version) + validation variants. Trace: FR-003, FR-003a, FR-003c.
- [ ] **T051** Реализовать `FakeNamedConfigsLocalStore` в `data/src/test/.../FakeNamedConfigsLocalStore.kt` — in-memory `MutableStateFlow<List<NamedConfig>>`, atomic ops через mutex. Acceptance: T050 PASS. Trace: Plan §7.3, CLAUDE.md rule §6. **Requires**: T050, T031.

### Real adapter (DataStore)

- [ ] **T055** [TEST-FIRST] Написать `DataStoreNamedConfigsLocalStoreTest` в `data/src/androidTest/.../DataStoreNamedConfigsLocalStoreTest.kt` (Robolectric). Same contract cases как T050 + persistence verification. Trace: FR-003.
- [ ] **T056** Реализовать `DataStoreNamedConfigsLocalStore` в `data/src/main/.../adapter/edit/DataStoreNamedConfigsLocalStore.kt`. Key `f014.named_configs.v1`, JSON serialization, atomic `updateData {}` для `markDefault` / `create`. Acceptance: T055 PASS. Trace: contracts/named-config-local.md §Atomic operations. **Requires**: T055.
- [ ] **T057** Реализовать `bootstrapDefaultConfig()` — на first-launch DataStore пуст → создать single config `(configName="default", isDefault=true, presetId=currentPreset, deviceClass="phone", activeDeviceIds=setOf(thisDeviceId))`. Trace: contracts §Default config bootstrap, FR-003d State 0/1.
- [ ] **T058** [TEST-FIRST] Написать `NamedConfigsProcessDeathTest` — write config → simulate process kill via re-creating DataStore from disk → read → assertEquals. Acceptance: persistence verified. Trace: state-management.md CHK015. **Requires**: T056.

### DI wiring

- [ ] **T060** Обновить `EditModule` DI — bind `NamedConfigsLocalStore` к `DataStoreNamedConfigsLocalStore` для release/debug, `FakeNamedConfigsLocalStore` для test build flavor. Trace: dev-experience.md CHK008, CLAUDE.md §6.

**Checkpoint**: storage port реализован + fake + real, oба тестированы. **Push**: `feat(014): NamedConfigsLocalStore adapters + DI wiring`.

---

## Phase 4 — US1: Admin Workspace self-edit (P1 MVP)

**Goal**: admin может add/move/remove плитки в своём Workspace через long-press → edit mode.

### Edit mode entry & state

- [ ] **T070** Создать `EditModeComposable` в `app/src/main/.../ui/edit/EditModeComposable.kt` — root composable, hosts jiggle decoration + banner. Принимает `EditMode` state, `onExit` callback. Trace: US1 AS1+AS4, FR-005, FR-010.
- [ ] **T071** Создать `EditTopBanner` в `app/src/main/.../ui/edit/EditTopBanner.kt` — banner с «Готово» button (admin self) или «← Назад» (remote target, Phase 5). Strings из `R.string`. Trace: FR-010, FR-014, localization.md CHK001.
- [ ] **T072** Создать `JiggleModifier` (или extend existing если есть) — 2°, 0.4s loop, with `prefers-reduced-motion` honor. При reduced-motion → static frame. Trace: FR-010, FR-011, performance.md CHK005.

### Entry gestures

- [ ] **T075** [TEST-FIRST] [P] Написать `LongPressEditEntryTest` в `app/src/test/.../ui/edit/LongPressEditEntryTest.kt` (Compose UI test). Long-press empty grid cell → EditMode.active flips to true, EditUiProfile derived. Trace: FR-005, US1 AS1.
- [ ] **T076** Реализовать long-press entry в `EditModeComposable` — Compose `detectTapGestures(onLongPress)` на empty grid area. Trace: FR-005. **Requires**: T070, T075.
- [ ] **T077** Реализовать bottom sheet «Виджеты / Обои / Настройки» по long-press пустого места (US1 AS1). **Note**: текущий MVP можем оставить только «Готово» banner без bottom sheet — bottom sheet items deferred to TODO-UX-025 (tutorial onboarding). Document decision inline.

### Empty state

- [ ] **T080** [TEST-FIRST] [P] Написать `EmptyStateTileTest`: пустой ConfigDocument → большой «+» tile в первой ячейке. Tap → picker opens **без** перехода в edit mode (Q6 / FR-020a). Trace: FR-020, FR-020a, US1 AS1.
- [ ] **T081** Реализовать `EmptyStateTile` в `app/src/main/.../ui/edit/EmptyStateTile.kt` — ≥72dp «+» иконка, contentDescription "Добавить плитку". Tap callback → directly open picker. Trace: FR-020, FR-020a, accessibility.md CHK008. **Requires**: T080.

### Unified picker

- [ ] **T085** [TEST-FIRST] [P] Написать `UnifiedPickerSheetTest`: target=Workspace → 5 tabs visible. Trace: FR-018.
- [ ] **T086** Создать `UnifiedPickerSheet` в `app/src/main/.../ui/edit/UnifiedPickerSheet.kt` — Material 3 ModalBottomSheet с 5 tabs (App / Contact / Document / Widget / Action). Trace: FR-018, FR-018a. **Requires**: T085.
- [ ] **T087** [P] Создать `PlaceholderInDevelopmentScreen` в `app/src/main/.../ui/edit/PlaceholderInDevelopmentScreen.kt` для Widget tab tap, Action tab tap, custom preset (FR-008b). Strings externalized. Trace: FR-018, FR-008b, localization.md CHK001.
- [ ] **T088** Wire Widget tab tap + Action tab tap → navigate to `PlaceholderInDevelopmentScreen` с appropriate copy. Trace: FR-018.

### Add / move / remove plotki

- [ ] **T090** Wire picker selection (Application / Contact / Document) → call `TileEditOperations.Add(flowId, slot)` → `ConfigEditor.updateDraft`. Trace: US1 AS2, FR-001, FR-002.
- [ ] **T091** [P] Wire long-press на tile + drag → `TileEditOperations.Move`. Reuse existing `TileDragAndDropModifiers` (спека 009/010). Trace: US1 AS3 (move), FR-010.
- [ ] **T092** Реализовать drag-to-X zone (top of screen) → `TileEditOperations.Remove` → undo snackbar 8s. Trace: US1 AS3 (remove), FR-010.
- [ ] **T093** [P] Реализовать undo snackbar (Material 3) с 8-секундным таймером, restore через `ConfigEditor.updateDraft`. Strings externalized. Trace: FR-010, localization.md.

### Exit edit mode

- [ ] **T095** Wire «Готово» banner button + tap-anywhere outside → `EditMode.active = false`. Trace: US1 AS4, FR-010 ("tap-anywhere").

### State preservation

- [ ] **T098** [TEST-FIRST] Написать `EditModeStateRestorationTest` (Compose `StateRestorationTester`): rotate device в edit mode → `EditMode.active` survives. Trace: state-management.md CHK001, CHK014. **Requires**: T070.
- [ ] **T099** Применить `rememberSaveable` / Decompose state preservation для `EditMode.active`, `EditMode.target`. Acceptance: T098 PASS. Trace: state-management CHK005.

**Checkpoint US1**: admin Workspace self-edit полностью работает локально, без сети, без F-014.1. **Push**: `feat(014): US1 admin Workspace self-edit complete`.

**Phase 4 gate**: `./gradlew :app:test --tests "*EditMode*" --tests "*EmptyStateTile*" --tests "*UnifiedPicker*"` green.

---

## Phase 5 — US2: Admin remote target editing (P1)

**Goal**: admin редактирует бабушкин Simple Launcher через сопряжённое устройство.

- [ ] **T110** Создать `RemoteEditFrame` composable в `app/src/main/.../ui/edit/RemoteEditFrame.kt` — 4dp colored frame around grid. Trace: FR-014.
- [ ] **T111** Расширить `EditTopBanner` для remote case: показывать «Редактируешь телефон <name>» + «← Назад» button. Name из target alias (existing спека 007 pairing). Strings format с `%s`. Trace: FR-014, localization CHK001, CHK009 (gender-neutral fallback "Редактируешь сопряжённое устройство" если alias пуст).
- [ ] **T112** Расширить `EditModeComposable` — если `target.isSelf == false` → render `RemoteEditFrame` + remote banner. Если `isSelf == true` → no frame. Trace: FR-014, FR-015.
- [ ] **T115** [TEST-FIRST] [P] Написать `RemoteEditIntegrationTest`: target=SimpleLauncher remote → picker shows 3 tabs only (App / Contact / Document), Widget+Action hidden per FR-019. Trace: FR-019, SC-006.
- [ ] **T116** Реализовать picker tab filtering в `UnifiedPickerSheet` по `targetPresetId`: SimpleLauncher → 3 tabs, others → 5 tabs. Acceptance: T115 PASS. Trace: FR-019.
- [ ] **T120** Wire entry: tap на target плитке в `admin_devices` Flow (existing спека 009) → open `EditModeComposable` с `target.linkId = pairedManagedLinkId`, `target.isSelf = false`. Trace: FR-007, FR-006a.
- [ ] **T121** Wire exit: «← Назад» banner button → return to `admin_devices` Flow (don't lose place). Trace: US2 AS4, US4 AS3.
- [ ] **T125** [TEST-FIRST] [P] Написать `ConcurrentEditConflictAdminTest`: admin push → `ConfigSyncError.Conflict` → snackbar «Бабушка только что изменила. [Обновить] [Перезаписать]» visible. Trace: FR-016, US2 AS5.
- [ ] **T126** Реализовать `ConflictSnackbar` в `app/src/main/.../ui/edit/ConflictSnackbar.kt` — Material 3 snackbar с двумя actions для admin profile. Conditional on `EditMode.profile == AdminProfile`. Trace: FR-016 (admin branch), Q7.
- [ ] **T127** Wire «Обновить» → re-fetch via `ConfigEditor.refresh()` + merge UI. Wire «Перезаписать» → `ConfigEditor.pushPending(force=true)`. Trace: FR-017.

**Checkpoint US2**: admin remote target edit работает через FakeConfigEditor; visual indicators + filtered picker + admin-side conflict UI. **Push**: `feat(014): US2 admin remote target edit + conflict UI`.

---

## Phase 6 — US3: Senior 7-tap local edit (P2)

**Goal**: бабушка через 7-tap входит в edit mode своего Simple Launcher'а локально.

- [ ] **T130** [TEST-FIRST] [P] Написать `SevenTapEntrySeniorTest`: 7 тапов в пустое место в 5-секундном окне → challenge gate triggered → edit mode entry с `target.isSelf=true`, `target.presetId="simple-launcher"`, `EditUiProfile=SeniorProfile`. Trace: FR-006, US3 AS1, AS2.
- [ ] **T131** Wire 7-tap gesture (existing спека 010 `SevenTapGateModifier`) → challenge gate → on success → `EditMode.enter(localSimpleLauncherTarget)`. Trace: FR-006. **Requires**: T070.
- [ ] **T135** [TEST-FIRST] [P] Написать `ConcurrentEditConflictSeniorTest`: senior local write + admin remote push в это же время → senior write applies silent'но (no UI dialog), admin'у возвращается conflict. Trace: FR-016 (senior branch), Q7, US3 AS implicit.
- [ ] **T136** Реализовать silent senior-side conflict path: на `ConfigSyncError.Conflict` при `EditMode.profile == SeniorProfile` — no `ConflictSnackbar`, write retried as `pushPending(force=true)`. Acceptance: T135 PASS. Trace: FR-016, FR-017 (senior never sees "Перезаписать"). **Requires**: T126.
- [ ] **T140** Verify use-mode rendering після exit: бабушка завершила edit → возврат в use mode с senior-safe rules (≥56dp tap targets, no jiggle, no hidden gestures). Trace: FR-021, US3 AS5, elderly-friendly.md CHK014-CHK016.

**Checkpoint US3**: senior 7-tap → edit mode → exit → use mode round-trip works. **Push**: `feat(014): US3 senior 7-tap local edit + silent conflict resolution`.

---

## Phase 7 — US4: Admin multi-target navigation (P2)

**Goal**: admin переключается между Workspace, target #1, target #2.

- [ ] **T150** Verify (smoke) admin BottomFlowBar «Управление устройствами» tab tap → `admin_devices` Flow (existing спека 009). Trace: US4 AS1.
- [ ] **T151** Wire tap на target плитке в `admin_devices` → `EditModeComposable` для target (already done в Phase 5 T120). Verify navigation preserves place в Flow. Trace: US4 AS2.
- [ ] **T152** Verify «Назад» из target Editor → возврат на `admin_devices` tab без потери scroll position. Trace: US4 AS3.

**Checkpoint US4**: multi-target navigation работает. **Push**: `feat(014): US4 admin multi-target navigation`.

---

## Phase 8 — Accessibility (FR-012a) + Fitness functions

**Goal**: TalkBack alternative для drag-and-drop + Konsist domain isolation + APK size gate.

### Accessibility (FR-012a)

- [ ] **T160** [TEST-FIRST] [P] Написать `TileContextMenuAccessibilityTest`: при `AccessibilityManager.isTouchExplorationEnabled() == true` (mocked) → long-press на плитке → context menu с 5 actions ("Переместить вверх / вниз / влево / вправо / Удалить"). Trace: FR-012a, accessibility.md CHK010, Plan R1.
- [ ] **T161** Реализовать `TileContextMenu` composable в `app/src/main/.../ui/edit/TileContextMenu.kt` — Material 3 DropdownMenu. Strings externalized. Trace: FR-012a. **Requires**: T160.
- [ ] **T162** Wire long-press на tile → если `TouchExplorationEnabled` → open `TileContextMenu`, иначе → standard drag. Action callbacks → `TileEditOperations.Move` / `Remove`. Trace: FR-012a, US3 AS3 fallback path.
- [ ] **T163** [P] Добавить `contentDescription` для всех interactive elements: empty state «+» ("Добавить плитку"), edit «×» ("Удалить плитку %s"), banner «← Назад» ("Выйти из режима редактирования"), drag handle ("Переместить плитку %s"), «Готово» ("Завершить редактирование"). Trace: accessibility.md CHK008.

### Konsist fitness functions (per CLAUDE.md §7)

- [ ] **T170** [P] [CRIT] Реализовать Konsist rule в `core/commonTest/.../KonsistDomainIsolationTest.kt`: no class в `core.commonMain.api.edit` импортирует `android.*`, `androidx.*`, `com.google.firebase.*`, `okhttp3.*`, retrofit. Acceptance: passes against current code. Trace: CLAUDE.md rule 1, domain-isolation.md, Plan §7.4.
- [ ] **T171** [P] [CRIT] Реализовать Konsist rule: no `expect`/`actual` declarations в `core.commonMain.api.edit`. Trace: CLAUDE.md rule 1, domain-isolation.md CHK016, Plan §7.4.

### APK size gate

- [ ] **T175** [M] Снять baseline APK size pre-F-014 — `git checkout main && ./gradlew :app:assembleRelease && ls -la app/build/outputs/apk/release/app-release.apk`. Record в `specs/014-.../perf-checkpoint.md`. Trace: SC-008, performance.md CHK017.
- [ ] **T176** [M] Снять post-F-014 APK size — `git checkout 014-... && ./gradlew :app:assembleRelease`. Record delta. Acceptance: delta ≤ 300 KB per SC-008. Trace: SC-008.
- [ ] **T177** [P] Добавить APK size delta CI check (gradle task или CI workflow YAML). Fails build если delta > 300 KB. Trace: SC-008, Plan §7.4.

**Checkpoint accessibility + fitness**: TalkBack ok, Konsist rules enforce domain isolation. **Push**: `feat(014): FR-012a TalkBack alt + Konsist fitness + APK size gate`.

---

## Phase 9 — Localization & polish

**Goal**: extract all strings to `strings.xml`, add Russian plurals, RTL verify.

### Strings externalization

- [ ] **T180** [P] Создать `app/src/main/res/values/strings_f014.xml` с **13 string keys** per localization.md CHK001 inventory:
  - `f014_remote_edit_banner_format` "Редактируешь телефон %s"
  - `f014_remote_edit_banner_fallback` "Редактируешь сопряжённое устройство"
  - `f014_banner_back` "← Назад"
  - `f014_banner_done` "Готово"
  - `f014_remove_snackbar_format` "Удалено: %s"
  - `f014_remove_snackbar_undo` "Отменить"
  - `f014_conflict_admin_snackbar_format` "%s только что изменил(а). Обновить?"
  - `f014_conflict_admin_overwrite` "Перезаписать"
  - `f014_picker_tab_apps` "Приложения"
  - `f014_picker_tab_contacts` "Контакты"
  - `f014_picker_tab_widgets` "Виджеты"
  - `f014_picker_tab_documents` "Документы"
  - `f014_picker_tab_actions` "Действия"
  - `f014_placeholder_widget_title` + `f014_placeholder_widget_body` (FR-018 wording)
  - `f014_placeholder_action_title` + `f014_placeholder_action_body` (FR-018 wording)
  - `f014_placeholder_custom_preset` "Custom presets появятся в будущих обновлениях"
  - `f014_empty_state_add` "Добавить плитку"
  - `f014_context_menu_move_up` / `move_down` / `move_left` / `move_right` / `delete`
  - Trace: localization.md CHK001, FR-008b, FR-010, FR-014, FR-018.

### Russian plurals

- [ ] **T181** [P] Создать `app/src/main/res/values/plurals_f014.xml`:
  - `f014_orphan_countdown_days` (one/few/many для "истёк через N дней")
  - `f014_orphan_unused_days` (one/few/many для "не используется N дней" — used в F-014.1; included now)
  - Trace: localization.md CHK002.

### RTL verify

- [ ] **T185** [M] Manual smoke: переключить эмулятор на RTL pseudo-locale → edit mode → verify banner «← Назад» mirror'ится, grid layout flows correctly, snackbar action position. Trace: localization.md CHK005.

### Doc updates

- [ ] **T190** [P] Update `docs/dev/server-roadmap.md` — add F-014.1 entry per backend-substitution.md CHK004. Inline content из research.md §2 "Server-roadmap entry". Trace: backend-substitution.md CHK004, CLAUDE.md rule 8.
- [ ] **T191** [P] Update `docs/dev/project-constants.md` — add «Edit UX profiles (admin / senior)» constant + «Progressive disclosure: multi-X UI hidden until X count > 1» principle. Trace: spec §Dependencies "Updates", project-constants.md reference.
- [ ] **T192** [P] Update `docs/product/roadmap.md` — добавить F-4 dependency arrow для F-014.1 phase. Mark F-014.0 as IN PROGRESS. Trace: spec §Dependencies "Updates".
- [ ] **T193** [P] Update `docs/dev/project-backlog.md` — добавить 6 TODO entries: TODO-UX-025/026/027/028, TODO-FUTURE-UX-012, TODO-FUTURE-DESIGN-PRINCIPLE. Trace: spec §Dependencies "Updates".

**Checkpoint polish**: i18n complete, RTL verified, docs updated. **Push**: `chore(014): strings extraction + plurals + doc updates`.

---

## Phase 10 — Integration smoke + quickstart validation

**Goal**: 2-эмулятор smoke verification + quickstart.md walkthrough.

- [ ] **T200** [M] 2-эмулятор smoke per quickstart.md «2-emulator smoke (manual)» section. Verify all 3 flows:
  - Admin Pixel 7 Workspace self-edit (US1).
  - Admin Pixel 7 → tap on Managed Pixel 4a tile in admin_devices → Target Editor с frame+banner (US2).
  - Managed Pixel 4a Simple Launcher → 7-tap → challenge → edit mode (US3).
  Document результаты в `perf-checkpoint.md`. Trace: SC-001 (≤4 тапа admin add), SC-002 (≤5 тапов senior remove), SC-007 (7-tap reliability).
- [ ] **T201** [M] OEM smoke на Xiaomi Mi 11 Lite 5G (physical device) — verify long-press dispatch не конфликтует с MIUI. Trace: permissions-platform.md CHK009, Plan R2.
- [ ] **T202** [P] [M] Run quickstart.md commands end-to-end — verify no manual step missed. Update quickstart.md если найдены gaps. Trace: dev-experience.md CHK022.
- [ ] **T203** [P] Run `procedure-cross-artifact-trace` для finalizing tasks ↔ FR/US coverage. Acceptance: 100% trace, no orphan FR. Trace: spec-kit governance.

**Phase 10 gate** [CRIT]: все 3 smoke flows green, OEM test pass, quickstart steps reproducible.

---

## Dependencies & Execution Order

### Phase dependencies

- Phase 0 (Foundation) → Phase 1 (Domain) → blocks all subsequent.
- Phase 2 (Wire format) parallel с Phase 1 после T030 ready.
- Phase 3 (Adapters) requires Phase 1 + Phase 2.
- Phase 4 (US1 MVP) requires Phase 3.
- Phase 5 (US2) requires Phase 4 (reuses EditModeComposable, picker, etc.).
- Phase 6 (US3) requires Phase 4 + Phase 5 (reuses conflict snackbar logic, inversed для senior silent path).
- Phase 7 (US4) requires Phase 5 (admin_devices tile tap → target editor).
- Phase 8 (Accessibility + Fitness) parallel с Phase 4-7 partially; Konsist (T170-T171) можно начать сразу после Phase 1.
- Phase 9 (Localization) parallel со всеми UI phases — strings.xml grows incrementally.
- Phase 10 (Smoke) — финальный gate перед merge.

### MVP path (минимальный shippable F-014.0)

Если нужно ship'ить раньше — **минимальный набор**:
- Phase 0, 1, 2, 3, 4 (US1).
- Phase 8 (T170-T171 Konsist + T175-T177 APK size).
- Phase 9 (T180 strings).
- Phase 10 (T200 single-emulator smoke US1 only).

Это **admin self-edit only**. US2/US3/US4 откладываются на rev.2 PR.

**Recommended path**: full Phase 0-10 в одном PR (per CLAUDE.md "One feature = one branch = one PR"). Spec'и 014 atomically delivers все 4 US.

### Parallelization opportunities

- Phase 1: T010-T015 all `[P]`, T020+T021 sequential, T025+T026 sequential, T030-T032 `[P]`, T035+T036 sequential.
- Phase 2: T040-T044 all `[P]`.
- Phase 3: T050+T051 sequential, T055+T056 sequential, T060 last.
- Phase 4-7: tasks без `[P]` обычно зависят от UI compositional order. `[P]` tasks внутри фазы можно делать параллельно.
- Phase 8: Konsist (T170, T171) `[P]` после Phase 1 — можно начать раньше.
- Phase 9: T180, T181, T190-T193 все `[P]`.

---

## Required-task gates verification

Per `speckit-tasks` Step 3:

✅ **Contracts roundtrip**: T040 (NamedConfig roundtrip), T041 (schemaVersion fail-closed), T042 (defaults).
✅ **Backward-compat**: N/A для v1 initial (no v0 exists).
✅ **Ports → fake adapters**: T051 (FakeNamedConfigsLocalStore). `EditUiProfileSelector` — pure function, fake не нужен.
✅ **New modules → Konsist**: T170, T171 (domain isolation rules).
✅ **Removed files**: F-014.0 ничего не удаляет.
✅ **Docs impacted**: T190 (server-roadmap), T191 (project-constants), T192 (roadmap), T193 (project-backlog).
✅ **UI screenshot/smoke**: T200 2-эмулятор smoke. Screenshots — manual во время smoke run, attach к perf-checkpoint.md.
✅ **Perf checkpoint**: T175+T176 APK size measurement, recorded в `perf-checkpoint.md`.

---

## Cross-artifact trace (FR ↔ task coverage)

| FR | Tasks |
|---|---|
| FR-001 (domain verbs) | T014, T025, T026 |
| FR-002 (ConfigEditor reuse) | T026, T090, T127 |
| FR-003 (named configs core) | T030, T031, T050, T051, T055-T058 |
| FR-003a (single-default invariant) | T050, T051, T056 |
| FR-003b (orphan UI marker) | N/A в F-014.0 UI (UI marker — F-014.1 My Configs screen); поле существует в domain (T030) |
| FR-003c (5-config limit) | T032, T050 |
| FR-003d (progressive disclosure) | T057 (bootstrap), T060 (DI). UI hidden by default — automatic since My Configs UI не строится в F-014.0 |
| FR-003e/f/g/h/i | **deferred to F-014.1** (My Configs screen, push edit dialog, anonymous→Google migration, compatibility filter). F-014.0 only single-config domain shape. |
| FR-004 (operations both profiles) | T026 (single ops impl); profile difference в presentation layer T086 (picker filter), T126/T136 (conflict UX split) |
| FR-005 (long-press entry Workspace) | T075, T076 |
| FR-006 (7-tap entry SimpleLauncher) | T130, T131 |
| FR-006a (remote bypass gesture) | T120 |
| FR-007 (target editor entry via tile tap) | T120, T151 |
| FR-008 (profile selector pure function) | T020, T021 |
| FR-008a (exit ramp F-2) | Documented в T021 + research.md §1 |
| FR-008b (fallback split) | T013, T020, T021, T087, T088 |
| FR-009 (profile by target preset) | T020, T021, T070, T112 |
| FR-010 (admin profile UX) | T070, T071, T072, T086, T090-T093, T095 |
| FR-011 (prefers-reduced-motion) | T072 |
| FR-012 (universal edit mode UX) | T070, T086, T090-T093 (single set of edit ops shared across profiles) |
| FR-012a (TalkBack drag alternative) | T160-T162 |
| FR-013 (use-mode senior tap-target ≥56dp) | T140 (verify in smoke) — actual rendering inherits existing спека 003/010 Simple Launcher rules |
| FR-014 (remote frame + banner) | T110, T111, T112 |
| FR-015 (no frame/banner on self) | T112 |
| FR-016 (conflict detection + split UX) | T125, T126, T127, T135, T136 |
| FR-017 (overwrite admin / silent senior) | T127, T136 |
| FR-018 (picker tabs Workspace) | T085, T086, T087, T088 |
| FR-018a (placeholder visibility rationale) | T087, T088 |
| FR-019 (picker filtered SimpleLauncher) | T115, T116 |
| FR-020 (empty state «+») | T080, T081 |
| FR-020a (empty state tap → picker direct) | T080, T081 |
| FR-021 (use-mode rendering rules) | T140 (verify в smoke) — existing rendering inherited |

| US | Tasks |
|---|---|
| US1 (admin Workspace self-edit) | T070-T099, T080-T081, T085-T088 |
| US2 (admin remote target) | T110-T127 |
| US3 (senior 7-tap local) | T130-T140 |
| US4 (admin multi-target nav) | T150-T152 |

| SC | Verification task |
|---|---|
| SC-001 (≤4 тапа admin add) | T200 smoke |
| SC-002 (≤5 тапов senior remove) | T200 smoke |
| SC-003 (push success / conflict resolution) | T125, T135 integration tests |
| SC-003a (named configs count gating) | T030, T050 |
| SC-003b (orphan UI marker) | **deferred to F-014.1** |
| SC-004 (no split-brain) | T125, T135, T136 |
| SC-005 (profile selection 100%) | T020, T021 |
| SC-006 (Widget/Action never shown for SimpleLauncher) | T115, T116 |
| SC-007 (7-tap reliability) | T200 smoke (existing спека 010 tests cover) |
| SC-008 (APK ≤300 KB delta) | T175, T176, T177 |

---

## Notes / gotchas

- **Existing `EditorScreen`/`EditorComponent` (спека 009)** — F-014 **расширяет**, не переписывает. Phase 5 T110-T112 hook'и встраиваются в existing structure. Перед началом Phase 5 — прочитать спека 009 для понимания integration points.
- **AddSlotWizardComponent (спека 005)** — переиспользуется внутри `UnifiedPickerSheet`. T086 wraps existing wizard, не создаёт новый.
- **TileDragAndDropModifiers (спека 009/010)** — переиспользуется в T091. Verify что modifier гонорит `EditMode.active` flag.
- **7-tap gesture (спека 010)** — T131 hooks existing `SevenTapGateModifier` → новый callback path для edit entry.
- **F-014.1 readiness**: после Phase 10 merge — открыть new branch `015-tile-editing-server-backup` (F-014.1) когда F-4 ready.
- **TODO inline comments**: при первом написании `DataStoreNamedConfigsLocalStore` (T056) и `NamedConfigsLocalStore` port (T031) — добавить `// TODO(server-roadmap): F-014.1 add RemoteNamedConfigsStore adapter; merge local + remote at use site` per CLAUDE.md rule 8.
- **AccessibilityManager mocking** (T160): use Robolectric `ShadowAccessibilityManager` or inject via DI — depend on existing project patterns.

---

## TL;DR на русском

**Что внутри**: 56 задач разделённых на 10 фаз для F-014.0 phase (local-only, без server backup).

**Главные milestones**:
1. **Phase 0-3** (foundation + domain + adapters): 30 задач, всё JVM-testable, без UI. Закладываем `EditUiProfileSelector`, `TileEditOperations`, `NamedConfigsLocalStore` port + DataStore adapter + Fake.
2. **Phase 4** (US1 admin self-edit, P1 MVP): 13 задач — edit mode + empty state «+» + unified picker + drag/drop + undo + exit. После этой фазы admin может настраивать свой Workspace.
3. **Phase 5** (US2 admin remote editing, P1): 8 задач — frame + banner + filtered picker (3 tabs для Simple Launcher) + admin conflict snackbar.
4. **Phase 6** (US3 senior 7-tap local, P2): 5 задач — entry через 7-tap + silent senior conflict resolution (last-local-write-wins per Q7).
5. **Phase 7** (US4 multi-target navigation, P2): 3 задачи — verify навигация Workspace ↔ target editors.
6. **Phase 8** (Accessibility FR-012a + Konsist): 8 задач — TalkBack context menu для drag-and-drop + Konsist domain isolation rules + APK size CI gate.
7. **Phase 9** (Localization + docs): 6 задач — `strings_f014.xml` (~17 ключей) + Russian plurals + 4 doc updates.
8. **Phase 10** (Smoke + cross-artifact trace): 4 задачи — 2-эмулятор manual smoke + OEM test (Xiaomi MIUI) + quickstart validation + final FR coverage check.

**Минимальный MVP**: phases 0-4 + Konsist + strings + single-эмулятор smoke = только admin self-edit (US1). US2/US3/US4 → rev.2 PR если нужно ship'ить раньше.

**Главные риски в tasks**:
- T160-T162 (TalkBack): R1 в плане; самая значительная accessibility работа, может потребовать iterations.
- T201 OEM smoke на Xiaomi: physical device — может выявить MIUI long-press conflict требующий quick-fix.
- T175-T176 APK size: если delta > 300 KB — нужен ревизия, что повлияло (jiggle animation libraries? string resources?).

**Что НЕ делается в F-014.0** (откладывается на F-014.1+):
- FR-003e (push edit dialog для multi-config).
- FR-003f (My Configs screen).
- FR-003g (anonymous→Google migration).
- FR-003h (target named configs cross-device).
- FR-003i (compatibility check at first install).
- SC-003b orphan UI marker рендеринг.
- ConfigDocument schema bump 1→2.
- Roundtrip backward-compat fixture v1 (не нужен — v1 initial).
