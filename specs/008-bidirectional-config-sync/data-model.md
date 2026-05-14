# Data Model: Spec 008 — Bidirectional Config Sync

**Date**: 2026-05-14
**Spec**: [spec.md](spec.md)
**Plan**: [plan.md](plan.md)
**Contracts**: [contracts/config.md](contracts/config.md), [contracts/state-applied.md](contracts/state-applied.md)

Domain types introduced by spec 008. Living in `core/src/commonMain/kotlin/com/launcher/api/config/`.

---

## Domain entities (commonMain)

### `ConfigDocument`

```kotlin
@Serializable
data class ConfigDocument(
    val schemaVersion: Int = SCHEMA_VERSION,
    val serverUpdatedAt: ServerTimestamp,  // domain wrapper around Long epoch millis
    val lastWriterDeviceId: DeviceId,
    val presetId: PresetId,
    val flows: List<Flow>,
    val contacts: List<Contact>,
) {
    companion object {
        const val SCHEMA_VERSION: Int = 1
    }
}
```

Maps 1:1 to [contracts/config.md](contracts/config.md). `ServerTimestamp` is a domain value wrapping Firestore's `Timestamp` to avoid leaking vendor type.

### `Flow`

```kotlin
@Serializable
data class Flow(
    val id: ElementId,
    val title: String,
    val slots: List<Slot>,
)
```

### `Slot`

```kotlin
@Serializable
data class Slot(
    val id: ElementId,
    val kind: SlotKind,            // sealed enum: Call, Sms, OpenApp, ... (closed set)
    val args: JsonObject? = null,  // kind-specific params; opaque to diff
)
```

### `Contact`

```kotlin
@Serializable
data class Contact(
    val id: ElementId,
    val displayName: String,
    val phoneNumber: String,
    val photoRef: String? = null,  // reserved for spec 011 (private:<uuid>)
)
```

### `ElementId`

```kotlin
@JvmInline @Serializable
value class ElementId(val value: String) {
    init { require(isUuidV4(value)) { "ElementId must be UUID v4" } }
    companion object {
        @OptIn(ExperimentalUuidApi::class)
        fun random(): ElementId = ElementId(Uuid.random().toString())
    }
}
```

Wraps UUID v4 string. Validation at construction prevents bad ids from sneaking in (per research.md §2). Pure-Kotlin via stdlib `kotlin.uuid.Uuid`.

### `DeviceId`

```kotlin
@JvmInline @Serializable
value class DeviceId(val value: String)  // UUIDv4 device pseudonym; persisted in DataStore
```

Reused from spec 007 (`identity/DeviceIdProvider`). Spec 008 adds no new identity infrastructure.

### `PresetId`

```kotlin
@JvmInline @Serializable
value class PresetId(val value: String)  // e.g., "simple-launcher", "medium-launcher"
```

Closed set of known presets (defined elsewhere — spec 003 or 010).

### `ServerTimestamp`

```kotlin
@Serializable
data class ServerTimestamp(val epochSeconds: Long, val nanoseconds: Int) {
    operator fun compareTo(other: ServerTimestamp): Int = ...
}
```

Domain abstraction over Firestore `Timestamp`. Adapter (`FirebaseRemoteSyncBackend`) maps from `com.google.firebase.Timestamp` ↔ `ServerTimestamp`.

---

## State document (`/state/current` extended)

### `StateApplied`

```kotlin
@Serializable
data class StateApplied(
    val schemaVersion: Int = SCHEMA_VERSION,  // 1, inherited from spec 007
    val appliedAt: ServerTimestamp,
    val presetId: PresetId,
    val fcmToken: String? = null,
    val updatedAt: ServerTimestamp,
    // Spec 008 extension (additive — FR-032):
    val appliedConfigUpdatedAt: ServerTimestamp? = null,
    val flowsApplied: List<FlowApplied>? = null,
    val contactsApplied: List<ContactApplied>? = null,
    val partialApplyReasons: List<PartialReason> = emptyList(),
) {
    companion object { const val SCHEMA_VERSION: Int = 1 }
}
```

