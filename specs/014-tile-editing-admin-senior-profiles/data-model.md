# Data Model: F-014.0

## Scope

Только F-014.0 phase (local-only). Domain types в `core/commonMain/kotlin/com/launcher/api/edit/`.

ConfigDocument (existing спека 008) — не описывается здесь, **не меняется** в F-014.0.

---

## 1. `EditUiProfile` (sealed class)

```kotlin
sealed class EditUiProfile {
    object AdminProfile : EditUiProfile()
    object SeniorProfile : EditUiProfile()
}
```

**Назначение**: UX rules selector. Включает derivable affordances:
- `AdminProfile` — picker shows 5 tabs (App/Contact/Doc/Widget/Action), undo via Material 3 snackbar 8s, conflict resolution via [Обновить]/[Перезаписать] dialog.
- `SeniorProfile` — picker shows 3 tabs (App/Contact/Doc — Widget/Action hidden per FR-019), undo via same snackbar 8s (edit mode UX universal per FR-012), conflict silent last-local-write-wins per Q7.

**Note**: Profile НЕ влияет на edit-mode UX (per Q4 cancellation) — только на:
- Picker tab visibility (FR-019).
- Conflict resolution UX (FR-016 split).
- Use-mode rendering (FR-021).

---

## 2. `TargetIdentity` (data class)

```kotlin
data class TargetIdentity(
    val linkId: String,        // pairing link ID (existing спека 007), self if "local"
    val presetId: String,      // "workspace" | "simple-launcher" | other built-in
    val isSelf: Boolean        // derived: linkId == "local"
)
```

**Назначение**: who/what is being edited. Used by `EditUiProfileSelector` and presentation для frame/banner decision (FR-014/FR-015).

**Invariants**:
- `linkId.isNotBlank()`.
- `presetId.isNotBlank()`.
- `isSelf == (linkId == "local")` — enforced in factory.

---

## 3. `EditMode` (data class)

```kotlin
data class EditMode(
    val active: Boolean,
    val target: TargetIdentity,
    val profile: EditUiProfile  // derived via EditUiProfileSelector at construction
)
```

**Назначение**: presentation state. UI-scoped (per state-management.md CHK005). Survives Activity recreation via Decompose state preservation.

**Lifecycle**:
- Enter: `EditMode(active=true, target=…, profile=selector(target.presetId))`.
- Exit: `active=false` (or component disposed).
- Process death: not persisted; on restart admin/senior возвращается в use mode (mainstream behavior).

---

## 4. `TileEditOperation` (sealed class)

```kotlin
sealed class TileEditOperation {
    data class Add(val flowId: String, val slot: Slot) : TileEditOperation()
    data class Move(val flowId: String, val slotId: String, val newPosition: Int) : TileEditOperation()
    data class Remove(val flowId: String, val slotId: String) : TileEditOperation()
    data class Replace(val flowId: String, val slotId: String, val newSlot: Slot) : TileEditOperation()
}
```

**Назначение**: domain operation на ConfigDocument.Flow.slots[]. Applied via `TileEditOperations.apply(op, config): Outcome<ConfigDocument, EditError>`.

**Idempotency**: каждая op с тем же slotId безопасна для retry (ConfigEditor.pushPending выполняет dedup per спека 008).

---

## 5. `EditError` (sealed class)

```kotlin
sealed class EditError {
    object InvalidPosition : EditError()
    data class SlotNotFound(val slotId: String) : EditError()
    data class FlowNotFound(val flowId: String) : EditError()
    object ConcurrentEditConflict : EditError()
    object NotAuthorized : EditError()
    object ProfileSelectionRequiresCapabilityRegistry : EditError()  // FR-008b, Q8
}
```

**Назначение**: domain error variants. Sealed для exhaustive `when` в presentation.

Per failure-recovery.md CHK001 — каждый variant имеет presentation handling (snackbar / dialog / placeholder screen).

---

## 6. `PickerType` (enum)

```kotlin
enum class PickerType { Application, Contact, Document, Widget, Action }
```

**Назначение**: tile types в unified picker. Filter:
- `Workspace target → all 5`.
- `Simple Launcher target → [Application, Contact, Document]` per FR-019.
- `Widget, Action` — open "В разработке" placeholder screen per FR-018 (no functional impl в F-014.0).

---

## 7. `NamedConfig` (data class) — F-014.0 LOCAL

```kotlin
@Serializable
data class NamedConfig(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,  // = 1 в F-014.0
    val configName: String,            // user label, NFC-normalized, 1..32 chars
    val description: String = "",
    val isDefault: Boolean = false,    // FR-003a invariant: exactly one true per admin
    val presetId: String,              // compatibility key
    val deviceClass: String,           // compatibility key (e.g. "phone")
    val activeDeviceIds: Set<String> = setOf(thisDeviceId),  // F-014.0: only thisDeviceId
    val orphanedAt: Instant? = null    // FR-003b: lifecycle marker
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        const val MAX_CONFIG_NAME_LENGTH = 32
        const val MAX_DESCRIPTION_LENGTH = 200
    }
}
```

