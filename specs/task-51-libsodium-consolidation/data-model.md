# Data Model: cryptokit (TASK-51)

**Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)

Описание новых типов, появляющихся / переименовывающихся в TASK-51.

**Note**: TASK-51 — refactor, не feature. Бо́льшая часть «новых» типов — это **уже существующие** типы под новыми именами (namespace rename) или **переезжающие** между пакетами (legacy `com.launcher.api.crypto.*` → `cryptokit.pairing.api.*`).

---

## 1. `cryptokit.crypto.exception.CryptoException` — sealed hierarchy

Существующий `family.crypto.exception.CryptoException` обогащается до 5 подклассов (FR-018).

```kotlin
package cryptokit.crypto.exception

sealed class CryptoException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {

    /** AEAD encrypt/decrypt failures (Poly1305 MAC mismatch, invalid ciphertext layout). */
    class AeadException(
        message: String,
        cause: Throwable? = null,
    ) : CryptoException(message, cause)

    /** SecureKeyStore failures (TEE unavailable, key invalidated, biometry change). */
    class KeyStoreException(
        message: String,
        cause: Throwable? = null,
    ) : CryptoException(message, cause)

    /** KDF/HKDF/Argon2 failures (invalid salt, weak input). */
    class KeyDerivationException(
        message: String,
        cause: Throwable? = null,
    ) : CryptoException(message, cause)

    /** JNI link errors (UnsatisfiedLinkError, missing native symbols). */
    class NativeLinkException(
        message: String,
        cause: Throwable? = null,
    ) : CryptoException(message, cause)

    /** Wire-format read/write failures (invalid schemaVersion, malformed bytes). */
    class SerializationException(
        message: String,
        cause: Throwable? = null,
    ) : CryptoException(message, cause)
}
```

**Lifetime**: created в adapter-слое при caught vendor exception → propagated up через uniform throws pattern → caught в universal `try/catch` в pairing-side ViewModel'ях.

**Relationships**:
- `AeadException` cause обычно — `com.ionspin.kotlin.crypto.aead.AeadCorrupedOrTamperedDataException`
- `KeyStoreException` cause — `android.security.KeyStoreException` или `javax.crypto.AEADBadTagException`
- `NativeLinkException` cause — `java.lang.UnsatisfiedLinkError`
- `SerializationException` cause — `kotlinx.serialization.SerializationException`

---

## 2. `cryptokit.pairing.api.*` package — 15 wire-format типов (мигрируют из legacy)

Все типы существуют в `com.launcher.api.crypto.*` (старая стопка). TASK-51 переносит их в новый namespace **без изменения byte-layout** (FR-004). Каждый тип получает explicit `@SerialName(...)` annotation.

### 2.1 `DeviceIdentity` (wire format spec 011)

```kotlin
package cryptokit.pairing.api

@Serializable
@SerialName("DeviceIdentity")
data class DeviceIdentity(
    val schemaVersion: Int = 1,
    val deviceId: DeviceId,
    val publicKey: PublicKey,                  // X25519 pub
    val signingPublicKey: SigningPublicKey,    // Ed25519 pub
    val signedTimestamp: Long,                 // unix millis
    val signature: ByteArray,                  // Ed25519 signature over signedPayloadBytes()
    val createdAt: Long,
) {
    fun signedPayloadBytes(): ByteArray = /* ... */
}
```

**Storage**: Firestore `/links/{linkId}/devices/{deviceId}` document.
**Schema version**: 1 (unchanged).
**Read backward compat**: TASK-51 не меняет layout — старые документы читаются новым кодом.

### 2.2 `DeviceId` (value class wrapper)

```kotlin
@Serializable
@SerialName("DeviceId")
@JvmInline
value class DeviceId(val value: String)  // UUIDv4 string
```

### 2.3 `PublicKey` / `SigningPublicKey`

