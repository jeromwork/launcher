# Data Model: F-CRYPTO

**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)

Domain ports, value types, exception hierarchy. Source-of-truth for `core/crypto/api/`.

---

## Value types

### `KeyId`

```kotlin
// commonMain/api/values/KeyId.kt
@JvmInline
value class KeyId(val raw: String) {
  init {
    require(KeyNamespace.isValidPrefix(raw)) {
      "Invalid KeyId '$raw': must start with one of: ${KeyNamespace.allPrefixes()}"
    }
    require(raw.matches(Regex("^[a-z_-]+(-[a-z0-9-]+)+$"))) {
      "Invalid KeyId '$raw': must be kebab-case ASCII (letters, digits, hyphens)"
    }
  }
}
```

Notes:
- `value class` — zero runtime overhead, compile-time type safety.
- Regex enforces format: lowercase, kebab-case, no special chars.

### `KeyNamespace`

```kotlin
// commonMain/api/values/KeyNamespace.kt
sealed class KeyNamespace(val prefix: String) {
  object Config : KeyNamespace("config-")
  object Media : KeyNamespace("media-")
  object Messenger : KeyNamespace("messenger-")
  object Recovery : KeyNamespace("recovery-")
  object Internal : KeyNamespace("__internal-")

  companion object {
    private val all: List<KeyNamespace> by lazy {
      listOf(Config, Media, Messenger, Recovery, Internal)
    }
    fun isValidPrefix(raw: String): Boolean = all.any { raw.startsWith(it.prefix) }
    fun allPrefixes(): List<String> = all.map { it.prefix }
  }
}
```

### Cryptographic value types

```kotlin
// commonMain/api/values/KeyPair.kt
data class KeyPair(
  val privateKey: ByteArray,
  val publicKey: ByteArray,
  val algorithm: String   // "X25519" | "Ed25519"
) {
  // equals/hashCode/toString must NOT log privateKey
  override fun toString(): String = "KeyPair(algorithm=$algorithm, publicKey=<${publicKey.size} bytes>, privateKey=<REDACTED>)"
  // equals + hashCode based on publicKey only (privateKey может различаться при rotation)
}

// commonMain/api/values/SharedSecret.kt
@JvmInline
value class SharedSecret(val bytes: ByteArray) {
  // Wraps 32 bytes from X25519 ECDH. ByteArray equals/hashCode pass-through.
}

// commonMain/api/values/Signature.kt
@JvmInline
value class Signature(val bytes: ByteArray) {
  // Ed25519 signature = 64 bytes.
}

// commonMain/api/values/SealedBlob.kt
@JvmInline
value class SealedBlob(val bytes: ByteArray) {
  // libsodium crypto_box_seal output: 48 + plaintext.size bytes
  // (ephemeral pubkey + ciphertext + mac).
}

// commonMain/api/values/Ciphertext.kt
@JvmInline
value class Ciphertext(val bytes: ByteArray) {
  // XChaCha20-Poly1305 envelope: nonce(24) + ciphertext + mac(16).
}
```

### `KeyBlob` (wire format)

См. отдельный contract [contracts/key-blob-v1.md](contracts/key-blob-v1.md).

```kotlin
// commonMain/api/values/KeyBlob.kt
@Serializable
data class KeyBlob(
  val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
  val algorithm: String,
  val createdAt: Instant,           // kotlinx.datetime.Instant
  val retiredAt: Instant? = null,
  val replacedBy: String? = null,    // KeyId.raw of replacement
  val wrappedKey: ByteArray,
  val iv: ByteArray,
  val wrapKeyAlias: String          // Android Keystore alias used for wrapping
) {
  companion object {
    const val CURRENT_SCHEMA_VERSION = 1
  }
  // toString MUST NOT log wrappedKey / iv
}
```

### Stub-only types (for future spec'и)

```kotlin
// commonMain/api/values/RotationReason.kt
sealed class RotationReason {
  object Periodic : RotationReason()
  object SuspectedCompromise : RotationReason()
  object DeviceChange : RotationReason()
  data class Custom(val reason: String) : RotationReason()
}

// commonMain/api/values/RetiredKey.kt
data class RetiredKey(
  val keyId: KeyId,
  val retiredAt: Instant,
  val reason: RotationReason,
  val replacedBy: KeyId?
)

// commonMain/api/values/EscrowBundle.kt
data class EscrowBundle(
  val schemaVersion: Int,
  val externalId: String,
  val encryptedPayload: ByteArray,
  val createdAt: Instant
) {
  // Used in future spec (TBD) social recovery; F-CRYPTO declares shape, no real serialization
}
```

---

## Domain ports

### `AeadCipher`

