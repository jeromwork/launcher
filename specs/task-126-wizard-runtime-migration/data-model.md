# Data Model: Wizard Runtime Migration (TASK-126)

**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)

All changes are additive. No field renames or removals. Backward-compatible read from v1 guaranteed.

---

## Component sealed class — new subtypes (FR-002..005)

Location: `core/src/commonMain/kotlin/com/launcher/preset/model/Component.kt`

```kotlin
// ADD to existing Component sealed class:

@Serializable
@SerialName("LauncherRole")
object LauncherRole : Component()                      // FR-002: no parameters; D2

@Serializable
@SerialName("Theme")
data class Theme(
    val paletteSeedHex: String,                        // e.g. "#FF5722"
    val typographyScale: TypographyScale,
    val shapeStyle: ShapeStyle,
    val darkMode: Boolean,
) : Component()                                        // FR-003; D3 flat fields only

@Serializable
@SerialName("Language")
data class Language(
    val locale: String,                                // "system" = follow OS; D4
) : Component()                                        // FR-004; null rejected by PresetValidator

@Serializable
@SerialName("StatusBarPolicy")
object StatusBarPolicy : Component()                   // FR-005; D8: no parameters
```

New enums (add to `Enums.kt` if not already present):

```kotlin
enum class TypographyScale { Small, Medium, Large, ExtraLarge }
enum class ShapeStyle { Rounded, Sharp, Mixed }
```

---

## Pool — ComponentDeclaration changes (FR-006)

Location: `core/src/commonMain/kotlin/com/launcher/preset/model/Pool.kt`

```kotlin
// BEFORE:
data class ComponentDeclaration(
    val id: String,
    val component: Component,
    val wizardBehavior: WizardBehavior = WizardBehavior.AutoApply,
    val critical: Boolean = false,
    val descriptionKey: String? = null,
)

// AFTER (additive):
data class ComponentDeclaration(
    val id: String,
    val component: Component,
    val wizardBehavior: WizardBehavior = WizardBehavior.AutoApply,
    val critical: Boolean = false,
    val descriptionKey: String? = null,
    val requires: List<String>? = null,                // FR-006: component IDs that must appear earlier in wizardFlow
    val required: Boolean = false,                     // FR-006 CL-3: wizard complete when all required=true are Applied
)

// Pool schemaVersion:
data class Pool(
    val schemaVersion: Int = 2,                       // BUMP: 1 → 2
    val declarations: List<ComponentDeclaration>,
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION: Int = 2     // was 1
    }
}
```

---

## Preset — new fields (FR-003, FR-007, FR-014)

Location: `core/src/commonMain/kotlin/com/launcher/preset/model/Preset.kt`

```kotlin
// NEW: wizard visual appearance (CL-2; applied once at wizard start; not a wizardFlow step)
@Serializable
data class WizardPresentation(
    val darkMode: Boolean = false,
    val typographyScale: TypographyScale = TypographyScale.Medium,
)

// BEFORE Preset:
data class Preset(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val presetId: String,
    val version: Int,
    val layoutKey: String,
    val wizardFlow: List<WizardFlowEntry> = emptyList(),
    val settingsMap: List<SettingsMapEntry> = emptyList(),
    val activeComponents: List<ActiveComponentEntry> = emptyList(),
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION: Int = 2     // was 1 (already bumped in codebase)
    }
}

// AFTER (additive):
data class Preset(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val presetId: String,
    val version: Int,
    val layoutKey: String,
    val wizardFlow: List<WizardFlowEntry> = emptyList(),
    val settingsMap: List<SettingsMapEntry> = emptyList(),
    val activeComponents: List<ActiveComponentEntry> = emptyList(),
    val hintFlow: List<HintFlowEntry>? = null,        // FR-007; D5: UI-layer only
    val wizardPresentation: WizardPresentation? = null, // FR-003 CL-2; applied once at wizard start
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION: Int = 2
    }
}
```

---

## HintFlowEntry — new type (FR-007)

Location: `core/src/commonMain/kotlin/com/launcher/preset/model/HintFlowEntry.kt` (NEW FILE)

```kotlin
@Serializable
data class HintFlowEntry(
    val hintId: String,
    val targetComponentId: String,       // references a ComponentDeclaration.id in pool
    val textKey: String,                 // localization key for hint text
)
```

`hint-pool.json` asset: loaded via `HintPoolSource` port + `BundledHintPoolSource` adapter (CL-7 — see dedicated section below). Structure:

```json
{
  "schemaVersion": 1,
  "hints": [
    { "hintId": "hint-launcher-role", "titleKey": "hint_launcher_role_title", "bodyKey": "hint_launcher_role_body" }
  ]
}
```

---

## Wizard progress — NO separate store (CL-5, supersedes CL-3)

**No `WizardStore` class is introduced.** The prior CL-3 decision to persist `lastCompletedStepIndex: Int` is **revised** by CL-5. Rationale: an explicit counter drifts when Android OS state changes externally (user grants a permission through system Settings, system update revokes launcher role, factory reset).

Wizard progress is derived on every entry:

```
ReconcileEngine.run(RunMode.Wizard)
  for each ComponentDeclaration in preset.wizardFlow:
    when Provider.check(component):
      Ok         → step already done in reality; skip
      NeedsApply → emit Interactive(componentId); wait for InteractionSink.answer()
      Failed     → emit ReconcileState.Failed(componentId, reason)
```

Only `ProfileStore` (existing, TASK-120) persists Component statuses. No new store.

---

## HintPoolSource — new port + adapter (FR-007, CL-7)

