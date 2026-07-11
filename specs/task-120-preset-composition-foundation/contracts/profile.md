# Wire Format Contract: profile.json

**Schema version**: 2
**Physical location**: DataStore (`profile.preferences_pb`), NOT filesystem JSON. Serialized via kotlinx.serialization + DataStore Serializer.
**Managed by**: `ProfileStore` port. Android impl uses `DataStoreProfileStore`.
**Language**: English (contract).

---

## Purpose

Device-local snapshot of what's applied. Sole source of truth for `ReconcileEngine.run(RunMode.BootCheck)` and `RunMode.Single`. Reconcile engine reads and writes only this.

## Not shareable

Profile is NOT a shareable artifact (rule 9). It contains device-specific runtime state, `preWizardSnapshot`, `ProfileState` (opaque capability evidence). Sharing is at Preset level, not Profile.

## Schema

```json
{
  "schemaVersion": 2,
  "basedOnPreset": "simple-launcher",
  "presetVersion": 1,
  "layoutKey": "layout.grid.2x3",
  "components": [
    {
      "id": "font-tile",
      "component": {
        "type": "FontSize",
        "scale": 1.8
      },
      "wizardBehavior": "Interactive",
      "critical": false,
      "status": "Applied"
    },
    {
      "id": "sos-main",
      "component": {
        "type": "Sos",
        "targetPairingId": "PAIR-9F72",
        "shareLocation": true,
        "autoAnswer": true
      },
      "wizardBehavior": "Interactive",
      "critical": true,
      "status": "Applied"
    }
  ],
  "preWizardSnapshot": null,
  "snapshotTimestamp": null,
  "unknownRefs": [],
  "state": {
    "opaque": {
      "cloudSession": {
        "activatedAt": 1720614400000,
        "evidenceHash": "sha256:abc..."
      }
    }
  }
}
```

## Field semantics

### Top level

| Field | Type | Required | Notes |
|---|---|---|---|
| `schemaVersion` | Int | yes | Currently 2. |
| `basedOnPreset` | String | yes | `presetId` of source preset. Reference only; preset itself not embedded. |
| `presetVersion` | Int | yes | Snapshot of source preset's `version` at time of ProfileFactory.create. |
| `layoutKey` | String | yes | Frozen at Profile creation; changes require new preset install. |
| `components` | Array | yes | List of `ProfileComponent`. Order matches wizardFlow order or activeComponents order. |
| `preWizardSnapshot` | Profile? | no, default `null` | Nested Profile, single-level (its own `preWizardSnapshot` is always null). |
| `snapshotTimestamp` | Long? | no, default `null` | Epoch millis; used for 7-day soft-limit per FR-029. |
| `unknownRefs` | Array | no, default `[]` | poolRefs that couldn't be resolved (from newer preset on older app). |
| `state` | ProfileState | no, default empty | Opaque state holder for CapabilityQuery (cloud tokens, etc.). |

### ProfileComponent

| Field | Type | Required | Notes |
|---|---|---|---|
| `id` | String | yes | Matches ComponentDeclaration.id from Pool. |
| `component` | Component | yes | Resolved from pool + paramsOverride applied. Full sealed payload. |
| `wizardBehavior` | Enum String | yes | Copied from ComponentDeclaration. |
| `critical` | Boolean | yes | Copied from ComponentDeclaration. |
| `status` | Enum String | yes | `Pending` \| `Applied` \| `Failed` \| `Skipped`. |

### ProfileState

Opaque JSON object. Fields evolve without wire format schemaVersion bump — `ProfileState` is the extension seam for CapabilityQuery adapters. Domain code does NOT parse `state.opaque` fields; only `CapabilityQuery` implementation reads/writes.

Example fields (implementation-managed):
- `cloudSession.activatedAt` — epoch millis of SignIn.
- `cloudSession.evidenceHash` — opaque hash of stored token (secure).
- `pairedWithAdmin.pairingId` — future.
- `contactsGranted.grantedAt` — future.

## Wire format vs storage

- Runtime object: Kotlin data class `Profile` per data-model.md.
- Storage: DataStore Serializer (kotlinx.serialization + protobuf or JSON, TBD in adapter). Wire format contract here is the **canonical shape** whatever the storage encoding.
- Export (future): shareable Profile-derived exports (e.g. anonymized diagnostic bundle) use a **different** wire format (identity-scrubbed).

## Additive-only growth

Same rule 5 discipline as pool/preset.

## Migration (v1 → v2)

TASK-120 introduces schemaVersion=2. Previous (v1) — pre-existing wizard state in different shape (if any). Migration writer in `ProfileStore` implementation.

Migration reference table for future v2 → v3 (when it comes):

| Field | Migration approach |
|---|---|
| Add optional field with default | Automatic — deserializer fills default. |
| Rename field | Migration writer maps old name to new. |
| Restructure `components[i].component` (e.g. new Component subtype) | Automatic — new subtype loads fresh; old subtypes remain. |
| Remove field | Fail loud during read; require pre-migration script before shipping. |

## Roundtrip test (SC-005)

```kotlin
val profile = Profile(
    basedOnPreset = "simple-launcher",
    presetVersion = 1,
    layoutKey = "layout.grid.2x3",
    components = listOf(...),
)
val jsonStr = json.encodeToString(profile)
val restored = json.decodeFromString<Profile>(jsonStr)
assertEquals(profile, restored)
```

## preWizardSnapshot semantics

- Set at Wizard start: `profile.copy(preWizardSnapshot = profile.copy(preWizardSnapshot = null))` (strip snapshot from snapshot).
- Restored on Undo: `profile.preWizardSnapshot ?: profile` (safe fallback if snapshot missing).
- Cleared on Wizard commit or 7 days elapsed since `snapshotTimestamp`.
- Serialized (not transient): survives process death.

## Backward-compat test (SC-008)

```kotlin
val v1Fixture = readResource("fixtures/profile-v1-legacy.json")
val loaded = profileStore.load(v1Fixture)   // migration writer
assertEquals(2, loaded.schemaVersion)
// Assert semantic equivalence: components list has expected size, statuses preserved.
```