```kotlin
@Serializable @SerialName("PublicKey")
data class PublicKey(val bytes: ByteArray)              // 32 bytes X25519

@Serializable @SerialName("SigningPublicKey")
data class SigningPublicKey(val bytes: ByteArray)       // 32 bytes Ed25519
```

### 2.4 `DeviceKeyPair` / `DeviceSigningKeyPair`

```kotlin
data class DeviceKeyPair(val publicKey: PublicKey, val privateKey: PrivateKey)
data class DeviceSigningKeyPair(val publicKey: SigningPublicKey, val privateKey: SigningPrivateKey)
```
**Not serialized** (private keys never leave device).

### 2.5 `PrivateKey` / `SigningPrivateKey` (sealed opaque)

```kotlin
sealed interface PrivateKey       // X25519 priv — opaque
sealed interface SigningPrivateKey // Ed25519 priv — opaque

class InMemoryPrivateKey(val bytes: ByteArray) : PrivateKey
class InMemorySigningPrivateKey(val bytes: ByteArray) : SigningPrivateKey
```

**Purpose**: opaque wrapper чтобы code не мог случайно сериализовать priv bytes. `InMemory*` — test-only implementations (production уходит через `SecureKeyStore`).

### 2.6 `EncryptedEnvelope` (wire format spec 011)

```kotlin
@Serializable
@SerialName("EncryptedEnvelope")
data class EncryptedEnvelope(
    val schemaVersion: Int = 1,
    val recipients: List<Recipient>,         // per-recipient sealed-box of CEK
    val nonce: ByteArray,                    // 24 bytes XChaCha20
    val ciphertext: ByteArray,
    val mac: ByteArray,                      // 16 bytes Poly1305
    val aad: ByteArray? = null,
)
```

### 2.7 `Recipient` (sealed CEK для одного peer)

```kotlin
@Serializable
@SerialName("Recipient")
data class Recipient(
    val deviceId: DeviceId,
    val sealedCEK: ByteArray,    // X25519-sealed CEK (80 bytes: 32 ephemeral pub + 32 ct + 16 mac)
)
```

### 2.8 `ContentEncryptionKey`

```kotlin
class ContentEncryptionKey(private val bytes: ByteArray) : AutoCloseable {
    fun useBytes(block: (ByteArray) -> Unit) { /* zeroize after */ }
    override fun close() { bytes.fill(0) }
}
```

**Not serialized**. Zeroize-on-close pattern (опционально, debug-debug-activity использует `MessageDigest` для fingerprint напрямую, не через CEK).

### 2.9 `DeviceIdentityRepository` (port)

```kotlin
package cryptokit.pairing.api

interface DeviceIdentityRepository {
    /** @throws CryptoException.SerializationException on malformed Firestore document */
    suspend fun publishOwn(linkId: String, identity: DeviceIdentity)

    /** @throws CryptoException on signature verify fail или document missing */
    suspend fun fetchPeer(linkId: String, peerDeviceId: DeviceId): DeviceIdentity

    suspend fun listAll(linkId: String): List<DeviceIdentity>
}
```

**Signature**: переписан с `Outcome<T, CryptoError>` на `throws CryptoException` (FR-009).

### 2.10 `EncryptedMediaStorage` (port)

```kotlin
package cryptokit.pairing.api

interface EncryptedMediaStorage {
    suspend fun upload(linkId: String, uuid: Uuid, envelope: EncryptedEnvelope)
    suspend fun download(linkId: String, uuid: Uuid): EncryptedEnvelope
    suspend fun delete(linkId: String, uuid: Uuid)
    suspend fun exists(linkId: String, uuid: Uuid): Boolean
    suspend fun list(linkId: String): List<Uuid>
}
```

### 2.11 `RecipientResolver` (port)

```kotlin
package cryptokit.pairing.api

fun interface RecipientResolver {
    suspend fun resolveRecipients(linkId: String): List<DeviceIdentity>
}
```

### 2.12 `CryptoEnvelopeWireFormat` (constants)

