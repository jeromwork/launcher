# Contract: `/links/{linkId}/devices/{deviceId}` Document

**Version:** 1.0.0 · **Status:** Stable from спек 011 rev. 2 (2026-05-22) · **Owner:** spec 011
**Storage**: Firestore document
**Test**: `DeviceIdentityWireFormatTest`, `DeviceIdentitySignatureTest`, `Security.devices.*`

---

## Purpose

Публичное удостоверение устройства, привязанного к конкретному link'у. Содержит:
- **Publickey X25519** для encryption (sealing CEK через `crypto_box_seal`).
- **Public key Ed25519** для signing/verifying (anti-tamper, future Jitsi/vendor auth).
- **Signed timestamp + signature** — защита от подмены payload через compromise Firestore document.

Этот документ — **прямой ответ** на необходимость взять Pub-ключи peer'a перед шифровкой blob'a и убедиться, что Pub'ы действительно от того устройства, которое мы спарили (см. data flow в [data-model.md §4](../data-model.md)).

---

## Document path

`/links/{linkId}/devices/{deviceId}` — one document per (link, device). Each link has 2+ devices (admin + Managed; в будущем спек 014 (groups) — 3+). Each device has exactly 1 X25519 keypair + 1 Ed25519 keypair per link (текущая модель; spec 016 introduces rotation).

---

## Field schema

| Field | Type | Required | Server-set | Notes |
|---|---|---|---|---|
| `schemaVersion` | `Int` | ✓ | ✗ | `1` |
| `deviceId` | `String` (UUIDv4) | ✓ | ✗ | Same as path segment `{deviceId}`. Stored for query convenience. Inside signed payload. |
| `publicKey` | `String` (base64 of 32 bytes) | ✓ | ✗ | X25519 public key (для encryption). Inside signed payload. |
| `signingPublicKey` | `String` (base64 of 32 bytes) | ✓ | ✗ | **NEW в rev. 2** — Ed25519 public key (для signing/verifying). Inside signed payload. |
| `signedTimestamp` | `Long` (epoch millis) | ✓ | ✗ | **NEW в rev. 2** — момент создания signature. Используется для freshness check (replay-attack mitigation). Inside signed payload. |
| `signature` | `String` (base64 of 64 bytes) | ✓ | ✗ | **NEW в rev. 2** — Ed25519 signature над `{schemaVersion, deviceId, publicKey, signingPublicKey, signedTimestamp}` (canonical CBOR encoding, см. ниже). Created by own Ed25519 priv key. |
| `createdAt` | `Timestamp` | ✓ | ✓ | server-set on document creation |
| `updatedAt` | `Timestamp` | ✓ | ✓ | server-set; bumped on key rotation (spec 016) |
| `algorithm` | `String` | ✓ | ✗ | `"x25519+ed25519"`. Forward-compat для post-quantum keys (~spec 020+). |
| `revokedAt` | `Timestamp?` | ✗ | ✗ | Set by owner on manual revoke (e.g. compromise suspicion). Readers MUST treat such device as no-encrypt target. |

---

## Signed payload (canonical CBOR)

Для воспроизводимой signature MUST использоваться канонический порядок полей в signed payload:

```cbor
{
  "schemaVersion": 1,
  "deviceId": "a1b2c3d4-e5f6-4789-abcd-ef0123456789",
  "publicKey": <32 bytes>,
  "signingPublicKey": <32 bytes>,
  "signedTimestamp": 1747900000000
}
```

(Поля в alphabetical order; bstr для key bytes, не base64 string — внутри signature.)

**Why canonical CBOR vs JSON in signed payload**:
- Bit-exact reproducibility (JSON whitespace, key ordering — не deterministic).
- libsodium-native (мы уже используем CBOR для envelope).
- Smaller signed payload — faster sign/verify.

**Implementation**: `DeviceIdentity.signedPayloadBytes(): ByteArray` — функция в commonMain, используется и для sign, и для verify.

---

## Verification flow

При `fetchPeer(linkId, peerDeviceId)`:

1. Прочитать document из Firestore.
2. Извлечь `signedPayload` = canonical CBOR encoding of {schemaVersion, deviceId, publicKey, signingPublicKey, signedTimestamp}.
3. `DigitalSignature.verify(signedPayload, signature, signingPublicKey)`:
   - **MUST pass** → continue.
   - Fail → `CryptoError.SignatureVerifyFailed(peerDeviceId)`. **No fallback.**
4. Verify freshness: `now - signedTimestamp < 7 days * 86400000 millis`.
   - Pass → use identity.
   - Fail → `CryptoError.SignatureVerifyFailed(peerDeviceId)` (stale signature — likely replay attack или device offline > 7 days, manual re-publication needed).
5. Cache identity locally для offline use.

---

## Example

