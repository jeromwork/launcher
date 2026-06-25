# Data Model: TASK-7

**Date**: 2026-06-24 | **Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)

> **⚠️ UPDATE 2026-06-25.** Section **§5 "CustomStep + CustomStepHandler"** below is **REVERTED** per constitution amendment 1.10 (no `StepType.Custom`, no per-refId handlers). `CustomStep.kt`, `CustomStepHandler` port, and `PairAdminCustomStepHandler` files deleted from the codebase. `WireStepType.Custom`, `StepType.Custom(name)`, `CUSTOM_DISPATCH_KEY` removed. Pair-admin returns at TASK-8 as a standard `SystemSetting` step with new `CheckSpec.PairAdminLink` + `ApplySpec.PairAdminIntent` variants. See [spec.md](spec.md) header + [`docs/dev/project-backlog.md`](../../docs/dev/project-backlog.md) → TODO-TASK7-005.

All types expressed as domain data — no vendor SDK / platform types in signatures (CLAUDE.md rule 1). Wire format types (for JSON) are described in [contracts/](contracts/); this file covers runtime types and relationships.

Types live in packages within the existing `:core` module:
- Domain ports + data → `core/src/commonMain/kotlin/com/launcher/api/wizard/`
- Adapter implementations → `core/src/androidMain/kotlin/com/launcher/adapters/wizard/handlers/`

---

## 1. Declarative dispatch types (new in TASK-7)

### 1.1 `CheckSpec` (sealed, commonMain)

```kotlin
@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("kind")
@Serializable
sealed class CheckSpec {

  @Serializable @SerialName("android-role")
  data class AndroidRole(val role: String) : CheckSpec()

  @Serializable @SerialName("android-permission")
  data class AndroidPermission(val permission: String) : CheckSpec()

  @Serializable @SerialName("android-special-permission")
  data class AndroidSpecialPermission(val variant: String) : CheckSpec()  // e.g. "ignore_battery_optimizations"

  @Serializable @SerialName("android-accessibility-service")
  data class AndroidAccessibilityService(val componentName: String? = null) : CheckSpec()

  @Serializable @SerialName("android-package-home")
  data class AndroidPackageHome(val packageName: String? = null) : CheckSpec()
  // packageName = null → check that current default home matches our own packageName
}
```

**JSON wire format example**:
```json
"check": { "kind": "android-role", "role": "HOME" }
```

**Cross-platform notes** (per Article VII §15):
- Future iOS variants (e.g., `IosAuthorizationStatus`, `IosInfoPlist`) added to the same sealed hierarchy in commonMain.
- Handlers for each variant live in the corresponding platform adapter module (androidMain / iosMain / etc.).
- Variants not registered in current build → `SystemSettingPort.status()` returns `SettingStatus.Indeterminate` (graceful degradation).

### 1.2 `ApplySpec` (sealed, commonMain)

```kotlin
@OptIn(ExperimentalSerializationApi::class)
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

**JSON wire format example**:
```json
"apply": { "kind": "android-role-request", "role": "HOME" }
```

### 1.3 `CheckHandler` (port, commonMain)

```kotlin
package com.launcher.api.wizard.handlers

interface CheckHandler {
  suspend fun check(spec: CheckSpec): SettingStatus
}
```

Real impls (androidMain — one per CheckSpec variant):
- `AndroidRoleCheckHandler` — uses `RoleManager.isRoleHeld(role)` on API 29+; legacy `PackageManager.resolveActivity(CATEGORY_HOME)` fallback on API 26-28.
- `AndroidPermissionCheckHandler` — wraps `PermissionRequestPort.isGranted(permission)`.
- `AndroidSpecialPermissionCheckHandler` — dispatches by `variant`: `ignore_battery_optimizations` → `PowerManager.isIgnoringBatteryOptimizations()`.
- `AndroidAccessibilityServiceCheckHandler` — returns `Indeterminate` (programmatic detection unreliable; relies on SelfAttest pattern).
- `AndroidPackageHomeCheckHandler` — `PackageManager.resolveActivity(CATEGORY_HOME)` + compare to own package.

Fake impl (commonTest):
- `FakeCheckHandler` — pre-configured `Map<CheckSpec, SettingStatus>`.

### 1.4 `ApplyHandler` (port, commonMain)

```kotlin
package com.launcher.api.wizard.handlers