```kotlin
// commonMain/api/AeadCipher.kt
interface AeadCipher {
  /**
   * Encrypts plaintext using AEAD with a randomly-generated nonce.
   *
   * The nonce is generated internally by the adapter and embedded in the
   * returned [Ciphertext]. Callers MUST NOT supply or extract the nonce —
   * doing so would risk nonce reuse, which destroys AEAD security.
   *
   * @param plaintext raw bytes to encrypt
   * @param key 32-byte symmetric key (from [KeyDerivation] or [RandomSource])
   * @param aad additional authenticated data (not encrypted but authenticated)
   * @return ciphertext envelope (nonce + ciphertext + MAC)
   * @throws CryptoException.RandomSourceUnavailable if RandomSource fails
   */
  suspend fun encrypt(
    plaintext: ByteArray,
    key: ByteArray,
    aad: ByteArray = ByteArray(0)
  ): Ciphertext

  /**
   * Decrypts ciphertext envelope. Extracts embedded nonce.
   *
   * @throws CryptoException.DecryptionFailed if MAC verification fails
   *   (corruption, tampering, or wrong key)
   * @throws CryptoException.MalformedCiphertext if envelope structure invalid
   */
  suspend fun decrypt(
    ciphertext: Ciphertext,
    key: ByteArray,
    aad: ByteArray = ByteArray(0)
  ): ByteArray
}
```

### `AsymmetricCrypto`

```kotlin
// commonMain/api/AsymmetricCrypto.kt
interface AsymmetricCrypto {
  /** Generates a new X25519 keypair (for ECDH key agreement). */
  suspend fun generateX25519KeyPair(): KeyPair

  /** Generates a new Ed25519 keypair (for digital signatures). */
  suspend fun generateEd25519KeyPair(): KeyPair

  /**
   * Performs X25519 ECDH key agreement.
   *
   * @param myPrivate 32-byte X25519 private key
   * @param theirPublic 32-byte X25519 public key from peer
   * @return shared 32-byte secret (use as input to [KeyDerivation])
   * @throws CryptoException.InvalidPublicKey on low-order or malformed keys
   */
  suspend fun deriveSharedSecret(myPrivate: ByteArray, theirPublic: ByteArray): SharedSecret

  /**
   * Signs message with Ed25519 private key.
   * @return 64-byte signature
   */
  suspend fun sign(message: ByteArray, privateKey: ByteArray): Signature

  /**
   * Verifies Ed25519 signature.
   * @return true if signature valid, false otherwise (NEVER throws on invalid signature)
   */
  suspend fun verify(signature: Signature, message: ByteArray, publicKey: ByteArray): Boolean

  /**
   * Seals (encrypts) a small payload for a specific recipient using sealed-box.
   * Sender is anonymous (ephemeral keypair used internally).
   *
   * Used for: ADR-008 social recovery (encrypt peer_nonce for trusted peer).
   *
   * @param payload typically 16-32 bytes (e.g., a CEK to be sealed)
   * @param recipientPublicKey X25519 public key of recipient
   * @return sealed blob (48 + payload.size bytes)
   */
  suspend fun sealForRecipient(payload: ByteArray, recipientPublicKey: ByteArray): SealedBlob

  /**
   * Opens (decrypts) a sealed blob with recipient's private key.
   * @throws CryptoException.DecryptionFailed if not addressed to this recipient
   */
  suspend fun openSealed(blob: SealedBlob, recipientPrivateKey: ByteArray): ByteArray
}
```

### `KeyDerivation`

```kotlin
// commonMain/api/KeyDerivation.kt
interface KeyDerivation {
  /**
   * HKDF-SHA256 key derivation.
   *
   * @param ikm input keying material (e.g., shared secret from ECDH)
   * @param salt cryptographic salt (any byte string; can be empty)
   * @param info context info — separates derived keys for different purposes
   *   (e.g., "config-key-v1", "launcher-recovery-aead-v1")
   * @param length output length in bytes (typically 32 for AEAD key)
   */
  suspend fun derive(ikm: ByteArray, salt: ByteArray, info: String, length: Int): ByteArray
}
```

### `RandomSource`

```kotlin
// commonMain/api/RandomSource.kt
interface RandomSource {
  /**
   * Generates cryptographically-secure random bytes.
   *
   * @param size number of bytes to generate
   * @throws CryptoException.RandomSourceUnavailable if entropy source unavailable
   */
  suspend fun nextBytes(size: Int): ByteArray
}
```

### `SecureKeyStore` (expect class)

