# Wire format: `/links/{linkId}/config/current`

**Source of truth**: this document.
**Used by**: spec 008 §FR-001..006, FR-010..014, FR-020.
**Schema version**: 1 (first commit).
**Lifetime**: пока link существует (deleted on revoke per FR-034 spec 008 + FR-033 spec 007).
**Subcollection root**: spec 007 `link.md` §Subcollections reserved this path (created by admin/Managed; consumer in spec 007 was push-only, full schema in 008).

---

## Document path

`/links/{linkId}/config/current` — single document per link (не collection of versions; история выносится в спек 009 per OUT-007).

## Field schema (root document)

| Field | Type | Required | Server-set | Notes |
|---|---|---|---|---|
| `schemaVersion` | `Int` | ✓ | ✗ | `1` (FR-001) |
| `serverUpdatedAt` | `Timestamp` | ✓ | ✓ | `FieldValue.serverTimestamp()`; используется как version для optimistic concurrency (FR-002, FR-013) |
| `lastWriterDeviceId` | `String` | ✓ | ✗ | UUIDv4 device ID последнего writer'а; для FR-023 self-as-writer skip; **НЕ копируется в /state** (privacy per security checklist CHK019) |
| `presetId` | `String` | ✓ | ✗ | Текущий preset (e.g. `simple-launcher`); FR-003 |
| `flows` | `List<Flow>` | ✓ | ✗ | Раскладка потоков; FR-003, FR-004 |
| `contacts` | `List<Contact>` | ✓ | ✗ | Список контактов; FR-003, FR-004 |

**Note**: backward-compat policy (FR-006) — новые опциональные поля добавляются additive без bump schemaVersion. Renames/removes требуют schemaVersion 1 → 2 + reader-migration в Phase 0 будущего спека.

### Nested: `Flow`

| Field | Type | Required | Notes |
|---|---|---|---|
| `id` | `String` (UUIDv4) | ✓ | client-generated at creation; FR-004 |
| `title` | `String` | ✓ | display label |
| `slots` | `List<Slot>` | ✓ | элементы flow'а |

### Nested: `Slot`

| Field | Type | Required | Notes |
|---|---|---|---|
| `id` | `String` (UUIDv4) | ✓ | client-generated; FR-004 |
| `kind` | `String` | ✓ | discriminator: `"call" | "sms" | "open-app" | …` (closed set; unknown → fail-closed per wire-format checklist CHK009) |
| `args` | `JsonObject` | ✗ | kind-specific params (phone number, package name, etc.) |

### Nested: `Contact`

| Field | Type | Required | Notes |
|---|---|---|---|
| `id` | `String` (UUIDv4) | ✓ | client-generated; FR-004 |
| `displayName` | `String` | ✓ | user-shown name |
| `phoneNumber` | `String` | ✓ | E.164 or as-typed |
| `photoRef` | `String?` | ✗ | reserved — namespace `private:<uuid>` introduced by spec 011 (crypto foundation); filled by spec 012 (contact photos) |

---

## Example

```json
{
  "schemaVersion": 1,
  "serverUpdatedAt": {"_seconds": 1747166400, "_nanoseconds": 0},
  "lastWriterDeviceId": "0c8e3a5e-1e7c-4f7b-9a1d-2b3c4d5e6f7a",
  "presetId": "simple-launcher",
  "flows": [
    {
      "id": "f1111111-1111-4111-8111-111111111111",
      "title": "Главный",
      "slots": [
        {
          "id": "s1111111-1111-4111-8111-111111111111",
          "kind": "call",
          "args": {"contactId": "c1111111-1111-4111-8111-111111111111"}
        }
      ]
    }
  ],
  "contacts": [
    {
      "id": "c1111111-1111-4111-8111-111111111111",
      "displayName": "Маша",
      "phoneNumber": "+71234567890"
    }
  ]
}
```

---

## Lifecycle

```text
Created    ── by editor (admin or Managed) at first push после pairing (FR-010)
   │
   ├── Updated by editor on push (FR-010..014, optimistic concurrency):
   │     • write requires snapshot serverUpdatedAt == current serverUpdatedAt;
   │     • mismatch → TransactionConflict → merge UI (FR-014)
   │     • server sets new serverUpdatedAt via FieldValue.serverTimestamp()
   │
   ├── Triggers FCM push (spec 007 Worker, payload type config.updated, FR-020):
   │     • Managed reads /config/current
   │     • ConfigApplier applies atomically (FR-021)
   │     • writes /state/current.appliedConfigUpdatedAt
   │
   └── Deleted on revoke (FR-034) — recursive subtree delete (extends 007 FR-033)
```

