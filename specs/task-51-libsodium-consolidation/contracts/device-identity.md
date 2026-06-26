# Contract: DeviceIdentity (TASK-51 namespace migration)

**Wire format**: Firestore JSON документ под `/links/{linkId}/devices/{deviceId}`
**Schema version**: `1` (**unchanged** в TASK-51)
**Source spec**: spec 011 (e2e-crypto-foundation)
**Туда переезжает**: `com.launcher.api.crypto.DeviceIdentity` → `cryptokit.pairing.api.DeviceIdentity`

---

## Byte layout (JSON serialization via kotlinx.serialization)

```json
{
  "schemaVersion": 1,
  "deviceId": "550e8400-e29b-41d4-a716-446655440000",
  "publicKey": { "bytes": "<base64 32 bytes>" },
  "signingPublicKey": { "bytes": "<base64 32 bytes>" },
  "signedTimestamp": 1718937600000,
  "signature": "<base64 64 bytes>",
  "createdAt": 1718937600000
}
```

## Kotlin declaration

```kotlin
package cryptokit.pairing.api

@Serializable
@SerialName("DeviceIdentity")     // ← CRITICAL: explicit name prevents binary drift on namespace rename
data class DeviceIdentity(
    val schemaVersion: Int = 1,
    val deviceId: DeviceId,
    val publicKey: PublicKey,
    val signingPublicKey: SigningPublicKey,
    val signedTimestamp: Long,
    val signature: ByteArray,
    val createdAt: Long,
) {
    /**
     * Сериализованное представление полей, которые покрывает signature.
     * Используется при sign и verify. Order matters.
     */
    fun signedPayloadBytes(): ByteArray = /* ... */
}
```

## Field invariants

| Field | Validation |
|---|---|
| `schemaVersion` | MUST be `1`. Reader rejects unknown versions (forward compat не требуется в TASK-51 scope). |
| `deviceId` | UUIDv4 string, 36 chars (`xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx`). Same UUID что использует spec 007 PairingService. |
| `publicKey.bytes` | Exactly 32 bytes (X25519 pub key). |
| `signingPublicKey.bytes` | Exactly 32 bytes (Ed25519 pub key). |
| `signedTimestamp` | Unix millis. Reader verifies `signature` against this + other fields ДО возврата identity. SignatureVerifyFailed на tamper / stale. |
| `signature` | Exactly 64 bytes (Ed25519 detached signature over `signedPayloadBytes()`). |
| `createdAt` | Unix millis at document creation. ≤ `signedTimestamp`. |

## Read invariants

`fetchPeer(linkId, peerDeviceId)`:
1. Reads Firestore document.
2. Deserializes JSON via kotlinx.serialization (`@SerialName` matches).
3. **Verifies Ed25519 signature** over `signedPayloadBytes()` — без этого identity не возвращается.
4. On verify fail → `throws CryptoException.SerializationException` (was `Outcome.Failure(SignatureVerifyFailed)` в legacy).

## Forward / backward compatibility

- **Backward**: `schemaVersion: 1` остаётся; документы, написанные предыдущей версией кода, читаются после TASK-51 byte-equal (verified by `EnvelopeConfigCipherRoundtripTest` если он покрывает DeviceIdentity, иначе manual smoke).
- **Forward**: при появлении `schemaVersion: 2` в будущем — reader должен fall-through или explicit handle. В TASK-51 не вводится.

## Namespace migration risk

**Кritическое**: при namespace rename (`com.launcher.api.crypto.DeviceIdentity` → `cryptokit.pairing.api.DeviceIdentity`) kotlinx.serialization **по умолчанию** использует Kotlin class FQN как discriminator key. Если бы typing был polymorphic (`@Polymorphic`) — drift был бы немедленным. Для `data class DeviceIdentity` с `@SerialName("DeviceIdentity")` (string discriminator) — drift не происходит, потому что serializer использует `@SerialName` value, не FQN.

**Mitigation**: Phase 4 obligatory — verify `@SerialName(...)` присутствует **до** namespace rename.

---

## TL;DR (по-русски, для новичка и для будущего AI)

**Суть.** Контракт `DeviceIdentity` — JSON-документ в Firestore про одно устройство pairing-pары. В TASK-51 переезжает из пакета `com.launcher.api.crypto` в `cryptokit.pairing.api` **без изменения byte-layout**.

**Конкретика, которую стоит запомнить:**
- **8 полей**: `schemaVersion: 1`, `deviceId` (UUIDv4 36 chars), `publicKey` (X25519 32 байта), `signingPublicKey` (Ed25519 32 байта), `signedTimestamp/createdAt` (unix millis), `signature` (Ed25519 64 байта).
- **`@SerialName("DeviceIdentity")`** **обязателен** — без него kotlinx.serialization при namespace rename использует Kotlin FQN как discriminator → байт drift → Firestore documents становятся unreadable.
- **Read invariant**: `fetchPeer` всегда verify Ed25519 signature **до** возврата identity. Tamper / stale timestamp → `throws CryptoException.SerializationException`.
- **schemaVersion остаётся 1** — никакого breaking change для существующих Firestore documents.

**На что смотреть с осторожностью:**
- **Phase 4 obligatory verify**: до namespace rename → grep `@SerialName` на этом типе. Если отсутствует — добавить в **этот же commit** что и rename, иначе все Firestore documents с предыдущего deployment'а становятся нечитаемыми.
- **`signedPayloadBytes()` order matters** — изменение порядка полей внутри сломает все existing signatures. **Не трогать**.