interface ApplyHandler {
  suspend fun apply(spec: ApplySpec): ApplyResult
}
```

Real impls (androidMain — one per ApplySpec variant):
- `AndroidStandardPermissionApplyHandler` — wraps `PermissionRequestPort.request(permission)`.
- `AndroidRoleApplyHandler` — `RoleManager.createRequestRoleIntent(role)` + `context.startActivity()`.
- `AndroidSettingsDeepLinkApplyHandler` — builds `Intent(action)`, optionally scoped to package via `Uri.parse("package:${packageName}")`.
- `AndroidInAppOnlyApplyHandler` — returns `PromptShown` (caller handles in-app toggle UI).

Fake impl (commonTest):
- `FakeApplyHandler` — pre-configured `Map<ApplySpec, ApplyResult>`.

### 1.5 Handler registry

DI-wired `Map<KClass<out CheckSpec>, CheckHandler>` and `Map<KClass<out ApplySpec>, ApplyHandler>` injected into `AndroidSystemSettingAdapter`:

```kotlin
class AndroidSystemSettingAdapter(
  private val context: Context,
  private val configSource: ConfigSource,
  private val permissionRequestPort: PermissionRequestPort,
  private val checkHandlers: Map<KClass<out CheckSpec>, CheckHandler>,
  private val applyHandlers: Map<KClass<out ApplySpec>, ApplyHandler>,
  private val cache: SettingStatusCache,
  private val lifecycleOwnerProvider: LifecycleOwnerProvider,
) : SystemSettingPort { ... }
```

Koin wiring:
```kotlin
val coreAndroidModule = module {
  // Handler registry (CheckSpec)
  single<Map<KClass<out CheckSpec>, CheckHandler>> {
    mapOf(
      CheckSpec.AndroidRole::class to AndroidRoleCheckHandler(get()),
      CheckSpec.AndroidPermission::class to AndroidPermissionCheckHandler(get()),
      CheckSpec.AndroidSpecialPermission::class to AndroidSpecialPermissionCheckHandler(get()),
      CheckSpec.AndroidAccessibilityService::class to AndroidAccessibilityServiceCheckHandler(),
      CheckSpec.AndroidPackageHome::class to AndroidPackageHomeCheckHandler(get()),
    )
  }
  // Handler registry (ApplySpec) — analogous
  // ...
}
```

---

## 2. Engine extensions (modifications to existing F-3 types)

### 2.1 `WizardEngine` (existing port, modified)

```kotlin
interface WizardEngine {
  // EXISTING:
  suspend fun run(manifest: WizardManifest): WizardOutcome
  fun currentState(): StateFlow<WizardState>

  // NEW in TASK-7:
  suspend fun computePending(manifest: WizardManifest): List<StepEntry>
  suspend fun runWalkThrough(manifest: WizardManifest): WizardOutcome

