# Port Contract: CapabilityFlag / CapabilityQuery / CapabilityContract + PresetValidator

**Location**: `core/preset/model/CapabilityFlag.kt`, `core/preset/port/CapabilityQuery.kt`, `core/preset/port/CapabilityContract.kt`, `core/preset/engine/PresetValidator.kt`.
**Introduced by**: FR-027 (session 2.5.5 clarify Q6 owner reframe — cloud is runtime state, not preset field).

---

## Rationale

Owner directive: `cloudRequirement` field in JSON = tight coupling via wire format. Prefer abstraction — Component's Provider queries an abstract capability port at runtime; the port has one adapter reading from wherever cloud state actually lives (Profile.state opaque holder for MVP). Changes to storage do NOT propagate to Components.

Additionally, admin-visible preset validation catches malformed ordering (e.g. `HealthForward` before `SignInGoogle`) **before** Wizard starts — via metadata contract, without runtime execution.

## CapabilityFlag (sealed hierarchy)

```kotlin
package com.launcher.preset.model

sealed class CapabilityFlag {
    /** Cloud authentication session active (Google / Yandex / custom). */
    object CloudSession : CapabilityFlag()
    // Future additive: PairedWithAdmin, ContactsGranted, HealthConnectAccessGranted, ...
}
```

MVP scope: **one flag** (`CloudSession`). All future flags added additively.

## CapabilityQuery port

```kotlin
package com.launcher.preset.port

import com.launcher.preset.model.CapabilityFlag

interface CapabilityQuery {
    /** Is the flag currently active on this device? Runtime query. */
    suspend fun isActive(flag: CapabilityFlag): Boolean

    /** Mark flag active with implementation-specific evidence (token, hash, etc.). */
    suspend fun markActive(flag: CapabilityFlag, evidence: Evidence)

    /** Mark flag inactive (e.g. user signs out). Idempotent. */
    suspend fun markInactive(flag: CapabilityFlag)

    /**
     * Evidence type — opaque holder. Adapter defines what evidence is stored.
     * Domain code only passes / receives; never inspects.
     */
    sealed class Evidence {
        data class Token(val value: String, val expiresAt: Long?) : Evidence()
        data class Hash(val sha256: String) : Evidence()
        object Marker : Evidence()   // presence-only (no data), for boolean flags
    }
}
```

MVP adapter: `DataStoreCapabilityAdapter` in `app/androidMain/capability/`. Reads/writes `Profile.state.opaque` fields via ProfileStore.

Runtime write flow:
- `SignInGoogleProvider.apply()` (future, draft-1) → performs Google Sign-In → `capabilityQuery.markActive(CloudSession, Token(googleIdToken, expiresAt))`.
- `HealthForwardProvider.check()` (future) → `capabilityQuery.isActive(CloudSession)` → if false, returns `Outcome.Unsupported` (or `Failed(NetworkUnavailable)` if online-required).

## CapabilityContract port

```kotlin
package com.launcher.preset.port

import com.launcher.preset.model.CapabilityFlag
import com.launcher.preset.model.Component
import kotlin.reflect.KClass

interface CapabilityContract {
    /**
     * What CapabilityFlags does this Component subtype REQUIRE to be active
     * before apply() can succeed?
     */
    fun requires(componentType: KClass<out Component>): Set<CapabilityFlag>

    /**
     * What CapabilityFlags does this Component subtype PROVIDE after apply() succeeds?
     * Runtime effect happens via CapabilityQuery.markActive() inside the Provider,
     * but metadata declared here for pre-Wizard validation.
     */
    fun provides(componentType: KClass<out Component>): Set<CapabilityFlag>
}
```

Registered in DI (Hilt module) alongside each Component's Provider:

```kotlin
@Provides @IntoMap @CapabilityContractKey(Component.SignInGoogle::class)
fun signInProvides() = mapOf(
    "requires" to emptySet<CapabilityFlag>(),
    "provides" to setOf(CapabilityFlag.CloudSession),
)
```

**MVP behavior**: no Component in MVP wave requires or provides CloudSession (SignInGoogle deferred to draft-1). So `CapabilityContract` returns empty sets for all MVP subtypes. Test coverage uses fake Components to exercise the mechanism.

## PresetValidator

