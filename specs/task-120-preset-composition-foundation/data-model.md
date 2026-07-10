# Data Model: Preset Composition Foundation

**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)
**Date**: 2026-07-10

Domain types in `core/preset/model/`. All KMP commonMain, pure Kotlin, `@Serializable` where JSON-bound.

---

## 1. Component (sealed hierarchy)

```kotlin
package com.launcher.preset.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class Component {

    @Serializable @SerialName("AppTile")
    data class AppTile(
        val packageName: String,
        val labelKey: String,           // i18n key, not literal (FR-026)
        val iconKey: String? = null,
        val pinProtected: Boolean = false,
    ) : Component()

    @Serializable @SerialName("FontSize")
    data class FontSize(
        val scale: Float,               // mutable via paramsOverride
    ) : Component()

    @Serializable @SerialName("Sos")
    data class Sos(
        val shareLocation: Boolean = true,
        val autoAnswer: Boolean = true,
        // NO targetPairingId here — identity is resolved via PairingService port at apply-time.
        // Preset stays identity-free per rule 9 (shareability-readiness).
    ) : Component()

    @Serializable @SerialName("Toolbar")
    data class Toolbar(
        val items: List<String>,        // e.g. ["call", "sos", "clock"]
        val layoutKey: String,          // i18n / layout key
    ) : Component()

    // MessengerTile — deferred to task-121
    // SignInGoogle — introduced by draft-1
    // FR-025 anti-explosion: new subtype only when apply() semantic differs
}
```

