# Port Contract: Provider + ProviderRegistry + Outcome

**Location**: `core/preset/port/Provider.kt`, `core/preset/port/ProviderRegistry.kt`, `core/preset/model/Outcome.kt`.
**Domain-side**: fully in `commonMain` (pure Kotlin).
**Implementations**: `app/androidMain/provider/*` (Android SDK-touching), `core/preset/adapter/NoOpProvider.kt` (domain default).

---

## Provider port

```kotlin
package com.launcher.preset.port

import com.launcher.preset.model.Component
import com.launcher.preset.model.Outcome
import com.launcher.preset.model.Profile

interface Provider<T : Component> {
    /**
     * Check whether desired state matches current runtime.
     * @return Ok if matches, NeedsApply if drift, Failed on structural error,
     *         Unsupported if platform/vendor cannot handle this Component.
     */
    suspend fun check(component: T, profile: Profile): Outcome

    /**
     * Bring runtime state to match desired.
     * MUST NOT contain persistent background loops — only configure (via WorkManager /
     * AlarmManager / geofencing / etc.) and return.
     * @return Ok on success, Failed with FailReason category on failure,
     *         Unsupported if platform gap.
     */
    suspend fun apply(component: T, profile: Profile): Outcome

    // TODO(capability-registry): check()/apply() will be exposed as domain-verbs through
    // future Capability Registry (F-2). Each Provider implementation becomes an exposure
    // point. Provider-side rollback (`suspend fun rollback(component, profile): Outcome
    // = Outcome.Unsupported`) — additive extension when needed per FR-029.

    // Runtime capability checks — through CapabilityQuery port, NOT direct Profile.state access.
}
```

## Outcome (see data-model.md §6)

```kotlin
sealed class Outcome {
    object Ok : Outcome()
    object NeedsApply : Outcome()
    data class Failed(val reason: FailReason) : Outcome()
    object Unsupported : Outcome()
}
```

## ProviderRegistry port

```kotlin
package com.launcher.preset.port

import com.launcher.preset.model.Component
import com.launcher.preset.model.HandlerKey
import com.launcher.preset.model.Vendor
import kotlin.reflect.KClass

interface ProviderRegistry {
    /**
     * Resolve a Provider for the given Component instance.
     * Fallback order: (type, platform, vendor) → (type, platform, null) →
     *                 (type, null, null) → NoOpProvider (returns Unsupported).
     */
    fun resolve(component: Component): Provider<Component>
}
```

## Fallback semantics

Given `component: Component` with runtime platform=`Android` and vendor=`Xiaomi`:

1. Try `HandlerKey(component::class, "Android", Vendor.Xiaomi)` → if found, return.
2. Try `HandlerKey(component::class, "Android", null)` → if found, return.
3. Try `HandlerKey(component::class, null, null)` → if found, return.
4. Return `NoOpProvider` (returns `Outcome.Unsupported` for all methods).

## Peripheral-vendor nested pattern (FR-015)

When a single Component (e.g. hypothetical future `BloodPressureDevice`) has multiple vendor-implementations (Omron, A&D):

- **Anti-pattern**: `when (component.vendorApp) { "omron" -> ... }` inside single Provider — forbidden by fitness function.
- **Right pattern**: Provider is thin shell; delegates to `PeripheralAdapterRegistry` (secondary DI-driven registry mirroring ProviderRegistry). Vendor-specific adapters registered via DI, resolved by `component.vendorApp` parameter.

This is NOT in MVP scope but pattern is declared here for downstream tasks introducing peripheral Components.

## Provider implementation checklist (per Component subtype)

For MVP wave (4 subtypes), each Provider in `app/androidMain/provider/`:

- [ ] `check()` returns `Ok` when runtime matches, `NeedsApply` when drift, `Unsupported` if platform gap.
- [ ] `apply()` mutates runtime (via facade), returns `Ok` on success, `Failed(reason)` on failure.
- [ ] All Android SDK access via a facade (rule 1 domain isolation).
- [ ] Runtime capability checks (e.g. HealthForward querying `CloudSession`) via `CapabilityQuery`, NOT direct DataStore read.
- [ ] Unit test: fake facade, test both `check` states + `apply` success + `apply` each `FailReason` category.
- [ ] Registered in `HandlerModule` via `@IntoMap @ComponentKey(Component.Foo::class)`.

## NoOpProvider

Default fallback in `core/preset/adapter/NoOpProvider.kt`:

```kotlin
object NoOpProvider : Provider<Component> {
    override suspend fun check(component: Component, profile: Profile): Outcome = Outcome.Unsupported
    override suspend fun apply(component: Component, profile: Profile): Outcome = Outcome.Unsupported
}
```

Returned by `ProviderRegistry.resolve` when no adapter found for `(type, platform, vendor)` fallback chain.

## Test contract

- Every sealed subtype of `Component` MUST have a registered Provider in the test DI graph (fitness #3 coverage). Orphan subtype → build fail.
- Provider isolation: `provider/foo/` MUST NOT import `provider/bar/` (fitness #6, straps future SosDispatcher pattern).
- Engine tests use `FakeProvider<T>` from `commonTest/fakes` for deterministic Outcome.

## Language-neutral note

Provider port is domain-only. No Android type appears in signatures. Kotlin `suspend fun` + coroutine cancellation is the concurrency contract; adapters bridge to Android coroutine scopes (viewModelScope, WorkManager, etc.) in their own code.
