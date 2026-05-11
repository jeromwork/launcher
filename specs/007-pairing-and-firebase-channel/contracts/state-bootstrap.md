# Wire format: `/links/{linkId}/state/current` (bootstrap)

**Source of truth**: this document.
**Used by**: spec 007 §FR-009, FR-027. Full schema — спек 008.
**Schema version**: 1.
**Lifetime**: пока link существует.

---

## Document path

`/links/{linkId}/state/current` — single document per link (не collection of states; единственный «current state» snapshot).

## Field schema (bootstrap snapshot only — spec 007)

| Field | Type | Required | Server-set | Notes |
|---|---|---|---|---|
| `schemaVersion` | `Int` | ✓ | ✗ | `1` |
| `appliedAt` | `Timestamp` | ✓ | ✓ | Server-set on write |
| `presetId` | `String` | ✓ | ✗ | Current preset (e.g. `simple-launcher`) |
| `fcmToken` | `String?` | ✗ | ✗ | FCM token OLD'а; `null` если GMS отсутствует (C13) |
| `updatedAt` | `Timestamp` | ✓ | ✓ | FR-030 |

**Полная схема state** (с `flows`, `slots`, applied capabilities snapshot и т.д.) — спек 008.

## Example

```json
{
  "schemaVersion": 1,
  "appliedAt": {"_seconds": 1746974400, "_nanoseconds": 0},
  "presetId": "simple-launcher",
  "fcmToken": "fAk3FcmT0k3n_eXampLe_a1b2c3d4...",
  "updatedAt": {"_seconds": 1746974400, "_nanoseconds": 0}
}
```

## Lifecycle

```text
Created    ── OLD on consent.allow (FR-009) — initial bootstrap snapshot
   │
   ├── Updated by OLD on:
   │     • FCM token rotation (FR-017) — обновляется fcmToken
   │     • Preset changed in Settings — обновляется presetId
   │     • (spec 008) on config apply — добавляются flows/slots fields
   │
   ├── Read by admin (Security Rules: adminId can read)
   │
   └── Deleted on revoke (FR-033, recursive subtree delete)
```

## Security Rules requirements

- **Create**: by `oldDeviceFirebaseUid` only.
- **Read**: by `adminId` OR `oldDeviceFirebaseUid`.
- **Update**: by `oldDeviceFirebaseUid` only; cannot change `schemaVersion` downward.
- **Delete**: by `oldDeviceFirebaseUid` (revoke).

## Tests (commonTest)

| Test | What it verifies |
|---|---|
| `LinkBootstrapWireFormat.roundtrip` | Write v1 → read v1 → assert deep-equal (incl. fcmToken=null case) |
| `LinkBootstrapWireFormat.backwardCompat_v2_reads_v1` | Future reader expecting v2-shape reads v1 |
| `LinkBootstrapWireFormat.fcmToken_null_handled` | null fcmToken survives roundtrip |
| `LinkBootstrap.security_rules.admin_cannot_write_state` | Admin writes to state denied by Security Rules (Firebase Emulator) |

## Backward compatibility policy

Spec 008 расширит schema добавлением `flows`, `slots`, `appliedCapabilities` и т.д. Расширение — **additive**, schemaVersion остаётся 1 для совместимости read'еров спека 007 со state'ом спека 008.

Если в spec 009/010 потребуется breaking change (rename/remove) → schemaVersion бампается до 2, требуется reader-migration в Phase 0 спека.

**TODO в `LinkBootstrap.kt`**: «при расширении в спеке 008 — добавлять опциональные поля, не менять schemaVersion».

---

<!-- novice summary -->

## TL;DR

«Текущее состояние бабушкиного телефона глазами admin'а». В этом спеке — **минимум**: какой preset, какой FCM-token (для пушей), когда применено. В следующем спеке (008) — наполнится полностью: какие плитки на экране, какие контакты, и т.д. Сейчас — bootstrap, чтобы admin увидел «pairing успешен, телефон отвечает».