```kotlin
package com.launcher.preset.engine

import com.launcher.preset.model.CapabilityFlag
import com.launcher.preset.model.Preset
import com.launcher.preset.model.Pool
import com.launcher.preset.model.ValidationError
import com.launcher.preset.port.CapabilityContract

class PresetValidator(
    private val contract: CapabilityContract,
    private val supportedSchemaVersion: Int = 2,
) {
    /**
     * Validate preset before Wizard start.
     * @return empty list = valid. Non-empty = Wizard MUST NOT start.
     */
    fun validate(preset: Preset, pool: Pool): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()

        // 1. schemaVersion
        if (preset.schemaVersion > supportedSchemaVersion) {
            errors += ValidationError.SchemaVersionUnsupported(preset.schemaVersion, supportedSchemaVersion)
            return errors                            // fail-fast on unsupported schema
        }

        // 2. UnknownPoolRef in all three lists
        (preset.wizardFlow.map { it.poolRef } +
         preset.settingsMap.map { it.poolRef } +
         preset.activeComponents.map { it.poolRef })
            .distinct()
            .forEach { ref ->
                if (pool.byId(ref) == null) errors += ValidationError.UnknownPoolRef(ref)
            }

        // 3. CapabilityMissing — walk wizardFlow ordered by `order`
        val available = mutableSetOf<CapabilityFlag>()
        preset.wizardFlow.sortedBy { it.order }.forEach { entry ->
            val decl = pool.byId(entry.poolRef) ?: return@forEach   // UnknownPoolRef already reported
            val kclass = decl.component::class
            val requires = contract.requires(kclass)
            val missing = requires - available
            if (missing.isNotEmpty()) {
                errors += ValidationError.CapabilityMissing(entry.poolRef, missing)
            }
            available += contract.provides(kclass)
        }

        return errors
    }
}
```

## PresetValidator invocation

- Called from `WizardViewModel.onStartPreset()` BEFORE `ReconcileEngine.run(RunMode.Wizard)`.
- On non-empty error list: emit UI state with localized error messages (via `LocalizedResources.resolve(err.toI18nKey())`); Wizard does NOT start; user sees dialog with clear action.
- On empty list: proceed to `ProfileFactory.create()` → `engine.run()`.

## ValidationError (see data-model.md §9)

```kotlin
sealed class ValidationError {
    data class CapabilityMissing(val componentId: String, val missing: Set<CapabilityFlag>) : ValidationError()
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

## Test contract (SC-014, fitness #9)

Three canonical scenarios:

```kotlin
class PresetValidatorTest {
    private val fakeContract = FakeCapabilityContract(
        requires = mapOf(
            FakeCloudConsumer::class to setOf(CapabilityFlag.CloudSession),
        ),
        provides = mapOf(
            FakeSignIn::class to setOf(CapabilityFlag.CloudSession),
        ),
    )
    private val validator = PresetValidator(fakeContract, supportedSchemaVersion = 2)
    private val pool = fakePool(FakeSignIn(...), FakeCloudConsumer(...), FontSize(1.6f))

    @Test fun validOrdering_SignInBeforeConsumer_returnsEmpty() {
        val preset = presetWith(wizardFlow = listOf(fontStep, signInStep, consumerStep))
        assertTrue(validator.validate(preset, pool).isEmpty())
    }

    @Test fun malformedOrdering_ConsumerBeforeSignIn_returnsCapabilityMissing() {
        val preset = presetWith(wizardFlow = listOf(fontStep, consumerStep, signInStep))
        val errors = validator.validate(preset, pool)
        assertEquals(1, errors.size)
        assertIs<ValidationError.CapabilityMissing>(errors[0])
        assertEquals(setOf(CapabilityFlag.CloudSession), (errors[0] as ValidationError.CapabilityMissing).missing)
    }

    @Test fun optionalPath_ComponentWithNoRequires_notBlocked() {
        val preset = presetWith(wizardFlow = listOf(fontStep))
        assertTrue(validator.validate(preset, pool).isEmpty())
    }

    @Test fun unknownPoolRef_returnsError() { ... }
    @Test fun schemaVersionTooHigh_returnsError() { ... }
}
```

Fake Components (`FakeSignIn`, `FakeCloudConsumer`) live in `commonTest/fakes/` since MVP wave does NOT include SignInGoogle. Real Component with `provides = {CloudSession}` lands in draft-1.

## Loose-coupling verification

Verify by grep at CI time (fitness function):

- `HealthForwardProvider.kt` (future) MUST NOT contain `Profile.state`, `DataStore`, `SharedPreferences`, or any capability-storage class name.
- Only allowed access to capability state: through `CapabilityQuery` port.

## Extension seams (future)

- New CapabilityFlag: add to sealed hierarchy. Additive. No wire format change.
- Different storage for cloud evidence: swap `DataStoreCapabilityAdapter` for `KeystoreCapabilityAdapter` in DI. No Provider changes.
- Cross-device capability (e.g. `PairedWithAdmin` via cloud sync): CapabilityQuery adapter reads sync state; wire format for admin-visible pairing lives in separate contract (TASK-67 pairing spec).
