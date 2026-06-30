# Tasks: TASK-65 — Preset Composition Foundation v2

**Spec**: [`spec.md`](spec.md) | **Plan**: [`plan.md`](plan.md) | **Branch**: `task-65-profile-composition-foundation-v2`.

**Task ID prefix**: `T6NN` (TASK-65). Numbering matches phases (T6**0**N = Phase 0, T6**1**N = Phase 1, ..., T6**6**N = Phase 6).

**Legend**:
- `[P]` — parallel-safe with other `[P]` tasks in the same phase (no file conflicts).
- `[deferred-local-emulator]` — needs emulator session that AI may not have.
- `[deferred-physical-device]` — needs real device, owner runs manually.

---

## Phase 0 — Inventory + Setup

> Verifies existing code state and prepares ground. **All other phases depend on this.**

- [x] **T600** Inventory `core/src/commonMain/kotlin/com/launcher/api/ProfileModels.kt` (`ProfileSnapshot`, `EffectiveProfile`, `DegradationRecord`) — grep all consumers; decide: rename to `Resolved*` (if alive) or delete (if dead). Document decision in `plan.md` § R2 result. (Plan §2.1, Risk «existing ProfileSnapshot decision»)
       Acceptance: decision committed в plan.md; either grep returns 0 consumers (→ delete in T640) or 1+ consumers (→ rename in T640).

- [x] **T601** Confirm `androidx.datastore.preferences` (1.1.1) in `libs.versions.toml`. (Plan §5.1)
       Acceptance: `grep datastore c:/work/launcher/gradle/libs.versions.toml` shows `datastore = "1.1.1"` and `androidx-datastore-preferences` binding.

- [x] **T602** Confirm `kotlinx.serialization.json` available in `core/build.gradle.kts` commonMain. (Plan §5.1)
       Acceptance: `grep "kotlinx-serialization-json" c:/work/launcher/core/build.gradle.kts` returns the dep.

- [x] **T603** Confirm existing `ConfigSource.kt`, `WizardEngine.kt`, `CheckSpec.kt`, `ApplySpec.kt`, `BundledConfigSource.kt` are present and parsable. (Plan §2.1 existing-code reuse)
       Acceptance: all 5 files exist at paths from plan §2.1.

---

## Phase 1 — Foundation types (commonMain, pure-Kotlin)

> Pure data types. No Android, no DI, no behaviour. Should compile in commonMain unit tests.

- [x] **T610 [P]** Create `core/src/commonMain/kotlin/com/launcher/api/preset/PresetRef.kt` — `data class PresetRef(uid: String, version: Int)` с `init` validations (uid non-blank, no `::`, version ≥ 1) + `toCompositeKey()` + `parseCompositeKey()` companion. (FR-001, R3)
       Acceptance: `./gradlew :core:test --tests "*PresetRefTest"` (placeholder green after T660).

- [x] **T611 [P]** Create `core/src/commonMain/kotlin/com/launcher/api/preset/Preset.kt` — `@Serializable data class Preset(schemaVersion, uid, version, slug, label, description, configs, abstractProfile?, requiredModules, optionalModules, pickEnabled)` + `val ref: PresetRef` extension + `const val PRESET_SCHEMA_VERSION = 1`. (FR-001)
       Acceptance: file compiles in commonMain.