### `FlowApplied`, `SlotApplied`, `ContactApplied`

```kotlin
@Serializable
data class FlowApplied(
    val id: ElementId,
    val title: String,
    val slots: List<SlotApplied>,
)

@Serializable
data class SlotApplied(
    val id: ElementId,
    val kind: SlotKind,
    val appliedSuccessfully: Boolean,
)

@Serializable
data class ContactApplied(
    val id: ElementId,
    val displayName: String,
    val appliedSuccessfully: Boolean,
)
```

### `PartialReason`

```kotlin
@Serializable
enum class PartialReason {
    PROVIDER_UNAVAILABLE,
    CONTACT_PERMISSION_DENIED,
    MEDIA_DECRYPT_FAILED,    // reserved for spec 011
    UNKNOWN_SLOT_KIND,
}
```

**Enum keys, not strings** (per ux-quality CHK011 — localization-safe). Client maps enum → user-visible Russian text in UI layer.

---

## Diff/merge types

### `ConfigDiff`

```kotlin
data class ConfigDiff(
    val addedFlows: List<Flow>,
    val removedFlows: List<ElementId>,
    val modifiedFlows: List<ModifiedFlow>,
    val addedSlots: List<Pair<ElementId, Slot>>,         // parentFlowId, slot
    val removedSlots: List<Pair<ElementId, ElementId>>,  // parentFlowId, slotId
    val modifiedSlots: List<ModifiedSlot>,
    val addedContacts: List<Contact>,
    val removedContacts: List<ElementId>,
    val modifiedContacts: List<ModifiedContact>,
) {
    val isEmpty: Boolean = ...
    val hasOverlappingChanges: Boolean = ...  // both editors touched same id

    companion object {
        /** Pure function — input: two ConfigDocuments, output: diff. No I/O. */
        fun compute(local: ConfigDocument, server: ConfigDocument): ConfigDiff = ...
    }
}
```

### `ModifiedFlow`, `ModifiedSlot`, `ModifiedContact`

```kotlin
data class ModifiedFlow(
    val id: ElementId,
    val localTitle: String?,    // null if unchanged
    val serverTitle: String?,
    // ...
)
// Similar for Slot, Contact
```

---

## Persistence (Room — androidMain, never leaked to commonMain)

### `LocalAppliedConfig`

```kotlin
@Entity(tableName = "applied_config")
data class LocalAppliedConfigEntity(
    @PrimaryKey val linkId: String,
    val configJson: String,          // serialized ConfigDocument (kotlinx.serialization)
    val appliedAt: Long,             // epoch millis
    val schemaVersion: Int,
)
```

Adapter (`RoomLocalConfigStore`) deserializes JSON ↔ commonMain `ConfigDocument`. Entity never crosses adapter boundary (Konsist-enforced).

### `PendingLocalChanges`

```kotlin
@Entity(tableName = "pending_changes")
data class PendingLocalChangesEntity(
    @PrimaryKey val linkId: String,
    val snapshotServerUpdatedAt: Long,  // serverUpdatedAt at start of editing session
    val draftJson: String,              // serialized current draft ConfigDocument
    val updatedAt: Long,                // last edit timestamp (for diagnostics)
)
```

Upserted on every autosave (FR-056, debounced 300ms). Deleted on successful push (after RemoteSyncBackend transaction commits).

---

## Error model

### `ConfigSyncError` (sealed; extends spec 007's `BackendError`)

```kotlin
// commonMain
sealed interface ConfigSyncError {
    data class Conflict(val localDiff: ConfigDiff, val serverConfig: ConfigDocument) : ConfigSyncError
    data class BackendFailure(val cause: BackendError) : ConfigSyncError
    data class ApplyPartial(val reasons: List<PartialReason>) : ConfigSyncError
    data class LocalStorageCorrupt(val cause: Throwable) : ConfigSyncError
}
```

**Categorical (per failure-recovery CHK017)** — not unique error message strings. Enables rate metrics.

---

## Identifier mapping

`linkId`, `adminId`, `managedDeviceFirebaseUid` — все inherited from spec 007. Spec 008 introduces no new identity primitives.

---

