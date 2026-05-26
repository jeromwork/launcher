# Wire format: `/links/{linkId}/state/current` (extended schema)

**Source of truth**: this document.
**Used by**: spec 008 §FR-030..033, SC-001b.
**Schema version**: 1 (consistent с spec 007 `state-bootstrap.md` — additive extension, без bump).
**Lifetime**: пока link существует.
**Origin**: spec 007 `state-bootstrap.md` defined initial bootstrap schema (presetId, fcmToken, appliedAt, updatedAt). Spec 008 adds applied-snapshot fields **additive** per FR-032.

---

## Document path

`/links/{linkId}/state/current` — same as spec 007; single document per link.

## Field schema (full — spec 008 superset of spec 007 bootstrap)

| Field | Type | Required | Server-set | Origin | Notes |
|---|---|---|---|---|---|
| `schemaVersion` | `Int` | ✓ | ✗ | 007 | `1` |
| `appliedAt` | `Timestamp` | ✓ | ✓ | 007 | server-set on every write |
| `presetId` | `String` | ✓ | ✗ | 007 | current applied preset |
| `fcmToken` | `String?` | ✗ | ✗ | 007 | nullable if no GMS (C13 from 007) |
| `updatedAt` | `Timestamp` | ✓ | ✓ | 007 | FR-030 of 007 |
| `appliedConfigUpdatedAt` | `Timestamp?` | ✗ | ✗ | **008 NEW** | mirrors `/config/current.serverUpdatedAt` at apply time; nullable если ещё не было apply'а; FR-031 |
| `flowsApplied` | `List<FlowApplied>?` | ✗ | ✗ | **008 NEW** | applied flows snapshot (что реально показано на launcher screen); FR-033 |
| `contactsApplied` | `List<ContactApplied>?` | ✗ | ✗ | **008 NEW** | applied contacts snapshot; FR-033 |
| `partialApplyReasons` | `List<PartialReason>?` | ✗ | ✗ | **008 NEW** | non-empty if partial apply happened; FR-033 |

**Additive guarantee** (FR-032): existing spec 007 readers (если будут запущены против spec 008 server) read только prefix полей; новые поля ignored. Schema version stays at `1`.

### Nested: `FlowApplied`

Subset of `/config/current.flows[i]` after apply. Same `id` (UUIDv4) as `/config` flow для diff in admin UI.

| Field | Type | Required | Notes |
|---|---|---|---|
| `id` | `String` (UUIDv4) | ✓ | matches /config Flow.id |
| `title` | `String` | ✓ | display label |
| `slots` | `List<SlotApplied>` | ✓ | applied slots |

### Nested: `SlotApplied`

| Field | Type | Required | Notes |
|---|---|---|---|
| `id` | `String` (UUIDv4) | ✓ | matches /config Slot.id |
| `kind` | `String` | ✓ | same discriminator as /config |
| `appliedSuccessfully` | `Boolean` | ✓ | `false` if provider unavailable / permission denied at apply time |

### Nested: `ContactApplied`

| Field | Type | Required | Notes |
|---|---|---|---|
| `id` | `String` (UUIDv4) | ✓ | matches /config Contact.id |
| `displayName` | `String` | ✓ | may differ if Managed's contact-provider rewrote |
| `appliedSuccessfully` | `Boolean` | ✓ | `false` if e.g. e2e photo decryption failed (introduced by spec 012, uses crypto foundation of spec 011) |

### Nested: `PartialReason`

Enum-keys (NOT human-readable strings — localization-safe per ux-quality checklist CHK011):

| Value | Meaning |
|---|---|
| `provider_unavailable` | Provider (e.g. dialer) not installed on Managed |
| `contact_permission_denied` | READ_CONTACTS permission revoked |
| `media_decrypt_failed` | e2e media couldn't be decrypted — namespace `private:<uuid>` from spec 011 (crypto foundation), эмитится впервые в spec 012 (contact photos) |
| `unknown_slot_kind` | Slot.kind не понятен текущему apply'еру (forward-compat) |

---

## Example

```json
{
  "schemaVersion": 1,
  "appliedAt": {"_seconds": 1747166410, "_nanoseconds": 0},
  "presetId": "simple-launcher",
  "fcmToken": "fAk3FcmT0k3n_eXampLe_a1b2c3d4...",
  "updatedAt": {"_seconds": 1747166410, "_nanoseconds": 0},
  "appliedConfigUpdatedAt": {"_seconds": 1747166400, "_nanoseconds": 0},
  "flowsApplied": [
    {
      "id": "f1111111-1111-4111-8111-111111111111",
      "title": "Главный",
      "slots": [
        {"id": "s1111111-1111-4111-8111-111111111111", "kind": "call", "appliedSuccessfully": true}
      ]
    }
  ],
  "contactsApplied": [
    {"id": "c1111111-1111-4111-8111-111111111111", "displayName": "Маша", "appliedSuccessfully": true}
  ],
  "partialApplyReasons": []
}
```

**Example with partial apply** (provider unavailable):