```json
{
  "schemaVersion": 1,
  "deviceId": "a1b2c3d4-e5f6-4789-abcd-ef0123456789",
  "publicKey": "MCowBQYDK2VuAyEAxIv2OG5d8q5GqRX...",
  "signingPublicKey": "MCowBQYDK2VwAyEA9q5Gq5BqRXMCowBQYDK2VwAyEA...",
  "signedTimestamp": 1747900000000,
  "signature": "Wm9hX2VkMjU1MTlfc2lnX2V4YW1wbGVfNjRfYnl0ZXNfaGVyZS4uLg==",
  "createdAt": {"_seconds": 1747900001, "_nanoseconds": 0},
  "updatedAt": {"_seconds": 1747900001, "_nanoseconds": 0},
  "algorithm": "x25519+ed25519",
  "revokedAt": null
}
```

---

## Lifecycle

```text
Created    ── Device on first launch generates DeviceKeyPair (X25519) via AsymmetricCrypto.generateX25519Pair()
             + DeviceSigningKeyPair (Ed25519) via DigitalSignature.generateEd25519Pair()
             ─► Stores private keys in SecureKeystore
             ─► After pairing's consent.allow, signs payload + publishes own DeviceIdentity to Firestore
   │
   ├── Updated (rotation):
   │     • spec 016 (key rotation) — device rotates keys → updates publicKey + signingPublicKey + signedTimestamp + signature + updatedAt
   │
   ├── Manually revoked:
   │     • Owner taps "compromise — revoke this device" in admin UI (spec 012+)
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
    return exists(/databases/$(database)/documents/links/$(linkId)/deviceOwnership/$(deviceId))
        && get(/databases/$(database)/documents/links/$(linkId)/deviceOwnership/$(deviceId)).data.ownerUid == request.auth.uid;
  }

  function freshSignedTimestamp() {
    // signedTimestamp not older than 7 days (replay-attack mitigation)
    let nowMillis = request.time.toMillis();
    let signedMillis = request.resource.data.signedTimestamp;
    return (nowMillis - signedMillis) < (7 * 24 * 60 * 60 * 1000)
        && (nowMillis - signedMillis) > -(60 * 1000);  // allow 1 minute clock skew
  }

  function hasRequiredFields() {
    let d = request.resource.data;
    return d.schemaVersion == 1
        && d.deviceId is string
        && d.publicKey is string
        && d.signingPublicKey is string
        && d.signedTimestamp is int
        && d.signature is string
        && d.algorithm == "x25519+ed25519";
  }

  allow read:   if isLinkMember();
  allow create: if isLinkMember() && request.resource.data.deviceId == deviceId
                && hasRequiredFields() && freshSignedTimestamp();
  allow update: if isLinkMember() && isOwnDevice()
                && hasRequiredFields() && freshSignedTimestamp();
  allow delete: if isOwnDevice();
}

match /links/{linkId}/deviceOwnership/{deviceId} {
  allow read: if isLinkMember();
  allow create: if request.auth != null
                && request.resource.data.ownerUid == request.auth.uid
                && !exists(/databases/$(database)/documents/links/$(linkId)/deviceOwnership/$(deviceId));
  allow update, delete: if false;
}
```

**Rationale:**
- Любой member пары может read peer's Pub'ы — нужно для encryption и signature verification.
- Только own device может update own Pub'ы — prevents impersonation.
- `deviceOwnership` doc captures first-write claim — race-free identity.
- `freshSignedTimestamp` — server-side gate against replay attacks (рядом с client-side verification на fetchPeer).
- `hasRequiredFields` — server-side schema enforcement (rev. 2 adds 3 mandatory fields).

**Note: Security Rules не верифицируют Ed25519 signature над payload** — Firestore Rules не имеют crypto primitives. Verification — client-side при fetchPeer. Server-side freshness gate — minimal protection layer.

---

## Tests

| Test | What | Phase |
|---|---|---|
| `DeviceIdentityWireFormat.roundtrip` | Write → read → deep-equal (включая все 3 новые поля) | 2 |
| `DeviceIdentityWireFormat.backwardCompat` | v0 synthetic missing `signingPublicKey` → reader returns `CryptoError.MalformedEnvelope` (schema mismatch) — backward-compat reads документов до rev. 2 НЕ поддерживаются (rev. 2 — initial production schema) | 2 |
| `DeviceIdentitySignature.signAndVerify` | Sign payload, verify with corresponding Ed25519 Pub — success | 3 |
| `DeviceIdentitySignature.tampered` | Modify any field after sign → verify fails → `SignatureVerifyFailed` | 3 |
| `DeviceIdentitySignature.staleTimestamp` | signedTimestamp > 7 days old → fetchPeer returns `SignatureVerifyFailed` | 4 |
| `Security.devices.read.member_OK` | Admin reads Managed's pub → allowed | 5 |
| `Security.devices.read.foreign_DENIED` | Non-pair-member uid → PERMISSION_DENIED | 5 |
| `Security.devices.create.staleSig_DENIED` | Create with signedTimestamp > 7 days old → PERMISSION_DENIED (server-side gate) | 5 |
| `Security.devices.create.futureSig_DENIED` | Create with signedTimestamp > 1 min in future → PERMISSION_DENIED | 5 |
| `Security.devices.create.missingFields_DENIED` | Create without signingPublicKey → PERMISSION_DENIED (hasRequiredFields fail) | 5 |
| `Security.devices.update.notOwn_DENIED` | Pair member tries to update peer's pub → PERMISSION_DENIED | 5 |
| `Security.devices.update.own_OK` | Owner rotates own pub → allowed | 5 |
| `Security.deviceOwnership.firstWrite_OK` | Owner claims deviceId → allowed once | 5 |
| `Security.deviceOwnership.steal_DENIED` | Another uid tries to overwrite ownership → PERMISSION_DENIED | 5 |