  // DEPRECATED in TASK-7 (kept for backward compat; remove in TASK-22):
  suspend fun diffPending(
    savedCompletedManifest: WizardManifest?,
    currentManifest: WizardManifest,
  ): List<PendingStep>
}
```

### 2.2 `WizardEngineImpl` (existing impl, modified)

Modifications:
- Add `computePending(manifest)` impl — iterates `orderedSteps(manifest)`, queries `SystemSettingPort.status()` per SystemSetting step, queries `UserPreferencesStore.current()` per UIChoice step.
- Modify `run(manifest)` to call `computePending()` as pre-flight; traverse only pending.
- Add `runWalkThrough(manifest)` impl — traverses all steps; per step displays current value + "Оставить"/"Изменить" options.
- `diffPending(...)` impl remains; deprecation annotation added.

### 2.3 `UserPreferences` (existing data class, modified)

```kotlin
@Serializable
data class UserPreferences(
  val schemaVersion: Int = 1,
  val theme: ThemeChoice = ThemeChoice.Auto,
  val fontScale: Float? = null,
  val languageOverride: String? = null,
  val attestedSettings: Map<String, AttestationRecord> = emptyMap(),
  val wizardCompletedAppFamilies: Set<String> = emptySet(),
) {
  // NEW helper in TASK-7
  fun hasValueFor(refId: String): Boolean = when (refId) {
    "theme" -> theme != ThemeChoice.Auto || refId in attestedSettings  // Auto is implicit; explicit non-Auto = set
    "fontScale" -> fontScale != null
    "language" -> languageOverride != null
    else -> attestedSettings.containsKey(refId)
  }
}
```

---

## 3. Pool wire format v2 (modifications to existing F-3 type)

### 3.1 `SystemSettingsPool` schema bump 1 → 2

`SystemSettingEntry` gains two optional fields:

```kotlin
@Serializable
data class SystemSettingEntry(
  // EXISTING fields:
  val id: String,
  val mechanism: WireSettingMechanism,
  val criticality: WireCriticality,
  val canSkip: Boolean = false,
  val deepLink: String? = null,
  val androidMinApi: Int? = null,
  val dependsOn: List<String> = emptyList(),
  val detectionStrategy: WireDetectionStrategy,
  val labelKey: String,
  val descriptionKey: String,
  val extendedInstructionKey: String? = null,

  // NEW in v2 (optional for backward compat):
  val check: CheckSpec? = null,
  val apply: ApplySpec? = null,
)
```

Backward-compat read: v1 entries (no `check`/`apply` blocks) deserialize with `check = null` / `apply = null`. `AndroidSystemSettingAdapter` falls back to legacy `mechanism + settingId` dispatch when `check == null`.

Forward-compat (Kubernetes-style additive): readers ignore unknown fields → future v3 entries readable by v2 readers as v2 entries (minus the v3-only fields).

---

## 4. Cache types (new in TASK-7)

### 4.1 `SettingStatusCache` (impl-internal, androidMain)

```kotlin
class SettingStatusCache(
  private val clock: Clock,
  private val ttl: Duration = 30.seconds,
) {
  private val entries: MutableMap<String, Pair<SettingStatus, Instant>> = mutableMapOf()

  fun get(settingId: String): SettingStatus? {
    val (status, recordedAt) = entries[settingId] ?: return null
    val age = clock.now() - recordedAt
    return if (age > ttl) null else status
  }

  fun put(settingId: String, status: SettingStatus) {
    entries[settingId] = status to clock.now()
  }

  fun invalidate(settingId: String) { entries.remove(settingId) }
  fun invalidateAll() { entries.clear() }
}
```

### 4.2 Lifecycle integration

```kotlin
// androidMain
class CacheInvalidatingLifecycleObserver(
  private val cache: SettingStatusCache,
) : DefaultLifecycleObserver {
  override fun onResume(owner: LifecycleOwner) {
    cache.invalidateAll()
  }
}
```

Registered in `WizardActivity.onCreate()` and any Settings activity that uses `SystemSettingPort`.

---

## 5. Custom step types (new in TASK-7)

### 5.1 `CustomStep` (commonMain UI host)

Existing `WizardStep` interface (F-3) handles UIChoice / SystemSetting / TutorialHint step types via DI map. New `Custom` step type dispatch via `refId`:

```kotlin
// commonMain
class CustomStep(
  private val handlers: Map<String, CustomStepHandler>,  // refId → handler
) : WizardStep {
  override val stepType: StepType = StepType.Custom("dispatch")
  override val canSkip: Boolean get() = true  // per-handler override
  override val canGoBack: Boolean get() = true

  override suspend fun execute(params: StepParams): StepResult {
    val handler = handlers[params.refId]
      ?: return StepResult.Skipped  // graceful: unknown custom step
    return handler.execute(params)
  }
}