## Persistence schema versioning (Room)

```kotlin
@Database(
    entities = [LocalAppliedConfigEntity::class, PendingLocalChangesEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class ConfigSyncDatabase : RoomDatabase() {
    abstract fun configDao(): ConfigDocumentDao
}
```

- Initial version: 1.
- Future bumps: Room `Migration` classes; tests required per wire-format CHK014.
- Schema export (`exportSchema = true`) → `core/schemas/com.launcher.adapters.ConfigSyncDatabase/1.json` — committed to git for migration diffing.

---

## Lifecycle ports (commonMain)

### `NetworkAvailability`

```kotlin
interface NetworkAvailability {
    /** Hot Flow emitting Unit each time network becomes available (transition offline → online). */
    val onAvailable: Flow<Unit>
}
```

Real adapter (`ConnectivityManagerNetworkAvailability`): registers `ConnectivityManager.NetworkCallback` lazily on first subscription; unregisters when no subscribers.

### `AppForegroundEvents`

```kotlin
interface AppForegroundEvents {
    /** Hot Flow emitting Unit on Activity#onResume of any launcher Activity, throttled per FR-022 T4 (2min). */
    val onResume: Flow<Unit>
}
```

Real adapter: subscribes to `ProcessLifecycleOwner.lifecycle`, debounces to per-2-minutes.

---

## Domain ports inventory (commonMain)

| Port | Purpose | Real adapter | Fake adapter |
|---|---|---|---|
| `ConfigApplier` | Apply ConfigDocument to UI/storage on Managed (FR-021..023) | `FirebaseConfigApplier` (androidMain) | `FakeConfigApplier` (commonTest) |
| `ConfigEditor` | Save локально + push с conflict check (FR-040, FR-056, FR-013) | `DefaultConfigEditor` (androidMain — uses RemoteSyncBackend) | `FakeConfigEditor` (commonTest) |
| `LocalConfigStore` | Room-backed persistence (FR-041, FR-042) | `RoomLocalConfigStore` (androidMain) | `FakeLocalConfigStore` (commonTest — in-memory map) |
| `NetworkAvailability` | OS-driven network events | `ConnectivityManagerNetworkAvailability` | `FakeNetworkAvailability` |
| `AppForegroundEvents` | Lifecycle-driven launcher visibility | `ProcessLifecycleForegroundEvents` | `FakeAppForegroundEvents` |

**Reused from spec 007** (no new code):
- `RemoteSyncBackend` (Firestore I/O)
- `PushReceiver`, `PushSender` (FCM)
- `IdentityProvider`, `DeviceIdProvider` (uid + deviceId)
- `LinkRegistry` (which links current user is a member of)

---

## Constants

```kotlin
// commonMain
object ConfigSyncConstants {
    const val AUTOSAVE_DEBOUNCE_MS: Long = 300                           // FR-056
    const val PUSH_NO_NETWORK_WARNING_DELAY_MS: Long = 5_000             // SC-001 / FR-015
    const val POST_STARTUP_FETCH_DELAY_MS: Long = 5_000                  // SC-004b
    const val RESUMED_TRIGGER_THROTTLE_MS: Long = 2 * 60_000             // FR-022 T4 (2 min)
    const val WORKMANAGER_POLL_INTERVAL_MIN: Long = 15                   // FR-022 T3
    const val PUSH_BUTTON_DEBOUNCE_MS: Long = 500                        // ux-quality CHK011
}
```

Single source of truth — no magic numbers scattered.

---

<!-- novice summary -->

## TL;DR

Список «вещей» (data classes), которые программа знает в коде. Главная — `ConfigDocument` (это то, что лежит на сервере: какие плитки, контакты). У каждой плитки и контакта — свой `ElementId` (случайный UUID), чтобы при сравнении версий понимать, что «плитка Маша» это та же плитка, даже если её переименовали. Когда есть конфликт — собираем `ConfigDiff` (где что разное между моей версией и сервером). Локально на телефоне хранится в Room: `LocalAppliedConfig` (что реально показано) и `PendingLocalChanges` (что я наредактировал, но ещё не отправил).
