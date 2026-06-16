# Data Model: F-3

**Date**: 2026-06-16 (REVISED 2026-06-17 post pre-flight) | **Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)

Все типы expressed как domain data — без vendor SDK / platform types в signatures (CLAUDE.md rule 1). Wire format types (для JSON) описаны в [contracts/wire-formats.md](contracts/wire-formats.md); этот файл — runtime types и связи между ними.

> **REVISED 2026-06-17**: все типы живут в пакетах внутри **существующего `:core` модуля**:
> - Domain ports + data → `core/src/commonMain/kotlin/com/launcher/api/wizard/`
> - Localization → `core/src/commonMain/kotlin/com/launcher/api/localization/`
> - UI primitives → `core/src/commonMain/kotlin/com/launcher/ui/senior/` (Compose Multiplatform)
> - UI host + steps → `core/src/commonMain/kotlin/com/launcher/ui/wizard/`
> - Android adapters → `core/src/androidMain/kotlin/com/launcher/adapters/wizard/`

---

## 1. Wizard execution model

### `WizardEngine` (port в `core/src/commonMain/kotlin/com/launcher/api/wizard/`)

```kotlin
interface WizardEngine {
  suspend fun run(manifest: WizardManifest): WizardOutcome
  fun currentState(): StateFlow<WizardState>
  fun diffPending(
    savedCompletedManifest: WizardManifest?,
    currentManifest: WizardManifest
  ): List<PendingStep>
}
```

### `WizardState` (sealed)

```kotlin
sealed class WizardState {
  data object Idle : WizardState()
  data class Running(
    val currentStepIndex: Int,
    val totalSteps: Int,
    val currentStep: WizardStep,
    val answers: Map<StepId, JsonElement>
  ) : WizardState()
  data class Completed(val outcome: WizardOutcome.Completed) : WizardState()
}
```

### `WizardOutcome` (sealed)

```kotlin
sealed class WizardOutcome {
  data class Completed(
    val initialConfig: ConfigDocument,         // от спеки 008 ConfigDocument
    val userPreferences: UserPreferences       // см. ниже
  ) : WizardOutcome()
  data object Cancelled : WizardOutcome()
  data class Failed(val reason: String) : WizardOutcome()
}
```

### `WizardStep` (interface)

```kotlin
interface WizardStep {
  val stepType: StepType                       // sealed enum: UIChoice, SystemSetting, TutorialHint
  val canSkip: Boolean
  val canGoBack: Boolean
  suspend fun render(params: StepParams): StepResult
}

sealed class StepType {
  data object UIChoice : StepType()
  data object SystemSetting : StepType()
  data object TutorialHint : StepType()
  // Custom types от app-family добавляются через DI Map (FR-009)
}

sealed class StepResult {
  data class AnswerCaptured(val answer: JsonElement) : StepResult()
  data object Skipped : StepResult()
  data object Cancelled : StepResult()
}
```

### `PendingStep` (для delta wizard, FR-014b)

```kotlin
data class PendingStep(
  val stepEntry: StepEntry,                    // raw entry из manifest
  val criticality: Criticality
)
enum class Criticality { Required, Optional }
```

---

## 2. Wizard checkpoint (persistent)

### `WizardCheckpoint` (persistent format, schemaVersion=1)

```kotlin
data class WizardCheckpoint(
  val schemaVersion: Int = 1,                  // FR-003 + wire-format rule 5
  val manifestId: String,
  val currentStepIndex: Int,
  val answers: Map<StepId, JsonElement>
)
```

Wire format: serialized JSON в DataStore.
Load с `schemaVersion > known` → engine treats as invalid, starts from step 0 (graceful, FR-003).

### `WizardCheckpointStore` (port)

```kotlin
interface WizardCheckpointStore {
  suspend fun load(manifestId: String): WizardCheckpoint?
  suspend fun save(checkpoint: WizardCheckpoint)
  suspend fun clear(manifestId: String)
}
```