```kotlin
// commonMain/api/SecureKeyStore.kt
expect class SecureKeyStore {

  /**
   * Stores a private key under [keyId], wrapped by platform-specific protection.
   * On Android: wrapped by Android Keystore AES key in TEE.
   * On iOS: wrapped by Keychain.
   * On JVM (test-only): in-memory HashMap.
   *
   * @throws CryptoException.KeystoreUnavailable if TEE not available
   * @throws CryptoException.KeystoreInvalidated if TEE alias was removed externally
   */
  suspend fun store(keyId: KeyId, secret: ByteArray)

  /**
   * Loads a previously-stored key.
   * @return secret bytes, or null if [keyId] not found (or device wiped)
   */
  suspend fun load(keyId: KeyId): ByteArray?

  /**
   * Deletes a stored key. Idempotent (does not throw if missing).
   */
  suspend fun delete(keyId: KeyId)
}
```

### `KeyRotation` (interface-only stub)

```kotlin
// commonMain/api/KeyRotation.kt
interface KeyRotation {
  fun currentKeyId(purpose: KeyNamespace): KeyId
  fun keyHistory(purpose: KeyNamespace): List<RetiredKey>
  suspend fun rotateIdentityKey(purpose: KeyNamespace, reason: RotationReason): KeyId
  suspend fun revoke(keyId: KeyId, reason: RotationReason)
}

// commonMain/stubs/StubKeyRotation.kt
class StubKeyRotation : KeyRotation {
  override fun currentKeyId(purpose: KeyNamespace): KeyId = TODO("Implement in future multi-device-recovery spec (TBD)")
  override fun keyHistory(purpose: KeyNamespace): List<RetiredKey> = emptyList()
  override suspend fun rotateIdentityKey(purpose: KeyNamespace, reason: RotationReason): KeyId =
    throw NotImplementedError("Key rotation real-impl deferred to future spec (TBD) — see ADR-008")
  override suspend fun revoke(keyId: KeyId, reason: RotationReason): Unit =
    throw NotImplementedError("Key revocation real-impl deferred to future spec (TBD) — see ADR-008")
}
```

### `KeyEscrow` (interface-only stub)

```kotlin
// commonMain/api/KeyEscrow.kt
interface KeyEscrow {
  /**
   * Exports a passphrase-encrypted backup of keys.
   * Real implementation in future spec (TBD) — social recovery per ADR-008.
   */
  suspend fun export(passphrase: ByteArray): EscrowBundle

  /**
   * Restores keys from an EscrowBundle using the passphrase.
   */
  suspend fun restore(bundle: EscrowBundle, passphrase: ByteArray)
}

// commonMain/stubs/StubKeyEscrow.kt
class StubKeyEscrow : KeyEscrow {
  override suspend fun export(passphrase: ByteArray): EscrowBundle =
    throw NotImplementedError("KeyEscrow real-impl deferred to future spec (TBD) — see ADR-008")
  override suspend fun restore(bundle: EscrowBundle, passphrase: ByteArray): Unit =
    throw NotImplementedError("KeyEscrow real-impl deferred to future spec (TBD) — see ADR-008")
}
```

---

## Exception hierarchy

```kotlin
// commonMain/exception/CryptoException.kt
sealed class CryptoException(message: String, cause: Throwable? = null) : Exception(message, cause) {

  /** AEAD decryption failed — MAC mismatch, tampering, or wrong key. */
  class DecryptionFailed(message: String = "AEAD decryption MAC mismatch") : CryptoException(message)

  /** Ciphertext envelope structure invalid (e.g., too short). */
  class MalformedCiphertext(message: String) : CryptoException(message)

  /** Invalid public key (low-order point, point-at-infinity, malformed). */
  class InvalidPublicKey(message: String) : CryptoException(message)

  /** Random source unavailable (e.g., entropy pool empty). */
  class RandomSourceUnavailable(cause: Throwable? = null) : CryptoException("Random source unavailable", cause)

  /** Android Keystore / iOS Keychain not available (e.g., rooted device without TEE). */
  class KeystoreUnavailable(message: String, cause: Throwable? = null) : CryptoException(message, cause)

  /** Keystore alias was invalidated externally (e.g., Xiaomi MIUI cleanup, biometry changed). */
  class KeystoreInvalidated(message: String, cause: Throwable? = null) : CryptoException(message, cause)

  /** KeyBlob has schemaVersion higher than current code can read. */
  class UnsupportedSchemaVersion(found: Int, known: Int) :
    CryptoException("Cannot read KeyBlob with schemaVersion=$found (max known: $known)")

  /** KeyBlob deserialization failed (corrupt file, malformed JSON). */
  class KeyBlobDeserializationFailed(message: String, cause: Throwable? = null) : CryptoException(message, cause)

  /** Wycheproof rejection (e.g., low-order X25519 point detected). */
  class WycheproofRejection(message: String) : CryptoException(message)

  /** Caller attempted to encrypt twice with the same key+nonce (detected by test or property test). */
  class NonceReuseDetected(message: String) : CryptoException(message)

  /** iOS adapter not yet implemented. */
  class NotImplementedOnIos(message: String) : CryptoException(message)
}
```