**Mutable-field allowlist** (per FR-004 paramsOverride validation, fitness #7):

| Component | Mutable | Immutable |
|---|---|---|
| AppTile | `iconKey`, `pinProtected`, `labelKey` (rare — only if preset wants override) | `packageName` (different pkg = different tile = different pool declaration) |
| FontSize | `scale` | (nothing else) |
| Sos | `shareLocation`, `autoAnswer` | (no identity fields — pairing target resolved via `PairingService` port at apply-time) |
| Toolbar | `items` order, `layoutKey` | (nothing else) |

Mutability declared via companion JSON Schema per Component. Fitness #7 enforces.

---

## 2. ComponentDeclaration (Pool entry)

```kotlin
@Serializable
data class ComponentDeclaration(
    val id: String,                                    // "tile-jitsi", "font-tile", "sos-main"
    val component: Component,                          // polymorphic sealed payload
    val wizardBehavior: WizardBehavior = WizardBehavior.AutoApply,
    val critical: Boolean = false,                     // participates in BootCheck
    val descriptionKey: String? = null,                // i18n key for pool docs
)
```

Wire format field name is `component`, not `step` (per H clarify: naming purge).

---

## 3. Pool (typed catalog)

```kotlin
@Serializable
data class Pool(
    val schemaVersion: Int = 1,                        // rule 5
    val declarations: List<ComponentDeclaration>,
) {
    fun byId(id: String): ComponentDeclaration? =
        declarations.firstOrNull { it.id == id }
}
```

Loaded via `PoolSource.loadPool()` port.

---

## 4. Preset (three-field split)

```kotlin
@Serializable
data class Preset(
    val schemaVersion: Int = 2,                        // rule 5
    val presetId: String,                              // "simple-launcher", "workspace"
    val version: Int,                                  // preset revision, not schema
    val layoutKey: String,                             // "grid-2x3", "tv-grid" — resolved by UI
    val wizardFlow: List<WizardFlowEntry>,
    val settingsMap: List<SettingsMapEntry>,
    val activeComponents: List<ActiveComponentEntry>,
)

@Serializable
data class WizardFlowEntry(
    val poolRef: String,                               // id from Pool
    val order: Int,
    val wizardTitleKey: String,                        // i18n key (FR-026)
    val wizardIntroKey: String? = null,
    val behavior: WizardBehavior,
    val paramsOverride: kotlinx.serialization.json.JsonObject? = null,
    val visibleIf: kotlinx.serialization.json.JsonElement? = null,  // MVP: only {"var":"profile.state.<flag>"}
)

@Serializable
data class SettingsMapEntry(
    val poolRef: String,
    val categoryKey: String,                           // i18n key
    val settingsIcon: String? = null,
    val sensitivity: Sensitivity = Sensitivity.Normal,
    val paramsOverride: kotlinx.serialization.json.JsonObject? = null,
)

@Serializable
enum class Sensitivity { Normal, High, Admin }

@Serializable
data class ActiveComponentEntry(
    val poolRef: String,
    val paramsOverride: kotlinx.serialization.json.JsonObject? = null,
    val status: ComponentStatus = ComponentStatus.Pending,
)
```

---

## 5. Profile (device runtime state)

```kotlin
@Serializable
data class Profile(
    val schemaVersion: Int = 2,                        // rule 5
    val basedOnPreset: String,                         // presetId reference
    val presetVersion: Int,
    val layoutKey: String,
    val components: List<ProfileComponent>,             // NOT `steps`
    val preWizardSnapshot: Profile? = null,             // FR-024, FR-029 (self-reference, single level)
    val snapshotTimestamp: Long? = null,                // epoch millis, for 7-day soft-limit
    val unknownRefs: List<String> = emptyList(),        // poolRefs not found in current app version
    val state: ProfileState = ProfileState(),           // opaque state holder for CapabilityQuery
)

@Serializable
data class ProfileComponent(
    val id: String,                                    // matches ComponentDeclaration.id
    val component: Component,                          // resolved from pool + paramsOverride applied
    val wizardBehavior: WizardBehavior,
    val critical: Boolean,
    val status: ComponentStatus = ComponentStatus.Pending,
)

@Serializable
enum class ComponentStatus { Pending, Applied, Failed, Skipped }

@Serializable
data class ProfileState(
    val opaque: kotlinx.serialization.json.JsonObject = kotlinx.serialization.json.JsonObject(emptyMap()),
    // ProfileState is intentionally opaque holder — CapabilityQuery adapter reads/writes fields inside opaque
    // Fields evolve without wire format schemaVersion bump (adapter contract, not domain contract)
)
```

`preWizardSnapshot` is single-level (no recursion): snapshot's own `preWizardSnapshot` field is always `null`.

---

## 6. Outcome (sealed)

```kotlin
sealed class Outcome {
    object Ok : Outcome()
    object NeedsApply : Outcome()
    data class Failed(val reason: FailReason) : Outcome()
    object Unsupported : Outcome()
}
```

---

## 7. FailReason (sealed, per FR-008 revised)

```kotlin
sealed class FailReason {
    data class PermissionDenied(val permission: String) : FailReason()
    data class PolicyBlocked(val policy: String) : FailReason()
    object NetworkUnavailable : FailReason()
    object Cancelled : FailReason()
    data class InternalError(
        val messageKey: String,                             // i18n key
        val args: Map<String, String> = emptyMap(),
    ) : FailReason()

    object PairingNotEstablished : FailReason()             // Sos and others requiring admin pairing

    fun toI18nKey(): String = when (this) {
        is PermissionDenied -> "outcome.failed.permission_denied"
        is PolicyBlocked -> "outcome.failed.policy_blocked"
        NetworkUnavailable -> "outcome.failed.network_unavailable"
        Cancelled -> "outcome.failed.cancelled"
        PairingNotEstablished -> "outcome.failed.pairing_not_established"
        is InternalError -> messageKey
    }
}
```

---

## 8. CapabilityFlag (sealed, MVP: CloudSession only)

```kotlin
sealed class CapabilityFlag {
    object CloudSession : CapabilityFlag()
    // Future additive: PairedWithAdmin, ContactsGranted, HealthConnectAccessGranted, ...
}
```

---

## 9. ValidationError (sealed, per US 5.5)

```kotlin
sealed class ValidationError {
    data class CapabilityMissing(
        val componentId: String,
        val missing: Set<CapabilityFlag>,
    ) : ValidationError()

    data class UnknownPoolRef(val ref: String) : ValidationError()
    data class SchemaVersionUnsupported(val version: Int, val supported: Int) : ValidationError()
    data class CircularOrdering(val cycle: List<String>) : ValidationError()

    fun toI18nKey(): String = when (this) {
        is CapabilityMissing -> "validator.error.capability_missing"
        is UnknownPoolRef -> "validator.error.unknown_pool_ref"
        is SchemaVersionUnsupported -> "validator.error.schema_version_unsupported"
        is CircularOrdering -> "validator.error.circular_ordering"
    }
}
```

---

## 10. RunMode, WizardBehavior, HandlerKey, Vendor

```kotlin
enum class RunMode { Wizard, BootCheck, Single, RemotePush }
enum class WizardBehavior { Interactive, AutoApply, InitialDefault }
enum class Vendor { Xiaomi, Samsung, Huawei, GoogleTV, GenericAndroid, iOS }

data class HandlerKey(
    val componentType: kotlin.reflect.KClass<out Component>,
    val platform: String? = null,       // null = any platform
    val vendor: Vendor? = null,         // null = any vendor
)
```

---

## 11. ChangeItem (PresetDiff output)

```kotlin
sealed class ChangeItem {
    data class Added(val id: String, val component: Component) : ChangeItem()
    data class Removed(val id: String) : ChangeItem()
    data class ParamsChanged(val id: String, val newComponent: Component) : ChangeItem()
}
```

---

## Serialization notes

- Polymorphic sealed `Component` uses `classDiscriminator = "type"` matching `@SerialName` values.
- `Json { classDiscriminator = "type"; ignoreUnknownKeys = true }` — forward-compat readers won't crash on newer fields.
- `paramsOverride: JsonObject?` remains untyped JSON at the wire; deserialized against Component-specific JSON Schema at runtime by `ProfileFactory`.

## Immutability

- All top-level types are `data class` or `object` (sealed variants).
- Mutation only through explicit `copy()` — engine `mark`/`replaceComponent` helpers use `copy` internally.

## Identity-bound value resolution — dedicated ports

Preset stays identity-free (rule 9). Providers resolve identity-bound values at apply-time via dedicated ports:

```kotlin
package com.launcher.preset.port

/** Resolves pairing target for identity-bound Components (Sos, MessengerTile handoff, admin push). */
interface PairingService {
    suspend fun currentAdmin(): PairingId?
}

@JvmInline
value class PairingId(val opaqueId: String)   // opaque, not derivable from Google sub / phone / email
```

MVP usage: `SosProvider.apply()` calls `pairingService.currentAdmin()`. If null → `Outcome.Failed(FailReason.PairingNotEstablished)` (new FailReason category), Wizard offers a nested pairing step (deferred to TASK-67 pairing spec; foundation just returns Failed).

Future ports (all follow same pattern):
- `ContactsResolver` — resolves human-readable contact identity.
- `SubscriptionEntitlement` — cloud entitlement check.
- `AccessibilityService` — a11y state.

**Rule**: any identity-bound value Provider needs → new port + interface + adapter, NOT new Component field.

## What is NOT in domain

- Android types (`Intent`, `Uri`, `PackageInfo`, `Context`, `PackageManager`) — live in facades in `app/androidMain/`.
- Vendor SDK types (Google Auth token, WhatsApp API, Firebase types) — encapsulated by adapter modules.
- UI types (`Composable`, `LazyListState`, `MutableState`) — live in `app/androidMain/ui/`.
- HTTP / transport types — no server touch in this foundation (FR-028 LOCAL mode).