Real impl: `PersistentCheckpointStore` (DataStore Android).
Fake impl: `InMemoryCheckpointStore` (commonTest).

---

## 3. User preferences (persistent + future cloud sync target)

### `UserPreferences` (persistent format, schemaVersion=1)

```kotlin
data class UserPreferences(
  val schemaVersion: Int = 1,                  // wire format version per CLAUDE.md rule 5
  val theme: ThemeChoice,                       // Light | Dark | Auto
  val fontScale: Float?,                        // null = follow system
  val languageOverride: String?,                // null = system locale; BCP-47 tag
  val attestedSettings: Map<String, AttestationRecord>   // per-settingId self-attestation
)

enum class ThemeChoice { Light, Dark, Auto }

data class AttestationRecord(
  val attestedAt: Instant,                      // kotlinx.datetime.Instant
  val value: Boolean
)
```

Future migration target: `ConfigDocument.userPreferences` (in spec 008) when F-4 + cloud sync ready (per FR-051 inline TODO).
Future cross-app target: shared `ContentProvider` (when messenger ecosystem app materializes, per C-31).

### `UserPreferencesStore` (port)

```kotlin
interface UserPreferencesStore {
  suspend fun save(prefs: UserPreferences)
  fun observe(): Flow<UserPreferences>
  suspend fun current(): UserPreferences
}
```

---

## 4. Configuration sources (5 wire formats)

### `ConfigSource` (port)

```kotlin
interface ConfigSource {
  suspend fun list(kind: ConfigKind): List<ConfigSummary>
  suspend fun load(kind: ConfigKind, id: String): ConfigSourceResult
}

enum class ConfigKind {
  WizardManifest,
  ScreenLayout,
  TileSet,
  SystemSettingsPool,
  UICustomizationPool
}

sealed class ConfigSourceResult {
  data class Success(val document: ConfigDocument) : ConfigSourceResult()
  data class IncompatibleVersion(val found: Int, val known: Int) : ConfigSourceResult()
  data class ParseError(val reason: String) : ConfigSourceResult()
  data class NotFound(val id: String) : ConfigSourceResult()
}

data class ConfigSummary(
  val id: String,
  val name: String,                             // localization key
  val description: String,                      // localization key
  val deviceClass: List<String>
)
```

Real impl: `BundledConfigSource` (in `:app`, reads Compose Resources `MR.files.*`).
Fake impl: `FakeConfigSource` (in commonTest, constructed from in-memory Map).

### Concrete document types (in-memory representations)

