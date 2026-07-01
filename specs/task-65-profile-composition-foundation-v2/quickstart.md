# Quickstart: TASK-65 — Developer Onboarding

Для нового разработчика, который хочет работать в области preset composition.

---

## 1. Setup

### Prerequisites (project-wide, не TASK-65-specific)

- JDK 21 installed (`./gradlew --version` shows Gradle JVM 21).
- Android Studio Hedgehog+ OR command-line `./gradlew`.
- Android emulator preset `pixel_5_api_34` via skill `android-emulator`.

### TASK-65-specific setup

#### Detekt installation (NEW для TASK-65)

Detekt — static analyzer для Kotlin, используется для custom fitness rules (`PresetIdBranchingDetector`, `ExtractionReadinessDetector`).

```bash
# 1. Verify Detekt available in libs.versions.toml (will be added in Phase 6):
grep -n detekt c:/work/launcher/gradle/libs.versions.toml
# Expected:
# detekt = "1.23.7"
# detekt-api = { group = "io.gitlab.arturbosch.detekt", name = "detekt-api", version.ref = "detekt" }
# detekt-test = { group = "io.gitlab.arturbosch.detekt", name = "detekt-test", version.ref = "detekt" }

# 2. Verify lint-rules module registered:
grep "lint-rules" c:/work/launcher/settings.gradle.kts
# Expected: include(":lint-rules")

# 3. Verify root build.gradle.kts applies Detekt plugin:
grep "io.gitlab.arturbosch.detekt" c:/work/launcher/build.gradle.kts
# Expected: id("io.gitlab.arturbosch.detekt") version libs.versions.detekt.get()
```

If any missing — Phase 6 implementation pending.

#### Pre-commit hook

```bash
# 1. Copy hook script:
cp c:/work/launcher/scripts/pre-commit-detekt c:/work/launcher/.git/hooks/pre-commit
chmod +x c:/work/launcher/.git/hooks/pre-commit

# 2. Test by attempting a violating commit:
echo 'fun bad() { if (presetId == "simple-launcher") {} }' > c:/work/launcher/app/src/main/kotlin/Test.kt
git -C c:/work/launcher add app/src/main/kotlin/Test.kt
git -C c:/work/launcher commit -m "test"
# Expected: commit rejected by PresetIdBranchingDetector.
```

(`scripts/pre-commit-detekt` script is created in Phase 6 implementation.)

---

## 2. Build & test commands

```bash
# JVM unit tests for preset composition logic:
./gradlew :core:test --tests "*PresetComposition*"
./gradlew :core:test --tests "*PresetWireFormatRoundtripTest"
./gradlew :core:test --tests "*ProfileStoreSerializationTest"
./gradlew :core:test --tests "*PoolSourceRoundtripTest"
./gradlew :core:test --tests "*EngineGenericityFitnessTest"
./gradlew :core:test --tests "*SimpleLauncherCompositionRegressionTest"
./gradlew :core:test --tests "*WizardManifestBackwardCompatTest"
./gradlew :core:test --tests "*SettingsCallbackThrowsTest"

# Detekt fitness rules:
./gradlew :lint-rules:test
./gradlew detektFoundation         # alias = detekt on core/, app/ with custom rules

# Instrumentation tests (emulator pixel_5_api_34):
./gradlew :app:connectedDebugAndroidTest -PandroidTest.filter="FirstLaunchPickerE2ETest"
./gradlew :app:connectedDebugAndroidTest -PandroidTest.filter="PresetSwitchE2ETest"
./gradlew :app:connectedDebugAndroidTest -PandroidTest.filter="MigrationE2ETest"
./gradlew :app:connectedDebugAndroidTest -PandroidTest.filter="SettingsRemindersE2ETest"
./gradlew :app:connectedDebugAndroidTest -PandroidTest.filter="BootCriticalMissingBannerE2ETest"
./gradlew :app:connectedDebugAndroidTest -PandroidTest.filter="BootBenchmarkTest"

# Full feature check:
./gradlew :core:check :lint-rules:check :app:check
```

---

## 3. Code structure tour (where things live)