```json
{
  "schemaVersion": 1,
  "appliedAt": {"_seconds": 1747166410},
  "presetId": "simple-launcher",
  "updatedAt": {"_seconds": 1747166410},
  "appliedConfigUpdatedAt": {"_seconds": 1747166400},
  "flowsApplied": [
    {
      "id": "f1111111-...",
      "title": "Главный",
      "slots": [
        {"id": "s1111111-...", "kind": "call", "appliedSuccessfully": true},
        {"id": "s2222222-...", "kind": "open-app", "appliedSuccessfully": false}
      ]
    }
  ],
  "contactsApplied": [...],
  "partialApplyReasons": ["provider_unavailable"]
}
```

---

## Lifecycle

```text
Created    ── Managed on consent.allow (spec 007 FR-009) — initial bootstrap with /state subset (007 fields only)
   │
   ├── Updated by Managed on:
   │     • FCM token rotation (spec 007 FR-017) — updates fcmToken
   │     • Preset changed in Settings — updates presetId
   │     • (spec 008 NEW) on ConfigApplier.applyFromRemote() — populates flowsApplied/contactsApplied/appliedConfigUpdatedAt/partialApplyReasons
   │
   ├── Read by admin (Security Rules from 007: adminId can read)
   │     • admin-UI renders pending-badge / applied-indicator from this document
   │
   └── Deleted on revoke (spec 007 FR-033, recursive subtree delete)
```

---

## Security Rules requirements

**No change from spec 007** (`state-bootstrap.md` §Security Rules):

- **Create**: by `managedDeviceFirebaseUid` only.
- **Read**: by `adminId` OR `managedDeviceFirebaseUid`.
- **Update**: by `managedDeviceFirebaseUid` only; cannot downgrade `schemaVersion`.
- **Delete**: by `managedDeviceFirebaseUid` (revoke).

`adminId` MUST NOT write to `/state/current` — это invariant («/state — source of truth для admin-UI» из spec.md FR-009; admin reads, Managed writes).

---

## Tests (commonTest + Firebase Emulator)

| Test | What it verifies | Phase |
|---|---|---|
| `StateAppliedWireFormat.roundtrip_bootstrapOnly` | spec 007 fields only → write/read → equal | 2 |
| `StateAppliedWireFormat.roundtrip_full` | spec 008 superset → write/read → equal | 2 |
| `StateAppliedWireFormat.backwardCompat_007Reader` | spec 007 reader reads spec 008 document → spec 008 fields ignored, 007 fields preserved | 2 |
| `StateAppliedWireFormat.partialApply_serialized` | partialApplyReasons non-empty → serialized correctly | 2 |
| `Security.state.admin_cannot_write` | admin write → PERMISSION_DENIED (Firebase Emulator) | 5 |
| `Security.state.managed_can_update_extension_fields` | Managed update of new 008 fields → allowed | 5 |

**Fixtures** (`commonTest/resources/wire-format/`):
- `state-applied-v1-bootstrap-only.json` (spec 007 shape; emulates 007 → 008 transition)
- `state-applied-v1-full.json` (spec 008 full)
- `state-applied-v1-partial.json` (with partialApplyReasons)

---

## Backward compatibility policy

- Schema stays at `1` (additive extension per FR-032).
- Spec 007 readers (LinkBootstrap.kt) read only their subset of fields; new fields silently ignored.
- Future fields (e.g. spec 010 health, spec 012 balance status) — add additive, no bump.
- Removal/rename of any field → bump to 2 + reader-migration (deferred to `app-version-compatibility` spec).

**TODO comment in code** (`StateApplied.kt`):
> Spec 008 расширяет схему 007 additive (schemaVersion stays 1). Если в spec 010 / 012 потребуется breaking change — bump 1 → 2.

---

## FCM payload `config.updated` (inline)

Spec 008 extends spec 007 `fcm-payload.md` with new payload type. **Payload format unchanged** — only new `type` discriminator value.

| Field | Type | Required | Notes |
|---|---|---|---|
| `schemaVersion` | `Int` | ✓ | `1` (matches spec 007 fcm-payload) |
| `type` | `String` | ✓ | `"config.updated"` (NEW in 008; spec 007 had `"config-changed"`, `"command-issued"`, `"revoke"`) |
| `linkId` | `String` | ✓ | which link the config update affects |
| `extra` | `JsonObject?` | ✗ | unused for `config.updated` (Managed reads /config/current; payload is trigger-only) |

**Reuse vs new payload type**: spec 007 already had `PushType.ConfigChanged` (sealed type). Spec 008 question: extend semantics of existing `ConfigChanged` OR add new `ConfigUpdated`? **Decision**: keep `PushType.ConfigChanged` and reuse for spec 008 (semantically same: «config has changed on server, Managed should re-read»). No new type. Avoids speculative `PushType` enrichment per meta-minimization.

**Action**: in Cloudflare Worker (`push-worker/`), Phase 6 of spec 008 — confirm that Firestore trigger on `/config/current` write → sends FCM with existing `type=config-changed`. If 007 didn't wire that trigger (only `revoke` was wired in 007), add it in Phase 6.

---

<!-- novice summary -->

## TL;DR

«Что бабушкин телефон реально применил из последнего конфига». Расширение схемы из спека 007: добавились поля «какая версия конфига сейчас на экране» (`appliedConfigUpdatedAt`), список применённых плиток/контактов, и список проблем при применении (если что-то не получилось — например, разрешения на контакты отозвали). Admin читает этот документ, чтобы видеть значок «применено на телефоне ✓» и понимать, синхронизирован ли его последний push.
