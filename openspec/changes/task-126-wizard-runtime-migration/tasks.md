## 1. New Component Subtypes + Pool Schema

- [ ] 1.1 Add `LauncherRole` data class to `core/commonMain/com/launcher/preset/component/`
- [ ] 1.2 Add `LauncherRoleProvider` to `core/androidMain/com/launcher/preset/provider/` — check/apply via `RoleManager` API
- [ ] 1.3 Add `Theme` data class with fields `paletteSeedHex`, `typographyScale`, `shapeStyle`, `darkMode`
- [ ] 1.4 Add `ThemeRef` value class + `ThemeCatalog` resolver reading `theme-catalog.json` from assets
- [ ] 1.5 Add `ThemeProvider` to androidMain — applies via `AppThemeController` (new port)
- [ ] 1.6 Add `Language` data class with `locale: String` sentinel `"system"`
- [ ] 1.7 Add `LanguageProvider` to androidMain — applies via `AppCompatDelegate.setApplicationLocales()`
- [ ] 1.8 Add `StatusBarPolicy` data class (no parameters)
- [ ] 1.9 Add `StatusBarPolicyProvider` to androidMain — applies via `WindowInsetsControllerCompat`
- [ ] 1.10 Add `requires: List<ComponentId>?` field to pool JSON component descriptor model
- [ ] 1.11 Add `PresetValidator` in domain — validate `wizardFlow` ordering against `requires` declarations
- [ ] 1.12 Add `hintFlow: List<HintFlowEntry>?` field to `Preset` domain model
- [ ] 1.13 Add `HintFlowEntry` data class + `hint-pool.json` loader in androidMain assets
- [ ] 1.14 Bump `Preset` schemaVersion to 2, `Pool` schemaVersion to 2 — additive, backward-compat read
- [ ] 1.15 Wire new subtypes + providers into `task120Module` Koin bindings
- [ ] 1.16 Unit tests: `LauncherRoleProviderTest`, `ThemeProviderTest`, `LanguageProviderTest`, `StatusBarPolicyProviderTest`
- [ ] 1.17 Unit tests: `PresetValidatorTest` — valid ordering, invalid ordering, missing requires
- [ ] 1.18 Unit test: `ThemeRefExpansionTest` — ThemeRef → flat fields round-trip
- [ ] 1.19 Unit test: `PresetSchemaVersionTest` — v1 reader ignores `hintFlow`, v2 reader defaults to empty list

## 2. FirstLaunch Wiring (Phase 2)

- [ ] 2.1 Wire `FirstLaunchActivity.onCreate()` → `PresetBootstrap.bootstrap()` + `ReconcileEngine.run(RunMode.Wizard, sink)`
- [ ] 2.2 Create `InteractionSink` interface in domain — `suspend fun answer(componentId: ComponentId, response: UserResponse)`
- [ ] 2.3 Rewrite `WizardScreen` Composable to consume `StateFlow<ReconcileState>` + call `InteractionSink`
- [ ] 2.4 Create `WizardViewModel` wrapping `ReconcileEngine` + exposing `InteractionSink`
- [ ] 2.5 Unit test: `WizardViewModelTest` — wizard flow state transitions
- [ ] 2.6 Manual smoke: full wizard on Xiaomi Redmi Note 11 — compare UX with `verification-evidence/task-120-xiaomi-first-launch.png`

## 3. Settings Migration (Phase 3)

- [ ] 3.1 Migrate `PendingChecklistViewModel` from `ConfigKind` → `Preset.settingsMap[]`
- [ ] 3.2 Migrate Settings screens that read `ConfigKind` → read from `ProfileStore` + `Preset`
- [ ] 3.3 Migrate `PresetSelectionService` + `PresetSwitchService` from TASK-65 API → `com.launcher.preset.*`
- [ ] 3.4 Migrate `PresetReminderService` from TASK-65 imports → `com.launcher.preset.*`
- [ ] 3.5 Unit tests: `PendingChecklistViewModelTest` against new Preset API
- [ ] 3.6 Manual smoke: Settings edit round-trip on Xiaomi — change FontSize, verify persistence

## 4. BootCheck Migration (Phase 4)

- [ ] 4.1 Migrate `BootCheckReceiver` to use `PresetBootstrap` + `ReconcileEngine.run(RunMode.BootCheck)`
- [ ] 4.2 Migrate all `CheckHandler` / `ApplyHandler` pairs from `adapters/wizard/` → `Provider<T>` implementations in `com.launcher.preset.provider.*`
- [ ] 4.3 Delete `WizardCheckpointStore` — callers use `ProfileStore.setPreWizardSnapshot()`
- [ ] 4.4 Unit tests: `BootCheckProviderTest` — reapply critical components after reboot
- [ ] 4.5 Manual smoke: force-reboot on Xiaomi — verify BootCheck reapplies critical components

## 5. E2E Test Migration (Phase 5)

- [ ] 5.1 Rewrite `BootBenchmarkE2ETest` on `PresetBootstrap` + `ReconcileEngine` API
- [ ] 5.2 Rewrite `BootCriticalMissingE2ETest` on new API
- [ ] 5.3 Rewrite `FirstLaunchPickerE2ETest` on new API
- [ ] 5.4 Rewrite `XiaomiOemMatrixE2ETest` on new API
- [ ] 5.5 Regenerate `simple-launcher` golden JSON for new Preset schemaVersion 2
- [ ] 5.6 Run `./gradlew :app:connectedMockBackendDebugAndroidTest --tests "*E2E*"` on Xiaomi — all green

## 6. Legacy Deletion + Fitness Function (Phase 6)

- [ ] 6.1 Delete `core/commonMain/kotlin/com/launcher/api/wizard/` (~26 files)
- [ ] 6.2 Delete `core/commonMain/kotlin/com/launcher/ui/wizard/` (~10 files)
- [ ] 6.3 Delete `core/androidMain/kotlin/com/launcher/adapters/wizard/` (~13 files)
- [ ] 6.4 Delete `app/main/java/com/launcher/app/wizard/` (WizardActivity + NoopAdapters)
- [ ] 6.5 Delete `core/commonMain/kotlin/com/launcher/api/preset/` (TASK-65, ~4 files)
- [ ] 6.6 Delete `core/commonMain/kotlin/com/launcher/api/profile/` + `androidMain/adapters/profile/`
- [ ] 6.7 Delete `core/commonMain/kotlin/com/launcher/api/pools/` + `api/switchstrategy/`
- [ ] 6.8 Delete `core/androidMain/assets/wizard/` tree (tile-sets/*, system-settings/*)
- [ ] 6.9 Delete `Spec015Module.kt` + `Task65Module.kt` — merge bindings into `PresetModule`
- [ ] 6.10 Remove `uses-accessibility-service` declaration from `AndroidManifest.xml`
- [ ] 6.11 Add lint rule FF-011: `com.launcher.api.wizard` import → build failure
- [ ] 6.12 Verify `git grep "import com.launcher.api.wizard"` returns zero results in production code
- [ ] 6.13 Run `./gradlew :app:testMockBackendDebugUnitTest :core:testMockBackendDebugUnitTest` — all green
- [ ] 6.14 Run `./gradlew lint` — FF-011 reports zero violations
- [ ] 6.15 Final manual smoke on Xiaomi: fresh install → wizard → home → settings edit → force-reboot → BootCheck