---

## Backward compatibility policy

- Schema rev. 2 (2026-05-22) — adds `signingPublicKey`, `signedTimestamp`, `signature` as mandatory fields. Это **breaking change** к rev. 1 (pre-rev.2 documents без signature не accept'ятся).
- Поскольку pre-production (нет реальных пользователей до спека ~35), миграция не нужна — старые документы можно стереть.
- Adding new fields в rev. 2+ → OK без миграции (additive, additional fields skipped by readers).
- Removing/renaming → bump 1 → 2 + reader migration (per CLAUDE.md rule 5).

---

## Discoverability

How does a peer find this document?

**Through `Link.KNOWN_SUBCOLLECTIONS`** ([Link.kt:37-44](../../../core/src/commonMain/kotlin/com/launcher/api/link/Link.kt#L37)). Spec 011 extends this list to include `"devices"`. After extension:

```kotlin
val KNOWN_SUBCOLLECTIONS: List<String> = listOf(
    "state", "config", "capabilities", "health", "commands", "configHistory",
    "devices",         // NEW spec 011
    "deviceOwnership", // NEW spec 011 (Firestore auxiliary)
)

// Separately, Storage paths (новый список для recursive Storage cleanup в LinkRegistry.revoke())
val KNOWN_STORAGE_PATHS: List<String> = listOf(
    "private-media",   // NEW spec 011 (see encrypted-media-storage.md)
)
```

This ensures `LinkRegistry.revoke()` correctly enumerates all Firestore subcollections AND Storage paths during subtree delete.

---

<!-- novice summary -->

## TL;DR (простым языком)

**Что в этом файле.** Описание того, как выглядит «паспорт устройства» в Firebase — каждое устройство, которое спарилось с кем-то, кладёт такой документ в Firestore.

**Что внутри документа (rev. 2 — 2026-05-22):**
- `deviceId` — уникальный ID устройства.
- `publicKey` — публичный ключ X25519 (для шифрования). 32 байта, base64.
- **`signingPublicKey` (новое)** — публичный ключ Ed25519 (для подписи). 32 байта, base64.
- **`signedTimestamp` (новое)** — когда мы подписали этот документ (защита от «возьми старую подпись и подделай»).
- **`signature` (новое)** — цифровая подпись Ed25519 над содержимым (защита от подмены, даже если кто-то залезет в Firestore).
- `createdAt` / `updatedAt` — когда создан / обновлён.
- `algorithm` — `"x25519+ed25519"`.
- `revokedAt` — если устройство скомпрометировано, владелец может его «отозвать».

**Как работает защита от подмены:**
1. Устройство подписывает своё «удостоверение» **своим** приватным Ed25519 ключом.
2. Подпись в документе.
3. Когда другое устройство читает «паспорт» — оно проверяет подпись соответствующим публичным ключом.
4. Если подпись не сходится → **отказ работать** с этим устройством (что-то подменили).
5. Дополнительно проверяется `signedTimestamp` — должен быть не старше 7 дней (защита от «повтори старую подпись»).

**Зачем 2 ключа (X25519 + Ed25519):**
- X25519 хорош для шифрования, но **не умеет** подписи.
- Ed25519 хорош для подписи, но **не умеет** шифрования.
- libsodium даёт оба — генерируем оба, используем по назначению.

**Security Rules:**
- Любой член пары может **читать** ключи других членов.
- **Изменить** свой ключ может только сам владелец устройства.
- Сервер проверяет, что `signedTimestamp` свежий (не старше 7 дней) — minimal защита от replay-атак.
- Сервер **не проверяет** саму подпись — Firestore Rules не умеют crypto, проверка — на клиенте при чтении.

**Жизненный цикл:**
- При первом запуске приложения устройство **генерирует** обе пары ключей.
- После pairing'а **публикует** оба публичных ключа + подпись в Firestore.
- В будущем спеке **016** будем **ротировать** (обновлять) ключи периодически.
- При **revoke** link'а — весь документ удаляется через recursive subtree delete.

**Зачем нужно `deviceOwnership`:** защита от race condition «два устройства одновременно пытаются записать один и тот же deviceId». Только первый успевает заклеймить — остальные получают PERMISSION_DENIED.