Location (domain): `core/src/commonMain/kotlin/com/launcher/preset/port/HintPoolSource.kt` (NEW FILE)

```kotlin
interface HintPoolSource {
    suspend fun load(): HintPool
}

@Serializable
data class HintPool(
    val schemaVersion: Int = 1,
    val hints: List<HintDescriptor> = emptyList(),
)

@Serializable
data class HintDescriptor(
    val hintId: String,
    val titleKey: String,
    val bodyKey: String,
)
```

Location (adapter): `app/src/main/java/com/launcher/app/preset/task126/BundledHintPoolSource.kt` (NEW FILE)

```kotlin
// TODO(shareability): future HintPoolSource adapters — file import, share intent, marketplace (CLAUDE.md rule 9)
class BundledHintPoolSource(private val assets: AssetManager) : HintPoolSource {
    override suspend fun load(): HintPool = runCatching {
        val json = assets.open("hint-pool.json").bufferedReader().use { it.readText() }
        Json.decodeFromString<HintPool>(json)
    }.getOrElse { HintPool(schemaVersion = 1, hints = emptyList()) } // missing/malformed → empty pool
}
```

---

## ValidationError — sealed class (FR-019, CL-8)

Location: `core/src/commonMain/kotlin/com/launcher/preset/model/ValidationError.kt` (NEW FILE)

```kotlin
sealed class ValidationError {
    data class RequiresOrderViolation(val offenderId: String, val missingId: String) : ValidationError()
    data class UnknownComponentId(val id: String) : ValidationError()
    data class NullLocale(val componentId: String) : ValidationError()
    data class SchemaVersionUnsupported(val actual: Int, val supported: IntRange) : ValidationError()
}
```

`PresetValidator.validate(preset, pool)` signature changes from `throw PresetValidationException` to:

```kotlin
fun validate(preset: Preset, pool: Pool): Result<Preset, ValidationError>
// Kotlin's stdlib Result or a domain-owned Either / Result alias — implementation choice at Phase 1.
```

Bundled preset validation gate (CI, FR-019):

```kotlin
class BundledPresetValidationTest {
    @Test fun `all bundled presets validate successfully`() {
        val pool = /* load bundled pool */
        val presets = listOf("workspace", "launcher", "simple-launcher")
        presets.forEach { name ->
            val preset = /* load JSON */
            val result = PresetValidator.validate(preset, pool)
            assertTrue(result.isSuccess) { "$name failed: ${(result as Failure).error}" }
        }
    }
}
```

---

## ThemeRef — write-time sugar (FR-003, D3)

Location: `app/src/main/java/com/launcher/app/preset/task126/ThemeCatalog.kt` (NEW FILE)

```kotlin
// NOT stored in wire format. Expansion happens at content-authoring time.
data class ThemeRef(val name: String)

class ThemeCatalog(private val catalogJson: String) {
    fun expand(ref: ThemeRef): Component.Theme  // looks up ref.name in theme-catalog.json
}
```

`theme-catalog.json` asset:
```json
{
  "schemaVersion": 1,
  "themes": [
    {
      "name": "warm-light",
      "paletteSeedHex": "#FF7043",
      "typographyScale": "Large",
      "shapeStyle": "Rounded",
      "darkMode": false
    }
  ]
}
```

---

## Deleted types (Phase 6)

All types in these packages are deleted outright (zero production users — Article XX):

- `com.launcher.api.wizard.*` — `WizardEngine`, `WizardState`, `WizardStep`, `CheckHandler`, `ApplyHandler`, `ConfigKind`, `ConfigSource`, `ConfigDocument`, `WizardManifest`, `WizardCheckpoint`, `UserPreferences`, `UserPreferencesStore`, `SystemSettingPort`, `PermissionRequestPort`, `AnimationPreferenceProvider`, `DismissedHintsStore`, `DiagnosticEmitter`, `PendingStep`, `Clock`, `TileSet`, `ScreenLayout`, `UICustomizationPool`, `SystemSettingsPool`, `ApplySpec`, `CheckSpec`, `ConfigParser`, `WizardOutcome`
- `com.launcher.api.preset.*` — `AbstractProfile`, `Config`, `Preset` (TASK-65), `PresetRef`
- `com.launcher.api.profile.*`, `com.launcher.api.pools.*`, `com.launcher.api.switchstrategy.*`

---

## Novice Summary (для владельца)

**Что описывается в этом файле?**

Здесь точные изменения в структурах данных — какие поля добавляются в JSON и в код Kotlin. Это технический справочник для разработчика.

**Главное что нужно знать:**

- Добавляем 4 новых «типа настройки» (`LauncherRole`, `Theme`, `Language`, `StatusBarPolicy`) — без параметров или с минимальными.
- Пресет и пул получают новые необязательные поля (если поля нет в старом JSON — всё работает как раньше).
- **Нет отдельного хранилища прогресса wizard'а** (revised CL-5). Wizard на каждом запуске спрашивает у Android «а это уже сделано?» через `Provider.check()`. Одно хранилище — `ProfileStore` (уже есть в TASK-120).
- **HintPoolSource — port + adapter** (CL-7). Порт в domain, `BundledHintPoolSource` читает `hint-pool.json` из assets. `schemaVersion: 1` — можно будет добавить file-import / marketplace без переписывания.
- **ValidationError sealed class** (CL-8). PresetValidator возвращает `Result<Preset, ValidationError>` вместо exception — типизированная ошибка, ловится на CI через `BundledPresetValidationTest`.
- Старые типы данных (`WizardEngine`, `ConfigKind` и ~80 других) полностью удаляются в последней фазе.