```
core/src/commonMain/kotlin/com/launcher/api/
├── preset/                  ← Wire format types (Preset, Config, AbstractProfile, PresetRef)
├── profile/                 ← Per-device runtime types (ProfileData, ProfileStore, Binding, SettingEntry, Layout, Slot)
├── pools/                   ← Pool port + PoolEntry type
├── switchstrategy/          ← ProfileSwitchStrategy port
└── wizard/                  ← Existing — extended with ConfigKind.Preset + CheckSpec.UIFont

core/src/androidMain/kotlin/com/launcher/adapters/
├── pools/HardcodedPoolSource.kt   ← Primary live adapter (TASK-65)
├── pools/JsonAssetPoolSource.kt   ← Scaffold с TODO (impl later)
├── preset/PresetReminderService.kt
├── preset/PresetSwitchService.kt
├── preset/PresetSelectionService.kt
├── profile/PreferencesProfileStore.kt
└── wizard/UIFontChecker.kt        ← Handler for CheckSpec.UIFont

core/src/androidMain/kotlin/com/launcher/ui/
├── PresetPickerScreen.kt          ← Compose, reused first-launch + Settings
├── PresetBootRouter.kt            ← Activity router (boot path SEQ-3)
└── HomeBanner.kt                  ← Critical-missing banner (FR-030)

core/src/androidMain/assets/presets/
├── simple-launcher.preset.json
├── launcher.preset.json
├── workspace.preset.json
└── ...

core/src/androidTest/assets/
├── presets/test-preset.json       ← Fixture for fitness FR-026
└── wizard-manifests/legacy-with-app-family-id.json  ← Backward-compat fixture (R6)

lint-rules/                        ← NEW Gradle module (R5)
└── src/main/kotlin/com/launcher/lint/
    ├── PresetIdBranchingDetector.kt
    └── ExtractionReadinessDetector.kt
```

---

## 4. Adding a new preset

Example: adding a new `home-office` preset.

```bash
# 1. Create file:
cat > c:/work/launcher/core/src/androidMain/assets/presets/home-office.preset.json <<'EOF'
{
  "schemaVersion": 1,
  "uid": "com.launcher.preset.home-office",
  "version": 1,
  "slug": "home-office",
  "label": "preset_home_office_label",
  "description": "preset_home_office_description",
  "configs": [
    {
      "id": "android-role-home",
      "poolId": "system-settings",
      "poolVersion": 1,
      "entryId": "system-settings.android.role.home",
      "title": "settings_role_home_title",
      "description": "settings_role_home_description",
      "check": { "kind": "android-role", "role": "HOME" },
      "apply": { "kind": "settings-deep-link", "action": "ACTION_HOME_SETTINGS" },
      "criticality": "Required"
    }
  ],
  "abstractProfile": {
    "layout": {
      "screens": [{ "id": "main", "slots": [{ "position": 0 }] }],
      "grid": { "rows": 1, "columns": 1 },
      "toolbarTop": [],
      "toolbarBottom": []
    },
    "bindings": []
  },
  "pickEnabled": true
}
EOF

# 2. Add i18n keys to strings_wizard.xml:
# preset_home_office_label, preset_home_office_description

# 3. Build & verify:
./gradlew :core:test --tests "*BundledPresetsParseTest"   # parses new preset
./gradlew :app:connectedDebugAndroidTest -PandroidTest.filter="FirstLaunchPickerE2ETest"
# Expected: picker now shows 4 cards (simple-launcher, launcher, workspace, home-office).
```

**Zero code change** required — that's the whole point of TASK-65 (per Article VII §13).

---

## 5. Adding a new CheckSpec variant

Example: adding `CheckSpec.BluetoothEnabled` for a future preset that requires Bluetooth.