- [x] **T612 [P]** Create `core/src/commonMain/kotlin/com/launcher/api/preset/Config.kt` — `@Serializable data class Config(id, poolId, poolVersion, entryId, title, description, check: CheckSpec, apply: ApplySpec, criticality: Criticality, defaultValue?, hideInWizard, showInSettings)` + `enum class Criticality { Required, Optional }`. (FR-001, Clarification #8)
       Acceptance: file compiles.

- [x] **T613 [P]** Create `core/src/commonMain/kotlin/com/launcher/api/preset/AbstractProfile.kt` — `@Serializable data class AbstractProfile(layout: Layout, bindings: List<Binding>)`. (FR-001, Clarification #8)
       Acceptance: file compiles (requires T620, T621 — see Phase 2 reorder note).

- [x] **T614 [P]** Add `ConfigKind.Preset` variant to existing `ConfigSource.kt`. (FR-004, Article VII §10 evolution)
       Acceptance: enum has 6 values; existing tests still green.

- [x] **T615 [P]** Add `CheckSpec.UIFont(minScale: Float)` variant to existing `core/src/commonMain/kotlin/com/launcher/api/wizard/data/CheckSpec.kt` (sealed extension per Article VII §16). (FR-024)
       Acceptance: file compiles; existing `CheckSpec` tests green.

- [x] **T616 [P]** Create `core/src/commonMain/kotlin/com/launcher/api/profile/Layout.kt` — `Layout`, `Screen`, `Grid`, `Slot` (`@Serializable data class` each) + `Layout.empty()` companion. `Slot.kind: String? = null` (hook per Clarification #11). (FR-017)
       Acceptance: file compiles.

- [x] **T617 [P]** Create `core/src/commonMain/kotlin/com/launcher/api/profile/Binding.kt` — `@Serializable data class Binding(slotPosition, targetPackage?, contactRef?, url?, intentExtras)`. (FR-017, Clarification #12 hook)
       Acceptance: file compiles.

- [x] **T618 [P]** Create `core/src/commonMain/kotlin/com/launcher/api/profile/AppliedState.kt` — `@Serializable sealed class AppliedState` with `NotApplied`, `Applied`, `WithValue(value: String)`, `Indeterminate` variants. (FR-017, Article VII §15 graceful)
       Acceptance: file compiles.

- [x] **T619 [P]** Create `core/src/commonMain/kotlin/com/launcher/api/profile/SettingEntry.kt` — `@Serializable data class SettingEntry(config: Config, state: AppliedState = NotApplied)`. (FR-017)
       Acceptance: file compiles.

- [x] **T61A [P]** Create `core/src/commonMain/kotlin/com/launcher/api/profile/ProfileData.kt` — `@Serializable data class ProfileData(layout, bindings, settings, unassigned)`. (FR-017, Clarification #9, Slot.kind/unassigned hooks)
       Acceptance: file compiles.

- [x] **T61B [P]** Create `core/src/commonMain/kotlin/com/launcher/api/profile/ProfileStoreState.kt` — `@Serializable data class ProfileStoreState(schemaVersion, activePresetRef?, profiles: Map<String, ProfileData>)` + `const val PROFILE_STORE_SCHEMA_VERSION = 1`. (FR-018, R3)
       Acceptance: file compiles; Map key documented as composite `"uid::version"`.

- [x] **T61C** Create `core/src/commonMain/kotlin/com/launcher/api/profile/ProfileStore.kt` — `interface ProfileStore { load(); save(); getActive(); putProfile(); setActive() }`. (FR-018)
       Acceptance: file compiles.
       Depends: T61B.

---

## Phase 2 — Ports + default strategies (commonMain)

- [x] **T620 [P]** Create `core/src/commonMain/kotlin/com/launcher/api/pools/Pool.kt` — `@Serializable data class Pool(id, schemaVersion, entries: List<PoolEntry>)` + `data class PoolEntry(id, title, description, check, apply, criticality, defaultValue?, deprecated)`. (FR-005, contracts/pool-naming.md)
       Acceptance: file compiles.

- [x] **T621 [P]** Create `core/src/commonMain/kotlin/com/launcher/api/pools/PoolSource.kt` — `interface PoolSource { load(poolId); version(poolId); listEntries(poolId) }`. (FR-005)
       Acceptance: file compiles.
       Depends: T620.

- [x] **T622** Create `core/src/commonMain/kotlin/com/launcher/api/switchstrategy/ProfileSwitchStrategy.kt` — `interface ProfileSwitchStrategy { migrate(from: ProfileData?, toPreset: Preset): ProfileData }`. (FR-019)
       Acceptance: file compiles.
       Depends: T611, T61A.

- [x] **T623** Create `core/src/commonMain/kotlin/com/launcher/api/switchstrategy/CopyOnActivateStrategy.kt` — default adapter: `from` ignored; returns `ProfileData(layout = toPreset.abstractProfile?.layout ?: Layout.empty(), bindings = toPreset.abstractProfile?.bindings ?: [], settings = toPreset.configs.map { SettingEntry(it, NotApplied) })`. (FR-019)
       Acceptance: unit test (T660 — Phase 6) covers happy path + null abstractProfile case.
       Depends: T622, T611, T612, T61A, T619.

---

## Phase 3 — PoolSource adapters (androidMain)

- [x] **T630** Create `core/src/androidMain/kotlin/com/launcher/adapters/pools/HardcodedPoolSource.kt` — implements `PoolSource` via Kotlin `const val POOL_*` constants for `system-settings` and `ui-customization` pools. Initial entries: `android.role.home`, `android.permission.POST_NOTIFICATIONS`, `ui.font.large` (uses CheckSpec.UIFont from T615). (FR-006, contracts/pool-naming.md)
       Acceptance: instance answers `load("system-settings")` and `load("ui-customization")` без throw; `listEntries` returns ≥3 total entries.
       Depends: T620, T621, T615.

- [x] **T631 [P]** Create `core/src/androidMain/kotlin/com/launcher/adapters/pools/JsonAssetPoolSource.kt` — **scaffold**: implements `PoolSource` interface; methods throw `NotImplementedError("scaffold — see TASK-65 plan R3")`. Inline TODO. (FR-007)
       Acceptance: file compiles; integration не нужна (scaffold).
       Depends: T621.

- [x] **T632** Set up DI binding switching `HardcodedPoolSource` ↔ `JsonAssetPoolSource` via Gradle property `-Ppools.json=true` or build flavor. Default: Hardcoded. (FR-008)
       Acceptance: `./gradlew :app:assembleDebug` uses Hardcoded by default; `./gradlew :app:assembleDebug -Ppools.json=true` switches binding (build succeeds; runtime would throw NotImplementedError from scaffold — expected).
       Depends: T630, T631.

---

## Phase 4 — Persistence + Migration

- [x] **T640** Apply T600 decision: either `git mv ProfileModels.kt → ResolvedPresetModels.kt` + rename types `ProfileSnapshot → ResolvedPresetSnapshot`, `EffectiveProfile → EffectivePreset`, etc., **OR** `git rm` ProfileModels.kt + remove consumers. Update all import sites. (Plan §2.1, R2)
       Acceptance: build green; `git grep ProfileSnapshot` returns 0 OR only renamed references.
       Depends: T600.

- [x] **T641** Extend `core/src/commonMain/kotlin/com/launcher/api/wizard/ConfigSource.kt` to support `ConfigKind.Preset`: `load(Preset, presetId)` returns `Preset` object. Update `ConfigSummary` to include `slug` field for preset summaries. (FR-009)
       Acceptance: existing tests green; new test in T660 covers Preset load.
       Depends: T611, T614.

- [x] **T642** Extend `core/src/androidMain/kotlin/com/launcher/adapters/wizard/BundledConfigSource.kt` (existing) to read `assets/presets/<slug>.preset.json` files. (FR-009)
       Acceptance: instance answers `load(Preset, "simple-launcher")` returns parsed `Preset` (test T660 verifies).
       Depends: T641.

- [x] **T643** Create `core/src/commonMain/kotlin/com/launcher/api/wizard/data/ConfigParser.kt` extension — `migrateLegacyWizardManifest(json: JsonObject): JsonObject` scoped function (removes `appFamilyId` from body, bumps `schemaVersion: 1 → 2`). Per R6. (FR-002)
       Acceptance: unit test T665 covers v1 → v2 transform.

- [x] **T644** Update existing `core/src/commonMain/kotlin/com/launcher/api/wizard/data/WizardManifest.kt` parser to: (a) read `schemaVersion` first; (b) if `version == 1` → invoke `migrateLegacyWizardManifest`; (c) if `version > 2` → return `IncompatibleVersion`; (d) deserialize after migration. (FR-002, R6)
       Acceptance: existing TASK-7 tests still green (manifest now v2); legacy v1 fixture parses to v2 structure via migrator.
       Depends: T643.

- [x] **T645** Edit `core/src/androidMain/assets/wizard/wizard-manifests/simple-launcher.json` — remove `body.appFamilyId` field, bump `"schemaVersion": 1 → 2`. (FR-002)
       Acceptance: `grep appFamilyId c:/work/launcher/core/src/androidMain/assets/wizard/wizard-manifests/simple-launcher.json` returns 0.
       Depends: T644.

- [x] **T646** Create fixture `core/src/androidTest/assets/wizard-manifests/legacy-with-app-family-id.json` — pre-TASK-65 simple-launcher.json (with `appFamilyId`, `schemaVersion: 1`). (R6, plan §6.1, CHK011 wire-format)
       Acceptance: file exists; parsable as legacy JSON.

- [x] **T647** Create `core/src/androidMain/kotlin/com/launcher/adapters/profile/PreferencesProfileStore.kt` — implements `ProfileStore` port using `androidx.datastore.preferences` single key `profile.store.json` containing `Json.encodeToString(ProfileStoreState)`. Composite Map keys per R3. Includes `appFamilyId` legacy migration (FR-015). (FR-018, R3, R6 idempotent)
       Acceptance: test T661 covers roundtrip + legacy migration.
       Depends: T61B, T61C.

- [x] **T648** Update DI module wiring `ProfileStore` → `PreferencesProfileStore` in androidMain. (Plan §2.1)
       Acceptance: `./gradlew :app:assembleDebug` succeeds.
       Depends: T647.

---

## Phase 5 — UI + Services (androidMain)

- [ ] **T650** Create bundled preset `core/src/androidMain/assets/presets/simple-launcher.preset.json` — schemaVersion=1, uid=`com.launcher.preset.simple-launcher`, version=1, slug=`simple-launcher`, configs reference: `android.role.home` (Required), `android.permission.POST_NOTIFICATIONS` (Optional); abstractProfile: 1-screen 2x3 grid, bottom toolbar [settings, sos], no bindings. (FR-001)
       Acceptance: parses via T642; T669 fitness build-time validation passes.
       Depends: T642.

- [ ] **T651** Create bundled preset `core/src/androidMain/assets/presets/launcher.preset.json` — classic launcher variant: uid=`com.launcher.preset.launcher`, similar configs to simple-launcher but with larger grid (3x4), no bottom toolbar (default Android-like). (FR-001)
       Acceptance: parses; appears in picker (test T67E).
       Depends: T642.

- [ ] **T652** Create bundled preset `core/src/androidMain/assets/presets/workspace.preset.json` — admin/work variant: uid=`com.launcher.preset.workspace`, configs reference: `android.role.home` (Optional, NOT Required), POST_NOTIFICATIONS (Required); abstractProfile: 3-screen 3x3 grid с placeholder bindings (`com.google.android.youtube`, `com.android.chrome`). (FR-001)
       Acceptance: parses; appears in picker.
       Depends: T642.

- [ ] **T653 [P]** Create test fixture `core/src/androidTest/assets/presets/test-preset.json` — schemaVersion=1, uid=`com.launcher.preset.test`, version=1, configs: [`ui.font.large` (Required, uses CheckSpec.UIFont)]; abstractProfile: minimal 1-slot layout. Used by `EngineGenericityFitnessTest` (T66D). (FR-023, FR-026)
       Acceptance: file exists; parses.

- [ ] **T654** Create `core/src/androidMain/kotlin/com/launcher/adapters/wizard/UIFontChecker.kt` — handler for `CheckSpec.UIFont`. Reads `context.resources.configuration.fontScale`, returns `Applied` if ≥ `minScale`, else `NotApplied`. (FR-024)
       Acceptance: test T66D covers Applied + NotApplied transitions.
       Depends: T615.

- [ ] **T655** Register `UIFontChecker` in DI as handler for `CheckSpec.UIFont` kind. (FR-024)
       Acceptance: instance available via DI; `WizardEngine.computePending` with CheckSpec.UIFont entry dispatches to UIFontChecker (test T66D verifies).
       Depends: T654.

- [ ] **T656** Create `core/src/androidMain/kotlin/com/launcher/adapters/preset/PresetSelectionService.kt` — `class PresetSelectionService { suspend fun beginSetup(presetRef: PresetRef) }`: loads preset via ConfigSource, applies CopyOnActivateStrategy, persists ProfileData to ProfileStore, sets activePresetRef. (FR-009, US-1)
       Acceptance: test (in T67E E2E) covers fresh install → setup flow.
       Depends: T623, T642, T648.

- [ ] **T657** Create `core/src/androidMain/kotlin/com/launcher/adapters/preset/PresetSwitchService.kt` — `class PresetSwitchService { suspend fun switchTo(newPresetRef: PresetRef): SwitchOutcome }`. Snapshot current → load new preset → if `profiles[newRef]` exists restore, else CopyOnActivateStrategy → `WizardEngine.computePending` → if empty commit, else launch mini-wizard → re-check → commit OR keep old. (FR-014, US-2)
       Acceptance: test (T67F) covers switch + restore from history.
       Depends: T623, T642, T648.

- [ ] **T658** Create `core/src/androidMain/kotlin/com/launcher/adapters/preset/PresetReminderService.kt` — `class PresetReminderService { suspend fun computeCriticalMissing(profile: ProfileData): List<SettingEntry>; suspend fun computeAllMissing(profile: ProfileData): List<SettingEntry> }`. Internally calls `WizardEngine.computePending(profile.settings)` + filters by `Criticality`. Used by PresetBootRouter (critical) и SettingsActivity onResume (all). (FR-016, FR-030, FR-031)
       Acceptance: tests in T66B + T67H verify critical vs all classification.
       Depends: T648.

- [ ] **T659** Create `core/src/androidMain/kotlin/com/launcher/ui/PresetBootRouter.kt` — Activity router. Reads activePresetRef from ProfileStore; if null AND `wizardDone` legacy flag absent → start FirstLaunchActivity (picker); if null AND legacy flag set → migration via PreferencesProfileStore (FR-015); if active set → load Preset, computeCriticalMissing, start HomeActivity (with BannerData if critical non-empty). (FR-029, FR-030, FR-031, US-3, US-7)
       Acceptance: test T67G covers boot path + classification + banner pass-through.
       Depends: T648, T642, T658.

- [ ] **T65A** Create `core/src/androidMain/kotlin/com/launcher/ui/PresetPickerScreen.kt` — Compose: reads `ConfigSource.list(ConfigKind.Preset)`, renders Material3 cards (label + description i18n), tap callback with `PresetRef`. Tap target ≥ 56dp, contrast ≥ 4.5:1, TalkBack contentDescription per card (Gate 5 accessibility partial). Fallback safety net if list empty → log warning + use hardcoded `simple-launcher`. (FR-012, US-1)
       Acceptance: smoke screenshot via android-emulator skill (T67E).
       Depends: T642.

- [ ] **T65B** Create `core/src/androidMain/kotlin/com/launcher/ui/HomeBanner.kt` — Compose: banner card на top of HomeActivity for critical missing requirements. Contains: title (i18n `home_banner_critical_missing_title`), CTA button «Настроить» (i18n `home_banner_critical_missing_cta`), dismiss button «Позже». Tap → callback to launch mini-wizard with all critical missing. Tap target ≥ 56dp. Dismiss = hide until next boot OR state change (in ViewModel `lifecycleScope`, not persisted). (FR-030, R4, US-7, Gate 5 partial)
       Acceptance: smoke screenshot via android-emulator skill (T67I).
       Depends: T658.

- [ ] **T65C** Update existing `app/src/main/kotlin/com/launcher/app/FirstLaunchActivity.kt` to route through `PresetBootRouter`. If picker needed → host `PresetPickerScreen`; on pick → `PresetSelectionService.beginSetup(ref)` → launch WizardActivity. (FR-011)
       Acceptance: instrumentation test T67E (FirstLaunchPicker) green.
       Depends: T659, T65A, T656.

- [ ] **T65D** Update existing `SettingsActivity` (search exact path in app/ module) to: (a) add «Сменить preset» entry → launches PresetSwitchActivity (reuses PresetPickerScreen with current marked); (b) in `onResume()` → call `PresetReminderService.computeAllMissing(currentProfile)` → render banner cards for each missing entry; tap banner → mini-wizard for that one entry. (FR-013, FR-016)
       Acceptance: instrumentation test T67H (SettingsReminders) green.
       Depends: T657, T658, T65A.

- [ ] **T65E** Add i18n string keys to `core/src/commonMain/composeResources/values/strings_wizard.xml` (English base): `preset_simple_launcher_label`, `preset_simple_launcher_description`, `preset_launcher_label`, `preset_launcher_description`, `preset_workspace_label`, `preset_workspace_description`, `settings_role_home_title`, `settings_role_home_description`, `settings_post_notifications_title`, `settings_post_notifications_description`, `settings_font_large_title`, `settings_font_large_description`, `home_banner_critical_missing_title`, `home_banner_critical_missing_cta`, `home_banner_dismiss_later`, `settings_change_preset_label`. (FR-001, FR-016, FR-030)
       Acceptance: 16 new keys present; RU manual translation in `values-ru/strings_wizard.xml` (per memory `feedback_language_russian` — RU manual, not auto).

- [ ] **T65F** Run `procedure-translate-spec-strings` skill to auto-translate the 16 new keys into 9 locales (ES/ZH/AR/HI/PT/DE/FR/JA/KK-Latn). RU NOT touched per FR-031a (spec 015). (T65E artifacts)
       Acceptance: each `values-<locale>/strings_wizard.xml` contains the 16 keys.
       Depends: T65E.

---

## Phase 6 — Fitness, contracts, integration tests

### 6a — Detekt setup + custom rules

- [ ] **T660** Add `detekt = "1.23.7"` + `detekt-api`, `detekt-test` bindings to `gradle/libs.versions.toml`. Apply `io.gitlab.arturbosch.detekt` plugin in root `build.gradle.kts`. (R5)
       Acceptance: `./gradlew detekt` runs (no errors из-за нет custom rules yet).

- [ ] **T661** Create new Gradle module `lint-rules/` with `build.gradle.kts` depending on `libs.detekt.api`. Register in `settings.gradle.kts`. (R5)
       Acceptance: `./gradlew :lint-rules:tasks` lists test task.
       Depends: T660.

- [ ] **T662** Create `lint-rules/src/main/kotlin/com/launcher/lint/PresetIdBranchingDetector.kt` — Detekt `Rule` extension scanning for `if (presetId == "...")`, `when (presetId)`, `when (appFamilyId)`, `if (appFamilyId == "...")`. Whitelist packages: `com.launcher.core.preset.*`, `com.launcher.core.preset.test.*`. (FR-020)
       Acceptance: unit test T66E (positive/negative cases) green.
       Depends: T661.

- [ ] **T663** Create `lint-rules/src/main/kotlin/com/launcher/lint/ExtractionReadinessDetector.kt` — Detekt `Rule` extension scanning imports inside packages `com.launcher.core.preset.*`, `com.launcher.core.wizard.*`, `com.launcher.core.pools.*` — flag imports matching `com.launcher.app.tiles.*`, `com.launcher.app.home.*`, `com.launcher.app.contacts.*`. (FR-021)
       Acceptance: unit test T66F green.
       Depends: T661.

- [ ] **T664** Create Gradle task alias `detektFoundation` in root `build.gradle.kts` running both rules on `core/` + `app/`. (FR-022)
       Acceptance: `./gradlew detektFoundation` runs; failure exit code on violations.
       Depends: T662, T663.

- [ ] **T665** Create `scripts/pre-commit-detekt` shell script running `./gradlew detektFoundation` on staged Kotlin files; doc in `quickstart.md` covers manual install via `cp scripts/pre-commit-detekt .git/hooks/pre-commit`. (FR-022)
       Acceptance: script exists; manually testable per quickstart §1.
       Depends: T664.

### 6b — Wire format contract tests

- [ ] **T666 [P]** Create `core/src/test/kotlin/com/launcher/api/preset/PresetWireFormatRoundtripTest.kt` — JVM unit. Write `Preset` (with PresetRef = `com.launcher.preset.test::2`, configs[2], abstractProfile) → JSON via `Json.encodeToString` → read via `Json.decodeFromString<Preset>` → `assertEquals(original, parsed)`. Covers PresetRef serialization. (FR-001, CHK010 wire-format)
       Acceptance: test green.
       Depends: T611, T610.

- [ ] **T667 [P]** Create `core/src/test/kotlin/com/launcher/api/preset/PresetRefValidationTest.kt` — JVM unit. Verifies `PresetRef.init` rejects: blank uid, uid containing `::`, version < 1. Covers `parseCompositeKey` round-trip + invalid input throws. (R3, FR-001)
       Acceptance: test green.
       Depends: T610.

- [ ] **T668 [P]** Create `core/src/test/kotlin/com/launcher/api/profile/ProfileStoreSerializationTest.kt` — JVM unit. Write `ProfileStoreState` with ≥2 entries in `profiles` Map (different PresetRef) + all 4 AppliedState variants in settings → JSON → read → assertEquals. Verifies composite key format `"uid::version"` per R3. (FR-018, CHK010)
       Acceptance: test green.
       Depends: T61A, T61B, T618.

- [ ] **T669 [P]** Create `core/src/test/kotlin/com/launcher/api/preset/BundledPresetsParseTest.kt` — build-time check: every `*.preset.json` in `core/src/androidMain/assets/presets/` parses successfully + has unique PresetRef + i18n keys present (validates against `strings_wizard.xml`). (FR-004, plan §6.4)
       Acceptance: test green; future-proof against malformed preset additions.
       Depends: T650, T651, T652, T65E.

- [ ] **T66A [P]** Create `core/src/test/kotlin/com/launcher/api/wizard/data/WizardManifestBackwardCompatTest.kt` — read fixture `legacy-with-app-family-id.json` (T646) → assert: migrator removed `appFamilyId`, bumped to `schemaVersion=2`, rest of fields intact. (FR-002, R6, CHK011 wire-format)
       Acceptance: test green.
       Depends: T643, T646.

- [ ] **T66B [P]** Create `core/src/test/kotlin/com/launcher/api/wizard/SettingsCallbackThrowsTest.kt` — JVM unit с `FakeSystemSettingPort` whose check throws. `WizardEngine.computePending` should treat entry as `Indeterminate` (graceful per Article VII §15), not crash. (Edge case CHK011, plan §6.5)
       Acceptance: test green; no exception bubbles.
       Depends: T618.

- [ ] **T66C** Create `core/src/test/kotlin/com/launcher/api/pools/PoolSourceRoundtripTest.kt` — JVM unit. `HardcodedPoolSource.listEntries("system-settings")` equality vs `JsonAssetPoolSource.listEntries("system-settings")`. JsonAssetPoolSource throws `NotImplementedError` (scaffold) — test annotated `@Ignore("scaffold, see plan R3 / quickstart")` with TODO. (FR-027)
       Acceptance: test marked `@Ignore`; class compiles.
       Depends: T630, T631.

- [ ] **T66D** Create `core/src/test/kotlin/com/launcher/api/wizard/EngineGenericityFitnessTest.kt` — JVM unit. Load `test-preset.json` (T653) via FakeConfigSource → `WizardEngine.computePending(settings)` → verify dispatched handler == `UIFontChecker` (not Android-permission); set fontScale=1.5 via `FakeUserPreferencesStore` → re-check returns missing=[]. (FR-026, US-8)
       Acceptance: test green.
       Depends: T615, T654, T653.

### 6c — Detekt rule tests

- [ ] **T66E [P]** Create `lint-rules/src/test/kotlin/com/launcher/lint/PresetIdBranchingDetectorTest.kt` — Detekt test framework. Positive cases (issue expected): `if (presetId == "x")` в `com/launcher/app/home/`, `when (appFamilyId)` в `com/launcher/core/wizard/`. Negative cases (no issue): same patterns в `com/launcher/core/preset/` (whitelisted). (FR-020)
       Acceptance: 4 cases all pass.
       Depends: T662.

- [ ] **T66F [P]** Create `lint-rules/src/test/kotlin/com/launcher/lint/ExtractionReadinessDetectorTest.kt` — Positive: `import com.launcher.app.tiles.Tile` в `core/preset/`. Negative: same в `app/`. (FR-021)
       Acceptance: 2 cases pass.
       Depends: T663.

### 6d — Regression + Migration tests

- [ ] **T66G** Create `core/src/test/kotlin/com/launcher/api/preset/SimpleLauncherCompositionRegressionTest.kt` — snapshot test: wizard derived from `simple-launcher.preset.json` (via composition engine) equals golden file `core/src/test/resources/fixtures/wizard-simple-launcher-golden.json`. (FR-025, SC-010)
       Acceptance: first run creates golden file; subsequent runs assert equality.
       Depends: T650, T642, T644.

- [ ] **T66H** Create `core/src/test/kotlin/com/launcher/adapters/profile/PreferencesProfileStoreTest.kt` — covers (a) roundtrip save → load, (b) legacy migration trigger (`wizard_done=true && applied_preset_id=null` → `activePresetRef=PresetRef("com.launcher.preset.simple-launcher", 1)` + ProfileData inserted), (c) idempotent: 2nd migration call no-op. (FR-015, FR-018, US-3)
       Acceptance: 3 sub-tests green.
       Depends: T647.

### 6e — Integration E2E tests (instrumentation, emulator pixel_5_api_34)

> All E2E tests marked `[deferred-local-emulator]` per CLAUDE.md hybrid AC model — AI session may not have AVD running.

- [ ] **T67E** [deferred-local-emulator] `app/src/androidTest/kotlin/com/launcher/app/FirstLaunchPickerE2ETest.kt` — Fresh install (clear data) → launch → assert `PresetPickerScreen` shown with **3 cards** (simple-launcher, launcher, workspace) → tap simple-launcher → assert `WizardActivity` started → complete wizard → assert `HomeActivity` rendered. (US-1, SC-001)
       Acceptance: test green via android-emulator skill smoke.
       Depends: T65C, T650, T651, T652.

- [ ] **T67F** [deferred-local-emulator] `app/src/androidTest/.../PresetSwitchE2ETest.kt` — wizard done simple-launcher → manually persist 2 bindings via ProfileStore → Settings → Сменить preset → tap test-preset (bundled in androidTest assets) → assert mini-wizard shows only `ui.font.large` step → complete → assert `activePresetRef = PresetRef("com.launcher.preset.test", 1)` → switch back to simple-launcher → assert prior 2 bindings restored. (US-2, SC-002)
       Acceptance: test green.
       Depends: T65D, T657, T650, T653.

- [ ] **T67G** [deferred-local-emulator] `app/src/androidTest/.../MigrationE2ETest.kt` — manually seed DataStore with legacy state (`wizard_done=true`, `applied_preset_id=null`) → cold boot → assert picker NOT shown → assert HomeActivity rendered → assert `activePresetRef = PresetRef("com.launcher.preset.simple-launcher", 1)`. (US-3, SC-003, FR-015)
       Acceptance: test green.
       Depends: T659, T647.

- [ ] **T67H** [deferred-local-emulator] `app/src/androidTest/.../SettingsRemindersE2ETest.kt` — simple-launcher active → programmatically revoke ROLE_HOME → open Settings → assert banner «Не настроен HOME launcher» shown → tap → assert mini-WizardActivity with one step → complete → assert banner disappears on next onResume. (US-4, SC-008, SC-011)
       Acceptance: test green.
       Depends: T65D, T658.

- [ ] **T67I** [deferred-local-emulator] `app/src/androidTest/.../BootCriticalMissingBannerE2ETest.kt` — simple-launcher active → revoke ROLE_HOME → cold boot → assert HomeActivity rendered with `HomeBanner` visible → tap banner → assert mini-WizardActivity opened with **all critical missing** entries (not just one). Dismiss banner → assert banner hidden until next state change. (FR-030, R4, US-7 revised)
       Acceptance: test green.
       Depends: T659, T65B.

- [ ] **T67J** [deferred-local-emulator] `app/src/androidTest/.../BootBenchmarkTest.kt` — cold boot (`adb shell am force-stop` + start) → measure time from `Sys.launch` to `HomeActivity.onResume`. Run 10 iterations. Assert P95 ≤ 1500ms. (SC-007, R4)
       Acceptance: test green; if fails → switch to async path per R4 fallback.
       Depends: T659.

### 6f — Physical device verification (manual, deferred to owner)

- [ ] **T67K** [deferred-physical-device] Manual verification on Xiaomi 11T: ROLE_HOME `ApplySpec.SettingsDeepLink` opens correct Settings screen OR fallback toast displayed with text instruction. (OEM matrix per spec; SC-008)
       Acceptance: owner runs manually, confirms in PR description.

- [ ] **T67L** [deferred-physical-device] Future: Samsung One UI verification when device available. (OEM matrix)
       Acceptance: owner runs manually when device acquired.

- [ ] **T67M** [deferred-physical-device] Future: Huawei EMUI (no GMS) verification when device available. (OEM matrix)
       Acceptance: owner runs manually when device acquired.

---

## Summary

**Total**: 50 tasks across 7 phases (Phase 0: 4, Phase 1: 13, Phase 2: 4, Phase 3: 3, Phase 4: 9, Phase 5: 14 incl. i18n, Phase 6: 21 incl. 5 E2E + 3 physical-device deferred).

**Parallel-safe `[P]` count**: 16 (mostly Phase 1 type creation, Phase 6 unit tests).

**Deferred markers**:
- `[deferred-local-emulator]`: 6 tasks (T67E, T67F, T67G, T67H, T67I, T67J — all E2E instrumentation).
- `[deferred-physical-device]`: 3 tasks (T67K, T67L, T67M).

**Phase order strict** (within-phase parallelism allowed for `[P]`). Phase 0 must complete before Phase 1. Phase 6c-e depend on Phase 1-5 completion.

**Coverage** (cross-artifact trace next step):
- All 31 FRs traced to ≥1 task.
- All 9 USs covered (US-1: T67E; US-2: T67F; US-3: T67G + T66H; US-4: T67H; US-5: T66E; US-6: T66F; US-7: T67I + T67J; US-8: T66D; US-9: T66C).
- All 12 SC measurable through tests.

---

## Plain Russian summary (для не-разработчика владельца)

**Этот файл — список конкретных задач разработчика** для реализации TASK-65. 50 задач, разбитых на 7 этапов (phases).

**Что в каждом phase**:
- **Phase 0 (4 задачи)** — Inventory: проверить что уже есть в коде, решить судьбу старого `ProfileSnapshot` (переименовать или удалить).
- **Phase 1 (13 задач)** — Чистые типы данных в commonMain: PresetRef, Preset, Config, AbstractProfile, ProfileData и т.д. Просто структуры без поведения, компилируются без Android.
- **Phase 2 (4 задачи)** — Порты: интерфейсы PoolSource, ProfileSwitchStrategy + одна default реализация (CopyOnActivateStrategy).
- **Phase 3 (3 задачи)** — Адаптеры для пулов: HardcodedPoolSource (живой, primary) + JsonAssetPoolSource (scaffold с TODO).
- **Phase 4 (9 задач)** — Persistence: PreferencesProfileStore (хранение в DataStore), migration writer для удаления appFamilyId, обновление существующего ConfigSource для поддержки Preset, fixture для backward-compat теста.
- **Phase 5 (14 задач)** — UI и services: 3 bundled preset'а (simple-launcher, launcher, workspace) + 1 test-preset, PresetPickerScreen Compose, HomeBanner для critical missing на boot, PresetBootRouter, PresetSwitchService, PresetReminderService, UIFontChecker, обновление существующих FirstLaunchActivity и SettingsActivity, 16 i18n keys + автоперевод на 9 локалей.
- **Phase 6 (21 задача)** — Тесты и Detekt: настройка Detekt в новом `lint-rules/` модуле, 2 custom Detekt rule + их тесты, contract tests (PresetWireFormat, ProfileStore, BundledPresetsParse, WizardManifestBackwardCompat, SettingsCallbackThrows, PoolSourceRoundtrip), regression test через golden snapshot, 6 instrumentation E2E тестов (FirstLaunchPicker, PresetSwitch, Migration, SettingsReminders, BootCriticalMissingBanner, BootBenchmark), 3 deferred задачи для проверки на Xiaomi/Samsung/Huawei.

**Что значит `[P]`** — задача может выполняться параллельно с другими `[P]` задачами в том же phase (нет конфликтов файлов). 16 из 50 задач параллельные.

**Что значит `[deferred-local-emulator]`** — задача требует Android эмулятора с конкретным AVD; AI session может не иметь рабочий эмулятор. Останется `[ ]` в backlog AC, переход task'а в Verification статус когда merge'нется.

**Что значит `[deferred-physical-device]`** — задача требует реального телефона (Xiaomi 11T для проверки MIUI deep-link и т.п.). Владелец прогоняет вручную.

**Главное обещание plan'а** (повтор для контекста): после TASK-65 любой новый preset (clinic-patient, self-care, и т.д.) добавляется **только как JSON-файл в assets**, без единой строки Kotlin кода. Phase 5 задачи T650/T651/T652 — это первая демонстрация (3 bundled preset'а без specific code under hood).

**Следующий шаг**: `/speckit.analyze` — финальный аудит cross-artifact (spec ↔ plan ↔ tasks ↔ contracts ↔ checklists). Затем `/speckit.implement` — собственно код.
