# Contract: `/links/{linkId}/devices/{deviceId}` Document

**Version:** 1.0.0 · **Status:** Stable from спек 011 · **Owner:** spec 011
**Storage**: Firestore document
**Test**: `DeviceIdentityWireFormatTest`, `Security.devices.*`

---

## Purpose

Публичное удостоверение устройства, привязанного к конкретному link'у. Содержит публичный ключ (X25519), который другие устройства этого link'a используют для шифровки CEK через `crypto_box_seal`.

Этот документ — **прямой ответ** на необходимость взять Pub-ключ peer'a перед шифровкой blob'a (см. data flow в [plan.md §Architecture](../plan.md)).

---

## Document path

`/links/{linkId}/devices/{deviceId}` — one document per (link, device). Each link has 2+ devices (admin + Managed; в будущем ~016 — 3+ для group). Each device has exactly 1 keypair per link (текущая модель; spec ~018 introduces rotation).

---

## Field schema

| Field | Type | Required | Server-set | Notes |
|---|---|---|---|---|
| `schemaVersion` | `Int` | ✓ | ✗ | `1` |
| `deviceId` | `String` (UUIDv4) | ✓ | ✗ | Same as path segment `{deviceId}`. Stored for query convenience. |
| `publicKey` | `String` (base64 of 32 bytes) | ✓ | ✗ | X25519 public key. base64-encoded for JSON-friendliness. |
| `createdAt` | `Timestamp` | ✓ | ✓ | server-set on document creation |
| `updatedAt` | `Timestamp` | ✓ | ✓ | server-set; bumped on key rotation (~018) |
| `algorithm` | `String` | ✓ | ✗ | `"x25519"`. Forward-compat для post-quantum keys (Kyber etc.) в ~020+. |
| `revokedAt` | `Timestamp?` | ✗ | ✗ | Set by owner on manual revoke (e.g. compromise suspicion). Readers MUST treat such device as no-encrypt target. |

---

## Example

```json
{
  "schemaVersion": 1,
  "deviceId": "a1b2c3d4-e5f6-4789-abcd-ef0123456789",
  "publicKey": "MCowBQYDK2VuAyEAxIv2OG5d8q5GqRX...",
  "createdAt": {"_seconds": 1747900000, "_nanoseconds": 0},
  "updatedAt": {"_seconds": 1747900000, "_nanoseconds": 0},
  "algorithm": "x25519",
  "revokedAt": null
}
```

---

## Lifecycle

```text
Created    ── Device on first launch generates DeviceKeyPair via AsymmetricCrypto.generateKeyPair()
             ─► Stores private key in SecureKeystore (alias = "launcher_device_priv_v1")
             ─► After pairing's consent.allow, publishes own DeviceIdentity to Firestore
   │
   ├── Updated (rotation):
   │     • spec ~018 (key rotation) — device rotates keys → updates publicKey + updatedAt
   │
   ├── Manually revoked:
   │     • Owner taps "compromise — revoke this device" in admin UI
   │     • Sets revokedAt; document remains for audit trail
   │     • Other devices stop sending new blobs to revoked device
   │
   └── Deleted on link revoke (спек 007 FR-033, recursive subtree delete)
```

---

## Security Rules requirements

```
match /links/{linkId}/devices/{deviceId} {
  function isLinkMember() {
    let link = get(/databases/$(database)/documents/links/$(linkId)).data;
    return request.auth != null && (
      link.adminId == request.auth.uid ||
      link.managedDeviceFirebaseUid == request.auth.uid
    );
  }

  function isOwnDevice() {
    // Verify request UID matches a device this user previously claimed.
    // Stored in /links/{linkId}/deviceOwnership/{deviceId} (auxiliary doc).
    return exists(/databases/$(database)/documents/links/$(linkId)/deviceOwnership/$(deviceId))
        && get(/databases/$(database)/documents/links/$(linkId)/deviceOwnership/$(deviceId)).data.ownerUid == request.auth.uid;
  }

  allow read:   if isLinkMember();   // any pair member can read all peers' Pub
  allow create: if isLinkMember() && request.resource.data.deviceId == deviceId;
  allow update: if isLinkMember() && isOwnDevice();   // only own device may update own keys
  allow delete: if isOwnDevice();
}

// Auxiliary collection to track device ownership without race conditions.
match /links/{linkId}/deviceOwnership/{deviceId} {
  allow read: if isLinkMember();
  allow create: if request.auth != null
                && request.resource.data.ownerUid == request.auth.uid
                && !exists(/databases/$(database)/documents/links/$(linkId)/deviceOwnership/$(deviceId));
  allow update, delete: if false;  // immutable after create
}
```