interface CustomStepHandler {
  suspend fun execute(params: StepParams): StepResult
}
```

### 5.2 `PairAdminCustomStepHandler` (androidMain)

```kotlin
class PairAdminCustomStepHandler(
  private val context: Context,
  private val linkRegistry: LinkRegistry,
  private val pairingActivityIntent: () -> Intent,
) : CustomStepHandler {
  override suspend fun execute(params: StepParams): StepResult {
    return try {
      val intent = pairingActivityIntent().apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
      context.startActivity(intent)
      // Suspending until PairingActivity returns result; see plan.md Phase 5 for activity-result wiring
      val outcome = awaitPairingActivityResult()
      when (outcome) {
        is PairingOutcome.Success -> StepResult.AnswerCaptured(JsonPrimitive("paired"))
        is PairingOutcome.Cancelled -> StepResult.Skipped
        is PairingOutcome.Failed -> StepResult.Skipped  // graceful
      }
    } catch (e: Exception) {
      StepResult.Skipped  // graceful: don't crash wizard
    }
  }
}
```

---

## 6. UI types (new in TASK-7)

### 6.1 Pending checklist screen state

```kotlin
// commonMain — used by Settings UI
data class PendingChecklistState(
  val items: List<PendingItem>,
) {
  data class PendingItem(
    val settingRefId: String,
    val labelKey: String,
    val descriptionKey: String,
    val isRequired: Boolean,
  )
}

class PendingChecklistViewModel(
  private val engine: WizardEngine,
  private val configSource: ConfigSource,
  private val stringResolver: StringResolver,
) {
  suspend fun load(): PendingChecklistState {
    val manifest = loadSimpleLauncherManifest()
    val pending = engine.computePending(manifest)
    return PendingChecklistState(
      items = pending.map { entry ->
        PendingChecklistState.PendingItem(
          settingRefId = entry.refId,
          labelKey = lookupLabelKey(entry),
          descriptionKey = lookupDescriptionKey(entry),
          isRequired = entry.criticality == WireCriticality.Required,
        )
      },
    )
  }
}
```

### 6.2 Locale divergence indicator state

```kotlin
data class LocaleDivergenceState(
  val appLocale: String,  // BCP-47 tag
  val systemLocale: String,  // BCP-47 tag
  val diverges: Boolean = appLocale != systemLocale,
)

class LocaleDivergenceViewModel(
  private val localeProvider: LocaleProvider,
  private val userPreferencesStore: UserPreferencesStore,
) {
  fun state(): LocaleDivergenceState {
    val app = userPreferencesStore.current().languageOverride
      ?: localeProvider.currentLocaleTag()
    val system = localeProvider.systemLocaleTag()
    return LocaleDivergenceState(app, system)
  }
}
```

---

## 7. Relationship diagram

```
WizardEngine (port)
  ├─ computePending(manifest): List<StepEntry>     [NEW]
  │   └─ for each StepEntry:
  │       ├─ SystemSettingPort.status(refId)       [calls handler registry]
  │       │   └─ SettingStatusCache.get(refId)
  │       │       └─ checkHandlers[CheckSpec::class].check(spec)
  │       │           └─ AndroidRoleCheckHandler / AndroidPermissionCheckHandler / etc.
  │       └─ UserPreferencesStore.current().hasValueFor(refId)
  ├─ run(manifest): WizardOutcome
  │   ├─ pending = computePending(manifest)
  │   ├─ if pending.isEmpty() → finishCompleted (no traversal)
  │   └─ else → traverse pending steps
  │       └─ for SystemSetting step:
  │           └─ SystemSettingPort.applyOrPrompt(refId)
  │               └─ applyHandlers[ApplySpec::class].apply(spec)
  ├─ runWalkThrough(manifest): WizardOutcome       [NEW]
  │   └─ traverse ALL steps; each with current value + Оставить/Изменить
  └─ diffPending(...)  [DEPRECATED]