---

## Security Rules requirements

Extends spec 007 Security Rules (`firestore.rules`):

```text
match /links/{linkId}/config/current {
  allow read:   if isAdmin(linkId) || isManaged(linkId);
  allow create: if isAdmin(linkId) || isManaged(linkId);
  allow update: if (isAdmin(linkId) || isManaged(linkId))
                && request.resource.data.schemaVersion == resource.data.schemaVersion
                && request.resource.data.serverUpdatedAt == request.time;
  allow delete: if isManaged(linkId);  // revoke (FR-034)
}
```

- **Create**: by adminId OR managedDeviceFirebaseUid (FR-011 — equal editors).
- **Read**: same (admin reads to compute diff; Managed reads to apply).
- **Update**: same + cannot downgrade schemaVersion + serverUpdatedAt must match `request.time` (server-set check).
- **Delete**: only Managed (revoke path) — admin cannot unilaterally orphan link.

**Optimistic concurrency**: enforced **client-side** через `RemoteSyncBackend.runTransaction { read → check serverUpdatedAt → write }`. Server Rules cannot enforce «snapshot must equal current» because Firestore Rules don't have access to client's prior read; transaction-based check is the standard Firestore pattern. See research.md §1.

---

## Tests (commonTest)

| Test | What it verifies | Phase |
|---|---|---|
| `ConfigDocumentWireFormat.roundtrip_minimal` | Write v1 minimal → read v1 → assertEquals | 2 |
| `ConfigDocumentWireFormat.roundtrip_full` | Write v1 full (10 flows × 5 slots, 30 contacts) → read → equal | 2 |
| `ConfigDocumentWireFormat.backwardCompat_v0_reads_v1` | Synthetic «v0» fixture (without `lastWriterDeviceId`) read by v1 reader with default | 2 |
| `ConfigDocumentWireFormat.unknown_slot_kind_fails_closed` | Slot.kind = "unknown-future-kind" → parse Failure, not crash | 2 |
| `ConfigDiff.identical_diff_empty` | Two ConfigDocuments with same content → diff empty (FR-052) | 1 |
| `ConfigDiff.non_overlapping_elements` | A adds flow X, B adds flow Y (different id) → diff has both, no conflict (FR-053) | 1 |
| `ConfigDiff.overlapping_modification` | Both modify slot id=Z → conflict in diff (FR-051) | 1 |
| `ConfigDiff.deletion_vs_modification` | A deletes id=Z, B modifies id=Z → conflict (US-2 scenario 5) | 1 |
| `Security.config.admin_can_write` | adminId write → OK (Firebase Emulator) | 5 |
| `Security.config.managed_can_write` | managedDeviceFirebaseUid write → OK | 5 |
| `Security.config.foreign_uid_denied` | Other uid → PERMISSION_DENIED | 5 |
| `Security.config.stale_snapshot_rejected` | Two clients race; second one's transaction aborts | 4 |

**Fixtures** (`commonTest/resources/wire-format/`):
- `config-v1-minimal.json`
- `config-v1-full.json`
- `config-v0-synthetic.json` (без `lastWriterDeviceId` — emulates pre-v1 reader test)

---

## Backward compatibility policy

- Spec 008 ships at `schemaVersion=1`. Все future additions — additive (новые опциональные поля без bump).
- Rename/remove of any field → bump to 2, reader-migration в Phase 0 следующего спека.
- Forward-compat (admin v2 ↔ Managed v1) — handled by `app-version-compatibility` спек (OUT-006 в spec 008; TODO-ARCH-007).
- Wire-format invariant: `schemaVersion` MUST be read FIRST in deserializer (wire-format checklist CHK002 — codified in `ConfigDocumentWireFormat.kt`).

**TODO comment in code** (`ConfigDocument.kt`):
> При расширении схемы — добавлять опциональные поля **без** изменения schemaVersion. Rename/remove — bump 1 → 2 + reader-migration в Phase 0 нового спека.

---

<!-- novice summary -->

## TL;DR

«Договор о том, как выглядит на сервере раскладка телефона». В этом документе записано: какой preset, какие потоки и плитки, какие контакты, кто последний редактировал. Каждое изменение перезаписывает документ целиком, сервер ставит свою временную метку. При попытке записать одновременно с двух устройств — побеждает тот, кто прочитал свежее; второй увидит «кто-то опередил, вот разница, выбери что оставить» (merge UI). История прошлых версий — в отдельном спеке 009.