**Rationale:**
- Any pair member can read all peers' Pub — needed for encryption to that peer.
- Only own device can update own Pub — prevents impersonation.
- `deviceOwnership` doc captures first-write claim, prevents others from stealing identity.

---

## Tests

| Test | What | Phase |
|---|---|---|
| `DeviceIdentityWireFormat.roundtrip` | Write → read → deep-equal | 2 |
| `DeviceIdentityWireFormat.backwardCompat` | v0 synthetic missing `algorithm` → reader applies default | 2 |
| `Security.devices.read.member_OK` | Admin reads Managed's pub → allowed | 5 |
| `Security.devices.read.foreign_DENIED` | Non-pair-member uid → PERMISSION_DENIED | 5 |
| `Security.devices.update.notOwn_DENIED` | Pair member tries to update peer's pub → PERMISSION_DENIED | 5 |
| `Security.devices.update.own_OK` | Owner rotates own pub → allowed | 5 |
| `Security.deviceOwnership.firstWrite_OK` | Owner claims deviceId → allowed once | 5 |
| `Security.deviceOwnership.steal_DENIED` | Another uid tries to overwrite ownership → PERMISSION_DENIED | 5 |

---

## Backward compatibility policy

- Schema stays at `1` for additive changes (CBOR-style readers skip unknown fields).
- `algorithm` field is reserved for forward-compat; current readers verify `algorithm == "x25519"` and `CipherSuiteUnsupported` otherwise.
- Adding new fields → OK без миграции.
- Removing/renaming → bump 1 → 2 + reader migration.

---

## Discoverability

How does a peer find this document?

**Through `Link.KNOWN_SUBCOLLECTIONS`** ([Link.kt:37-44](../../../core/src/commonMain/kotlin/com/launcher/api/link/Link.kt#L37)). Spec 011 extends this list to include `"devices"`. After extension:

```kotlin
val KNOWN_SUBCOLLECTIONS: List<String> = listOf(
    "state", "config", "capabilities", "health", "commands", "configHistory",
    "devices",       // NEW spec 011
    "private-media", // NEW spec 011 (for Storage path, see encrypted-media-storage.md)
    "deviceOwnership", // NEW spec 011 (Firestore auxiliary)
)
```

This ensures `LinkRegistry.revoke()` correctly enumerates all subcollections during subtree delete.

---

<!-- novice summary -->

## TL;DR (простым языком)

**Что в этом файле.** Описание того, как выглядит «паспорт устройства» в Firebase — каждое устройство, которое спарилось с кем-то, кладёт такой документ в Firestore.

**Что внутри документа:**
- `deviceId` — уникальный ID устройства (как «паспорт серии»).
- `publicKey` — публичный ключ (32 байта, закодированы в base64). Это «адрес почтового ящика», куда можно отправить зашифрованные сообщения.
- `createdAt` / `updatedAt` — когда создан / последний раз обновлён.
- `algorithm` — какая математика стоит за этим ключом (сейчас `x25519`, на будущее можем поменять).
- `revokedAt` — если устройство скомпрометировано, владелец может его «отозвать»; другие перестают шифровать для него.

**Security Rules:**
- Любой член пары может **читать** ключи других членов (нужно, чтобы зашифровать им что-то).
- **Изменить** свой ключ может только сам владелец устройства (нельзя притвориться чужим устройством).
- Document `deviceOwnership` — служебный, фиксирует «вот этот uid первым заклеймил этот deviceId», чтобы никто не мог украсть удостоверение.

**Жизненный цикл:**
- При первом запуске приложения устройство **генерирует** пару ключей.
- После pairing'а **публикует** публичный ключ в Firestore.
- В будущем спеке `~018` будем **ротировать** (обновлять) ключи периодически.
- При **revoke** link'а — весь документ удаляется через recursive subtree delete.

**Зачем нужно `deviceOwnership`:** защита от race condition «два устройства одновременно пытаются записать один и тот же deviceId». Только первый успевает заклеймить — остальные получают PERMISSION_DENIED.