```kotlin
// 1. Extend sealed hierarchy in core/commonMain/api/wizard/data/CheckSpec.kt:
@Serializable
@SerialName("bluetooth-enabled")
data object BluetoothEnabled : CheckSpec()

// 2. Add handler in core/androidMain/adapters/wizard/BluetoothEnabledChecker.kt:
class BluetoothEnabledChecker(private val btAdapter: BluetoothAdapter?) : CheckHandler<CheckSpec.BluetoothEnabled> {
    override fun check(spec: CheckSpec.BluetoothEnabled): AppliedStatusRecord =
        if (btAdapter?.isEnabled == true) AppliedStatusRecord.Applied
        else AppliedStatusRecord.NotApplied
}

// 3. Register in DI:
single<CheckHandler<CheckSpec.BluetoothEnabled>> { BluetoothEnabledChecker(get()) }

// 4. Add pool entry (HardcodedPoolSource):
PoolEntry(
    id = "system-settings.android.bluetooth.enabled",
    title = "settings_bluetooth_title",
    description = "settings_bluetooth_description",
    check = CheckSpec.BluetoothEnabled,
    apply = ApplySpec.SettingsDeepLink(action = "ACTION_BLUETOOTH_SETTINGS"),
    criticality = Criticality.Optional
)

// 5. Test that engine dispatches correctly:
// (similar to EngineGenericityFitnessTest pattern for UIFont)
```

**Engine не меняется** — per Article VII §15-§16. Extension is additive.

---

## 6. Debugging

### Logcat filters

```bash
# Boot path issues:
adb logcat -s PresetBoot:V

# Settings switch issues:
adb logcat -s PresetSwitch:V

# Banner / reminder issues:
adb logcat -s PresetReminder:V

# Persistence issues:
adb logcat -s ProfileStore:V

# All TASK-65 logs:
adb logcat -s PresetBoot:V PresetSwitch:V PresetReminder:V PresetSelect:V ConfigSource:V PoolSource:V ProfileStore:V
```

### Inspect Profile state

```bash
# Pull DataStore Preferences file:
adb shell run-as com.launcher.app cat /data/data/com.launcher.app/files/datastore/profile_store.preferences_pb | head

# Or via Database Inspector в Android Studio (Tools → App Inspection).
```

### Reset Profile (debug)

```bash
# Clear app data:
adb shell pm clear com.launcher.app
# Next launch: PresetPickerScreen shows for fresh user (FR-011).
```

---

## 7. Common pitfalls

- **`presetId == "..."` outside whitelist** → Detekt rule fails commit. Fix: use composition (preset.configs lookup), not branching.
- **`import com.launcher.app.tiles.*` в `core/presets/`** → ExtractionReadinessDetector fails. Fix: pure domain types only in `core/presets/`.
- **`Map<PresetRef, ProfileData>` сериализуется с `::` в uid** → PresetRef.init throws. Fix: use namespaced reverse-DNS uid without `::`.
- **Adding new field to `preset.json` without bump** → may break server sync (TASK-70). Decision: optional with default = no bump; required field = bump + migrator.
- **Forgetting i18n keys для new preset's label / description** → `procedure-translate-spec-strings` skill catches at `/speckit.tasks` end.

---

## 8. Cross-references

- Spec: [`spec.md`](spec.md).
- Plan: [`plan.md`](plan.md).
- Research decisions: [`research.md`](research.md).
- Data model: [`data-model.md`](data-model.md).
- Contracts: [`contracts/preset-wire-format.md`](contracts/preset-wire-format.md), [`profile-store-format.md`](contracts/profile-store-format.md), [`pool-naming.md`](contracts/pool-naming.md).

---

## Owner-readable summary

**Quickstart** — это инструкция «как новый разработчик сюда заходит, что ему делать чтобы запустить и протестировать».

**Главные команды**:
- `./gradlew :core:test` — JVM тесты для composition logic.
- `./gradlew :lint-rules:test` — fitness rules (Detekt) тесты.
- `./gradlew :app:connectedDebugAndroidTest -PandroidTest.filter="FirstLaunchPickerE2ETest"` — е2е на эмуляторе.

**Как добавить новый preset** (без кода!):
1. Создать файл `<slug>.preset.json` в `core/src/androidMain/assets/presets/`.
2. Добавить i18n keys для label/description.
3. Запустить тест — picker автоматически покажет новый вариант.

Это и есть главное обещание TASK-65: profile-driven, не code-driven.

**Как добавить новый тип проверки** (например, Bluetooth):
1. Добавить variant в sealed hierarchy `CheckSpec` (commonMain).
2. Написать handler в androidMain.
3. Зарегистрировать в DI.
4. Добавить pool entry.

Engine не меняется — расширение additive.