```kotlin
// wizard.manifest body
data class WizardManifest(
  val schemaVersion: Int = 1,
  val id: String,                               // "wizard-manifest.simple-launcher"
  val nameKey: String, val descriptionKey: String,
  val deviceClass: List<String>,
  val appFamilyId: String,
  val steps: List<StepEntry>?,                  // null если autoOrder = true
  val autoOrder: Boolean = false                // FR-014c
)

data class StepEntry(
  val stepType: StepType,                       // sealed: UIChoice | SystemSetting | TutorialHint
  val refId: String,                            // optionId / settingId / hintId
  val params: Map<String, JsonElement>,
  val canSkip: Boolean = false,
  val criticality: Criticality? = null          // если null → derived from pool entry
)

// screen.layout body
data class ScreenLayout(
  val schemaVersion: Int = 1,
  val id: String, val nameKey: String, val descriptionKey: String,
  val deviceClass: List<String>,
  val gridRows: Int, val gridCols: Int,
  val bottomToolbar: ToolbarSpec? = null,
  val topTabs: List<TabSpec>? = null
)

// tile.set body
data class TileSet(
  val schemaVersion: Int = 1,
  val id: String, val nameKey: String, val descriptionKey: String,
  val deviceClass: List<String>,
  val tiles: List<TileSpec>
)

data class TileSpec(
  val position: GridPosition,                   // (row, col)
  val actionType: String,                       // opaque string ref to capability registry
  val labelKey: String, val iconKey: String
)

// system-settings.pool body (per Part K)
data class SystemSettingsPool(
  val schemaVersion: Int = 1,
  val id: String, val nameKey: String, val descriptionKey: String,
  val deviceClass: List<String>,
  val platform: Platform,                       // "android" | "ios" | "android-tv"
  val settings: List<SystemSettingEntry>
)

data class SystemSettingEntry(
  val id: String,                               // "android.role.home"
  val mechanism: SettingMechanism,              // sealed enum
  val criticality: Criticality,                 // Required | Optional (FR-053)
  val canSkip: Boolean = false,
  val deepLink: String? = null,                 // intent action name (Android)
  val androidMinApi: Int? = null,
  val dependsOn: List<String> = emptyList(),
  val detectionStrategy: DetectionStrategy,
  val labelKey: String, val descriptionKey: String,
  val extendedInstructionKey: String? = null
)

sealed class SettingMechanism {
  data object StandardPermission : SettingMechanism()
  data object SpecialPermission : SettingMechanism()
  data object AccessibilityService : SettingMechanism()
  data object DeepLink : SettingMechanism()
  data object InAppOnly : SettingMechanism()
}

enum class DetectionStrategy {
  Programmatic, SelfAttest, Indeterminate
}

// ui-customization.pool body (per FR-014a, NEW)
data class UICustomizationPool(
  val schemaVersion: Int = 1,
  val id: String, val nameKey: String, val descriptionKey: String,
  val deviceClass: List<String>,
  val platform: String,                         // "*" (cross-platform UI options)
  val options: List<UIOptionEntry>
)

data class UIOptionEntry(
  val id: String,                               // "language", "theme", "tileSet"
  val kind: UIOptionKind,
  val questionKey: String,
  val descriptionKey: String?,
  val criticality: Criticality,
  val defaultValue: String,
  // Для simple-choice:
  val choices: List<Choice>? = null,
  // Для pick-from-bundled:
  val choicesFrom: ChoicesFromRef? = null
)

sealed class UIOptionKind {
  data object SimpleChoice : UIOptionKind()
  data object PickFromBundled : UIOptionKind()
}

data class Choice(val value: String, val labelKey: String)
data class ChoicesFromRef(val kind: ConfigKind, val filter: String? = null)
```

---

## 5. System settings application

### `SystemSettingPort` (port)

```kotlin
interface SystemSettingPort {
  suspend fun status(settingId: String): SettingStatus
  suspend fun applyOrPrompt(settingId: String): ApplyResult
}

sealed class SettingStatus {
  data object Applied : SettingStatus()
  data object NotApplied : SettingStatus()
  data object Indeterminate : SettingStatus()          // нет programmatic API → требуется SelfAttest
  data object NotSupportedOnPlatform : SettingStatus()
  data class CheckFailed(val reason: String) : SettingStatus()
}

sealed class ApplyResult {
  data object Applied : ApplyResult()
  data object PromptShown : ApplyResult()              // открыт deep-link / системный диалог
  data object UnsupportedMechanism : ApplyResult()
  data class Failed(val reason: String) : ApplyResult()
}
```

Real impl: `AndroidSystemSettingAdapter` (in `:app/androidMain`, reads android-pool.json via ConfigSource, dispatches per mechanism).
Fake impl: `FakeSystemSettingAdapter` (constructed from `Map<settingId, SettingStatus>`).

---

## 6. Localization

### `StringResolver` (port)

```kotlin
interface StringResolver {
  fun resolve(key: String, args: Map<String, Any> = emptyMap()): String
  fun currentLocaleTag(): String                       // BCP-47 (e.g. "ru", "kk-Latn", "en-US")
}
```

Real impl: `AndroidStringResolverAdapter` (binds Compose Resources MR class).
Fake: constructed with `FakeLocaleProvider` + inline `Map<key, String>` per test.

### `LocaleProvider` (port)