WizardManifest
  └─ steps: List<StepEntry>
      ├─ stepType: WireStepType (UIChoice / SystemSetting / TutorialHint / Custom)
      ├─ refId: String
      ├─ params: Map<String, JsonElement>
      ├─ canSkip: Boolean             [per-profile override of pool default]
      └─ criticality: WireCriticality

SystemSettingEntry (v2)
  ├─ id, mechanism, canSkip, criticality, deepLink, etc.  [v1 fields]
  ├─ check: CheckSpec?                                     [NEW v2]
  └─ apply: ApplySpec?                                     [NEW v2]

CheckSpec (sealed)
  ├─ AndroidRole(role)
  ├─ AndroidPermission(permission)
  ├─ AndroidSpecialPermission(variant)
  ├─ AndroidAccessibilityService(componentName)
  └─ AndroidPackageHome(packageName)

ApplySpec (sealed)
  ├─ StandardPermissionRequest(permission)
  ├─ AndroidRoleRequest(role)
  ├─ SettingsDeepLink(action, packageScoped)
  └─ InAppOnly

UserPreferences (modified)
  └─ hasValueFor(refId): Boolean                            [NEW helper]

CustomStep (commonMain)
  └─ CustomStepHandler (per refId)
      └─ PairAdminCustomStepHandler (androidMain)
          └─ launches PairingActivity (spec 007)
```

---

## TL;DR (по-русски, для новичка и для будущего AI)

**Суть.** Новые типы TASK-7: `CheckSpec` / `ApplySpec` (sealed классы с `@JsonClassDiscriminator("kind")`) для declarative dispatch вместо hardcoded `when(settingId)`. Порты `CheckHandler` / `ApplyHandler` per variant. На F-3 типах модификации: `WizardEngine` получает `computePending` + `runWalkThrough`, `UserPreferences` получает helper `hasValueFor(refId)`, `SystemSettingEntry` получает optional `check`/`apply` блоки (schemaVersion 2).

**Конкретика, которую стоит запомнить:**
- **5 `CheckSpec` variants** для Android: AndroidRole, AndroidPermission, AndroidSpecialPermission, AndroidAccessibilityService, AndroidPackageHome. Все в commonMain (Article VII §15 multi-platform seam).
- **4 `ApplySpec` variants**: StandardPermissionRequest, AndroidRoleRequest, SettingsDeepLink, InAppOnly.
- **5 + 4 Android handlers** в androidMain, registered через Koin как `Map<KClass<out CheckSpec>, CheckHandler>` и аналогичный ApplyMap.
- **Cache** — `Map<settingId, Pair<SettingStatus, Instant>>` с TTL 30s, invalidate-on-resume через `CacheInvalidatingLifecycleObserver`.
- **`CustomStep`** в commonMain dispatches `Custom` step type по `refId` через map handlers; `PairAdminCustomStepHandler` в androidMain launches `PairingActivity` (spec 007).
- **`PendingChecklistViewModel` и `LocaleDivergenceViewModel`** для Settings UI — load engine.computePending() и compare locale tags.

**На что смотреть с осторожностью:**
- **`UserPreferences.hasValueFor(refId)` помощник имеет hardcoded ключи** (theme/fontScale/language) — для расширения за пределы этих trios нужна более общая map. Inline TODO в коде про generalization.
- **`PairAdminCustomStepHandler` использует `awaitPairingActivityResult()`** — это псевдо-API; реальная wiring через ActivityResultRegistry / startActivityForResult — будет в plan Phase 5.
- **`SettingStatusCache` simple synchronization** — `MutableMap` не thread-safe; если будем читать из background coroutine, нужна protection. Сейчас все calls с main coroutine context.
- **Backward-compat v1 read** через legacy dispatch path — этот path **должен быть удалён** после migration всех bundled JSON entries в v2. Inline TODO в adapter.