```kotlin
package cryptokit.pairing.api

object CryptoEnvelopeWireFormat {
    const val XCHACHA20_NONCE_SIZE = 24
    const val POLY1305_MAC_SIZE = 16
    const val SEALED_CEK_SIZE = 80
    const val X25519_KEY_SIZE = 32
    const val ED25519_KEY_SIZE = 32
    const val ED25519_SIGNATURE_SIZE = 64
    const val SUPPORTED_SCHEMA_VERSION = 1
}
```

---

## 3. Что **удаляется** (старые типы)

| Type | Reason |
|---|---|
| `com.launcher.api.crypto.AeadCipher` | Дубликат — есть `cryptokit.crypto.api.AeadCipher` |
| `com.launcher.api.crypto.AsymmetricCrypto` | Дубликат — есть `cryptokit.crypto.api.AsymmetricCrypto` (с `sign`/`verify` встроенными) |
| `com.launcher.api.crypto.DigitalSignature` | Покрывается `cryptokit.crypto.api.AsymmetricCrypto.sign/verify` |
| `com.launcher.api.crypto.HashFunction` | Removed — inline `MessageDigest.SHA-256` в одном debug-activity (FR-014) |
| `com.launcher.api.crypto.SecureKeystore` | Заменяется `cryptokit.crypto.api.SecureKeyStore` (expect/actual из spec 016) |
| `com.launcher.api.crypto.CryptoError` (Outcome-based sealed) | Заменяется `cryptokit.crypto.exception.CryptoException` (throws) |
| `com.launcher.api.result.Outcome` (если orphan) | Возможно остаётся для других модулей — проверить grep в Phase 5 |

---

## 4. Inline TODO markers для post-TASK-51

В коде Phase 5 (PairingCryptoCoordinator rewrite) обязательны:

```kotlin
// TODO(post-task-6): replace read-old-then-re-encrypt with derive-from-root after Root Key Hierarchy lands
// Появляется в SecureKeyStore wrapper или legacy migration utility (FR-005).
```

```kotlin
// TODO(post-task-8): pair-admin step returns as SystemSetting в wizard manifest
// Появляется в local-actions config если есть placeholder.
```

---

## TL;DR (по-русски, для новичка и для будущего AI)

**Суть.** Описание типов, которые либо появляются (5 подклассов `CryptoException`), либо переезжают в новый namespace `cryptokit.pairing.api.*` (15 spec 011 wire-format типов: `DeviceIdentity`, `EncryptedEnvelope`, `Recipient`, `DeviceIdentityRepository`, `EncryptedMediaStorage` и др.), либо удаляются (5 криптопортов из старой стопки, заменены ionspin-эквивалентами).

**Конкретика, которую стоит запомнить:**
- **5 подклассов `CryptoException`**: `AeadException`, `KeyStoreException`, `KeyDerivationException`, `NativeLinkException`, `SerializationException`. Sealed → exhaustive `when` возможен.
- **15 типов мигрируют в `cryptokit.pairing.api.*`** (полный список выше): wire-format спеки 011, **byte-layout не меняется**, добавляется `@SerialName(...)` на каждый.
- **7 типов удаляются полностью**: `AeadCipher`, `AsymmetricCrypto`, `DigitalSignature`, `HashFunction`, `SecureKeystore`, `CryptoError`, `Outcome` (если orphan).
- **`schemaVersion` остаётся 1** во всех wire-format — никакого breaking change для Firestore-документов.
- **Inline-TODO к TASK-6** в migration utility — после Root Key Hierarchy эта код-логика заменится derive-from-root.

**На что смотреть с осторожностью:**
- **`@SerialName(...)` audit** — критично перед namespace rename. Без них Kotlin serializer использует FQN класса как key → byte drift → unreadable Firestore documents → SC-013 fail.
- **`ContentEncryptionKey` zeroize-on-close** — security pattern, easy потерять при rewrite. Verify что pattern сохранён в pairing-side rewrite.
- **`com.launcher.api.result.Outcome`** — может остаться orphan в коде после TASK-51, проверить нужно ли удалить.