---

## Lifecycle

### Initialization order

1. `:app` starts.
2. Koin DI module `CryptoModule` is loaded.
3. DI picks adapters based on build variant:
   - `release` → `LibsodiumAeadCipher`, `LibsodiumAsymmetricCrypto`, ..., `KeystoreSecureKeyStore`, `StubKeyRotation`, `StubKeyEscrow`.
   - `debug` → same as release (Fake* are TEST-only, not for runtime apps).
4. F-CRYPTO does **not** initialize libsodium at startup (lazy on first use).
5. First use → `libsodium.init()` called inside adapter (ionspin handles this).

### Initialization assertion

In `Application.onCreate()` (or first F-CRYPTO usage point):

```kotlin
val aeadCipher: AeadCipher = get()
check(aeadCipher !is FakeAeadCipher) {
  "FATAL: FakeAeadCipher detected in release build. " +
  "Fake adapters MUST NOT be wired in production DI. " +
  "Check CryptoModule.kt and build variant."
}
```

### Tear-down

F-CRYPTO has no global state to tear down. Each operation is stateless from F-CRYPTO's perspective. `SecureKeyStore` may hold a reference to Android `Context` — lifecycle managed by `:app`.

---

## Cross-platform compatibility table

| Type | commonMain | androidMain | jvmMain | iosMain |
|---|---|---|---|---|
| `AeadCipher` | interface | LibsodiumAeadCipher | (same — uses ionspin) | (same — uses ionspin) |
| `AsymmetricCrypto` | interface | LibsodiumAsymmetricCrypto | (same) | (same) |
| `KeyDerivation` | interface | LibsodiumKeyDerivation | (same) | (same) |
| `RandomSource` | interface | LibsodiumRandomSource | (same) | (same) |
| `SecureKeyStore` | expect class | actual: KeystoreSecureKeyStore | actual: InMemorySecureKeyStore (test) | actual: stub-screamer |
| `KeyRotation` | interface | StubKeyRotation | StubKeyRotation | StubKeyRotation |
| `KeyEscrow` | interface | StubKeyEscrow | StubKeyEscrow | StubKeyEscrow |
| `KeyId`, `KeyBlob`, etc. | data class / value class | (shared) | (shared) | (shared) |

---

## Notes for implementation phase

- **Suspend functions everywhere**: even simple `nextBytes` is suspend. Reason: future iOS adapter может потребовать suspend (Keychain async), и common API не должен меняться.
- **ByteArray equality is reference-based**: `data class` с `ByteArray` member нужно override `equals`/`hashCode` или использовать `@JvmInline value class` wrapper (мы используем второй pattern для `SharedSecret`, `Signature`, `SealedBlob`, `Ciphertext`).
- **No KDoc должен log raw key bytes**: convention для plan-фазы — `@SensitiveByteArray` annotation (custom) или explicit KDoc note.
- **Кросс-platform Instant**: `kotlinx.datetime.Instant` через `kotlinx.serialization` поддерживается из коробки.

---

## TL;DR простым языком

Это **список всех типов данных и функций**, которые будут в крипто-модуле. Чтобы программист, который будет писать код, знал «вот такие интерфейсы я должен реализовать, вот такие классы данных у меня есть».

Ключевые группы:
- **Идентификаторы ключей** (`KeyId`, `KeyNamespace`) — с защитой от опечаток на этапе компиляции.
- **Криптографические типы** (`KeyPair`, `SharedSecret`, `Signature`, `SealedBlob`, `Ciphertext`) — для функций ввода-вывода.
- **Сам "обёрнутый ключ"** (`KeyBlob`) — структура файла, где лежат ключи на диске.
- **Интерфейсы для разных операций** — шифрование (`AeadCipher`), асимметричные ключи (`AsymmetricCrypto`), производство ключей (`KeyDerivation`), генерация случайностей (`RandomSource`), сейф (`SecureKeyStore`), ротация (`KeyRotation` — заглушка), резервная копия (`KeyEscrow` — заглушка).
- **Иерархия ошибок** — конкретные классы исключений для каждой ситуации (например, «не удалось расшифровать», «защищённый чип недоступен», «формат файла устарел»).
- **Таблица «что где живёт»** — какие классы в `commonMain` (общий код), какие в `androidMain` (Android-специфика), какие в `iosMain` (iOS-специфика — пока заглушки).