**Invariants** (enforced at NamedConfigsLocalStore boundary):
- `configName.length in 1..MAX_CONFIG_NAME_LENGTH`.
- `configName` matches `^[\p{L}\p{N} -]+$` (NFC normalized), case-insensitive uniqueness per admin.
- `description.length <= MAX_DESCRIPTION_LENGTH`.
- Exactly one `isDefault=true` across all NamedConfigs per admin (FR-003a — enforced atomically).
- `activeDeviceIds.size >= 1` OR `orphanedAt != null` (lifecycle invariant per FR-003).
- Max 5 NamedConfigs per admin (FR-003c).

**F-014.0 simplification**: только `thisDeviceId` в `activeDeviceIds` (single-device). Multi-device sync — F-014.1.

---

## 8. `NamedConfigsLocalStore` (port)

```kotlin
interface NamedConfigsLocalStore {
    val configs: Flow<List<NamedConfig>>
    suspend fun create(config: NamedConfig): Outcome<Unit, StoreError>
    suspend fun update(configName: String, transform: (NamedConfig) -> NamedConfig): Outcome<Unit, StoreError>
    suspend fun markDefault(configName: String): Outcome<Unit, StoreError>  // atomic
    suspend fun applyToCurrentDevice(configName: String): Outcome<Unit, StoreError>
    suspend fun removeFromCurrentDevice(configName: String): Outcome<Unit, StoreError>
}

sealed class StoreError {
    object LimitReached : StoreError()              // 5/5
    data class InvalidName(val reason: String) : StoreError()
    data class NameAlreadyExists(val name: String) : StoreError()
    object NotFound : StoreError()
    object DefaultMustExist : StoreError()          // attempt to remove last default
}
```

**Adapters**:
- `DataStoreNamedConfigsLocalStore` (real, F-014.0) — DataStore Preferences key `f014.named_configs.v1`.
- `FakeNamedConfigsLocalStore` (tests) — in-memory `MutableStateFlow<List<NamedConfig>>`.

**Future F-014.1**: add `RemoteNamedConfigsStore` parallel port для Firestore. Local + remote merged at use site.

---

## 9. Relationships

```
EditMode
  ├─ target: TargetIdentity ─── presetId ──► EditUiProfileSelector ──► profile: EditUiProfile
  └─ presentation derived from profile

User action ──► TileEditOperation ──► TileEditOperations.apply()
                                          │
                                          ├─ Success ──► ConfigEditor.updateDraft()
                                          └─ EditError ──► presentation handler

NamedConfigsLocalStore (F-014.0)
  └─ stores List<NamedConfig> separately from ConfigDocument
       └─ ConfigDocument lives in ConfigEditor.pendingDraft / appliedConfig (спека 008)
       └─ F-014.0: NamedConfig.isDefault=true config's ConfigDocument is the active one
```

---

## 10. F-014.0 vs F-014.1 split

| Concept | F-014.0 (this plan) | F-014.1 (deferred) |
|---|---|---|
| NamedConfig persistence | DataStore local | Firestore `/admin-self-configs/{adminUid}/configs/{configName}/current` |
| Schema | NamedConfig v1, ConfigDocument v1 (unchanged) | ConfigDocument v2 (adds named-config fields), NamedConfig v2 with multi-device fields |
| Number of configs | Max 5 enforced, but UI shows only 1 (progressive disclosure State 0/1) | Multi-config UI in My Configs screen |
| Default flag | Local only, single device | Atomic Firestore transaction across devices |
| Orphan lifecycle | `orphanedAt` field exists, no auto-delete (own-server prerequisite TODO-FUTURE-SPEC-008) | UI marker only, still no auto-delete |
| Migration anonymous→Google | N/A | FR-003g — explicit user choice dialog |

---

## TL;DR на русском

**8 domain types** в F-014.0:
1. `EditUiProfile` — sealed AdminProfile/SeniorProfile.
2. `TargetIdentity` — кто/что редактируется (linkId, presetId, isSelf).
3. `EditMode` — presentation state (active, target, profile).
4. `TileEditOperation` — sealed Add/Move/Remove/Replace.
5. `EditError` — sealed 6 variants ошибок.
6. `PickerType` — enum 5 tile types.
7. `NamedConfig` — F-014.0 local domain type с invariants (NFC, length, uniqueness, default, 5-limit).
8. `NamedConfigsLocalStore` — port + StoreError sealed class.

**ConfigDocument не меняется** в F-014.0 — schema bump 1→2 откладывается на F-014.1. Это значит F-014.0 можно ship'ить без F-4 (Google Sign-In) и без F-5 (encryption). Multi-device sync, server backup, atomic default flag invariant across devices — всё F-014.1 work.

**Главный invariant**: ровно один NamedConfig имеет `isDefault=true`. В F-014.0 — local enforcement. В F-014.1 — atomic Firestore transaction.