```kotlin
interface LocaleProvider {
  fun currentLocaleTag(): String                       // returns BCP-47
}
```

Real impl: `AndroidLocaleProvider` (reads `Resources.configuration.locales[0]`, converts to BCP-47).
Fake: `FakeLocaleProvider` allows test override.

### `RtlHelper` (free function)

```kotlin
fun layoutDirectionFor(localeTag: String): LayoutDirection  // returns Rtl для AR/HI tags
```

---

## 7. Hint management

### `TutorialHintManager` (class)

```kotlin
class TutorialHintManager(
  private val dismissedHintsStore: DismissedHintsStore,
  private val stringResolver: StringResolver,
  private val clock: Clock
) {
  suspend fun show(hintId: String, anchor: HintAnchor, textKey: String): HintResult
  fun isDismissed(hintId: String): Boolean
  suspend fun reset(hintId: String)
}

sealed class HintResult { data object Dismissed; data object AlreadyDismissed }
sealed class HintAnchor { data object TopLeft; data object TopRight; ... }  // плейсхолдер
```

### `DismissedHintsStore` (port)

```kotlin
interface DismissedHintsStore {
  suspend fun isDismissed(hintId: String): Boolean
  suspend fun markDismissed(hintId: String)
  suspend fun clear(hintId: String)
}
```

---

## 8. Cross-cutting ports

### `Clock` (port — per A-18)

```kotlin
interface Clock {
  fun now(): Instant                              // kotlinx.datetime.Instant
}
```

Real: `SystemClock` (delegates to `kotlinx.datetime.Clock.System`).
Fake: `FakeClock(fixedInstant: Instant)`.

### `AnimationPreferenceProvider` (port — per FR-036a reduce-motion)

```kotlin
interface AnimationPreferenceProvider {
  fun durationScale(): Float                      // 0.0 = reduce-motion; 1.0 = normal
}
```

Real: `AndroidAnimationPreferenceProvider` (reads `Settings.Global.ANIMATOR_DURATION_SCALE`).
Fake: defaults to 1.0; test can override.

### `DiagnosticEmitter` (port — per A-17)

```kotlin
interface DiagnosticEmitter {
  fun emit(event: DiagnosticEvent)
}

sealed class DiagnosticEvent {
  data class WizardStarted(val manifestId: String) : DiagnosticEvent()
  data class WizardStepCompleted(val stepIndex: Int, val stepType: String) : DiagnosticEvent()
  data class WizardCompleted(val manifestId: String) : DiagnosticEvent()
  data class WizardCancelled(val atStep: Int) : DiagnosticEvent()
  data class WizardStepDenied(val settingId: String, val isPermanent: Boolean) : DiagnosticEvent()
  data class FallbackWarning(val area: String, val reason: String) : DiagnosticEvent()
}
```

Real impl: **none в F-3** (per A-17 — analytics backend ships в S-1+).
Fake: `RecordingDiagnosticEmitter` (captures events для assertions).

### `PermissionRequestPort` (port — для StandardPermission mechanism в SystemSettingAdapter)

```kotlin
interface PermissionRequestPort {
  suspend fun request(permission: String): PermissionResult
  fun isGranted(permission: String): Boolean
  fun isPermanentlyDenied(permission: String): Boolean
}

sealed class PermissionResult { data object Granted; data object Denied; data object PermanentlyDenied }
```

---

## 9. Senior UI primitives (data shape — UI types в `com.launcher.ui.senior`)

Не строго data model, но fixated public API surface:

| Composable | Public parameters | Senior-safe constraints (per FR-034) |
|---|---|---|
| `SeniorButton(text, onClick, modifier)` | text: String, onClick: () -> Unit | height ≥ 56dp, text ≥ 18sp, wrapContent, autoMirrored icons |
| `SeniorIconButton(icon, onClick, contentDescription, modifier)` | icon: ImageVector, cd: String | ≥ 56dp square, autoMirrored |
| `SeniorTextField(value, onValueChange, label)` | value, onValueChange, label | height ≥ 56dp, wrapContentHeight |
| `SeniorBodyText(text, modifier)` | text: String | fontSize ≥ 18sp, line-height 1.5× |
| `SeniorTitleText(text, modifier)` | text: String | fontSize ≥ 24sp, line-height 1.5× |
| `SeniorWarmTheme.Light / Dark` | content: @Composable () -> Unit | warm-contrast palette, ≥ 7:1 contrast |
| `WizardProgressIndicator(stepIndex, totalSteps)` | stepIndex, totalSteps | text «Шаг N из M» ≥ 18sp + dots, FR-008c |
| `LiveRegionAnnouncement(text)` | text: String | Modifier.semantics liveRegion = Polite, FR-008b |
| `TutorialHintOverlay(text, anchor, onDismiss)` | text, anchor, onDismiss | dismissible через «Понял» button, FR-023 |

---

## 10. Entity relationships diagram

```
WizardEngine
  ├─ loads → WizardManifest (от ConfigSource)
  ├─ iterates → StepEntry[]
  │     ├─ stepType=UIChoice → UIChoiceStep → reads UIOptionEntry from ui-pool
  │     ├─ stepType=SystemSetting → SystemSettingStep → reads SystemSettingEntry from android-pool
  │     │      └─ → SystemSettingPort.status / applyOrPrompt
  │     └─ stepType=TutorialHint → TutorialHintStep → TutorialHintManager
  │           └─ → DismissedHintsStore
  ├─ writes → WizardCheckpoint → WizardCheckpointStore
  └─ produces → WizardOutcome.Completed
        ├─ initialConfig: ConfigDocument (от спеки 008)
        └─ userPreferences: UserPreferences → UserPreferencesStore
              └─ attestedSettings: Map<String, AttestationRecord(attestedAt: Instant, value: Boolean)>
                    └─ uses → Clock

StringResolver (через app DI)
  └─ uses → LocaleProvider
        └─ uses → Compose Resources MR.strings (real adapter)
```

---

## Краткое содержание простым русским языком

Этот документ — **список всех объектов** (классов / интерфейсов), которые мы создаём в коде F-3.

**Главное упрощённо**:

1. **WizardEngine** — главный «движок мастера настройки». Берёт инструкцию (manifest) и проводит пользователя по шагам.
2. **WizardCheckpoint** — «закладка»: где остановился пользователь. Хранится на диске, переживает выключение телефона.
3. **UserPreferences** — настройки пользователя (тема, шрифт, язык). Тоже хранится на диске. В будущем будет sync'аться с другими приложениями экосистемы.
4. **ConfigSource** — «загрузчик файлов». Читает 5 разных JSON: какие шаги показывать, какие настройки Android доступны, какие UI опции, какие наборы плиток, какой каркас экрана.
5. **SystemSettingPort** — «помощник для Android настроек». Знает, как открыть диалог Android для каждой настройки и как проверить, применилась ли она.
6. **StringResolver** — «переводчик». Берёт ключ строки («wizard.next_button») и язык, возвращает текст («Далее» / «Next» / «Weiter»).
7. **TutorialHintManager** — «подсказки». Показывает всплывающие подсказки и помнит, какие пользователь уже видел.
8. **Senior UI** — набор больших кнопок и тёплых тем для пожилых.

**Почему так много интерфейсов** (port'ов): каждый «помощник» (для дисков, для системных настроек, для перевода) **имеет 2 версии**:
- **Настоящая** — реально работает на Android.
- **Тестовая** — для проверки кода без эмулятора.

Это позволяет тестировать бизнес-логику быстро (без запуска эмулятора), что экономит часы каждый день.

**Что отложено**:
- Реальная аналитика (DiagnosticEmitter port есть, но реализация — в S-1).
- iOS реализации всех port'ов — когда дойдёт iOS launcher.
- Care family ContentProvider — когда дойдёт мессенджер.
