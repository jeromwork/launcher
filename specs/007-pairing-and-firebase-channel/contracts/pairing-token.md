# Wire format: `/pairings/{token}`

**Source of truth**: this document.
**Used by**: spec 007 §FR-003, FR-006, FR-007, FR-008.
**Schema version**: 1 (с первого коммита).
**Lifetime**: ephemeral — 5 минут или single claim.

---

## Document path

`/pairings/{token}` где `token` — 6-символьная строка из alphabet `[A-HJ-NP-Z2-9]` (32 chars, excluding `0`, `O`, `I`, `1`).

## Field schema (JSON over Firestore)

| Field | Type | Required | Server-set | Notes |
|---|---|---|---|---|
| `schemaVersion` | `Int` | ✓ | ✗ | Currently `1` |
| `pairingType` | `String?` | ✗ (default `"admin-managed-link"`) | ✗ | Discriminator для reusable trust primitive (см. plan.md §Reusable trust primitive). В 007 — только `"admin-managed-link"`. Будущие спеки добавят `"trusted-contact"` (спек 011), `"call-trust-edge"` (звонки), `"sub-admin-link"` (multi-admin), `"device-replacement"` (config-portability). Backward-compat: отсутствие = `"admin-managed-link"` |
| `managedDeviceId` | `String` | ✓ | ✗ | UUIDv4 из DataStore Managed'а (FR-001). **Note**: для будущих pairing-типов поле может стать опциональным или переименоваться через wire-format v2 |
| `managedDeviceFirebaseUid` | `String` | ✓ | ✗ | Текущий Firebase Auth UID Managed'а (для Security Rules) |
| `claimed` | `Boolean` | ✓ | ✗ | `false` при создании; `true` после admin transaction (FR-006) |
| `expiresAt` | `Timestamp` | ✓ | ✗ | Client-computed: `now + 5min` |
| `createdAt` | `Timestamp` | ✓ | ✓ | `FieldValue.serverTimestamp()` |
| `updatedAt` | `Timestamp` | ✓ | ✓ | `FieldValue.serverTimestamp()` (FR-030) |

## Example (Firestore JSON)

```json
{
  "schemaVersion": 1,
  "pairingType": "admin-managed-link",
  "managedDeviceId": "0c8e3a5e-1e7c-4f7b-9a1d-2b3c4d5e6f7a",
  "managedDeviceFirebaseUid": "anonUidAbc123",
  "claimed": false,
  "expiresAt": {"_seconds": 1746974400, "_nanoseconds": 0},
  "createdAt": {"_seconds": 1746974100, "_nanoseconds": 0},
  "updatedAt": {"_seconds": 1746974100, "_nanoseconds": 0}
}
```

## Lifecycle

```text
Created                ── Managed writes (FR-003) ── claimed:false
   │
   ├── admin transaction claim (FR-006)
   │       │
   │       ├── precondition: !claimed && expiresAt > now
   │       └── atomic: claimed := true; create /links/{linkId}
   │
   ├── Managed consent.decline (FR-008): document fully deleted
   ├── Managed consent.allow (FR-009): /links/{linkId}/state created (doc itself stays for audit until TTL)
   └── expiresAt < now: client deletes on next read (no Cloud Function)
```

## Security Rules requirements (FR-029)

- **Create**: only anonymous-authenticated users; `managedDeviceFirebaseUid == request.auth.uid` (anti-spam).
- **Read**: anyone (token is the secret).
- **Update**: only admin transaction (`claimed: false → true`); validate via Firestore `request.resource.data.claimed == true && resource.data.claimed == false`.
- **Delete**: only by `managedDeviceFirebaseUid` (decline) or by creator (cleanup).

## Tests (commonTest)

| Test | What it verifies |
|---|---|
| `PairingTokenWireFormat.roundtrip` | Write v1 → read v1 → assert deep-equal |
| `PairingTokenWireFormat.backwardCompat_v2_reads_v1` | Reader expecting v2 reads v1-snapshot without error (unknown fields tolerated, missing v2-fields use defaults) |
| `PairingToken.generate_validates_alphabet` | `PairingToken.generate()` produces only chars from allowed alphabet |
| `PairingToken.regex_rejects_visually_similar` | Strings containing `0`, `O`, `I`, `1` are rejected |

## Backward compatibility policy (CLAUDE.md §5)

- Adding fields → OK без миграции (readers ignore unknown).
- Renaming field → requires new schemaVersion + migration code.
- Removing field → not allowed for at least 1 major release; instead mark deprecated.

**TODO для будущих расширений**: если spec 011 (private media) добавит `pairingKey` для e2e — добавляем поле как опциональное, schemaVersion остаётся 1 (additive); next breaking change → v2.

---

<!-- novice summary -->

## TL;DR

Это документ в Firestore (как «строка в таблице»), который существует **5 минут** или до первого claim'а. Managed-устройство создаёт его когда пользователь включает «Разрешить удалённое управление». Admin читает его при сканировании QR. Поля простые: токен, кто хозяин, claimed-флаг, expires-время. После claim'а — становится «истории» (или удаляется при decline).
