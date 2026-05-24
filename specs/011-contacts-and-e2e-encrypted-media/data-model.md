# Data Model: E2E Crypto Foundation

**Spec**: [spec.md](spec.md)
**Plan**: [plan.md](plan.md)
**Date**: 2026-05-21 / rev. 2 2026-05-22 (scope-split — visible feature вынесена в спек 012; добавлены DigitalSignature + HashFunction)

Этот документ описывает domain-уровни типы (Kotlin commonMain), persisted local types (SQLDelight schemas), и их семантику для **криптофундамента**. Wire-format типы — отдельно в [contracts/](contracts/).

**Scope 011**: только инфраструктура — domain types, ports, BlobReferenceLedger. **Не включает** в 011:
- `PrivateMediaReference` (string wrapper `"private:<uuid>"`) — переезжает в спек 012, потому что только клиент-код (resolver IconStorage) его создаёт.
- `PrivateMediaCache` (расшифрованные blob bytes) — переезжает в спек 012, потому что только клиент кеширует расшифрованное.
- `BlobMetadata.kind` (Image/Document discriminator) — переезжает в спек 012; envelope в 011 нейтрален к content type, metadata — freeform CBOR map.

---

## §1. Domain types (commonMain pure Kotlin)

### `DeviceId`

```kotlin
@JvmInline @Serializable
value class DeviceId(val value: String) {
    init { require(isUuidV4(value)) { "DeviceId must be UUIDv4" } }
    companion object {
        @OptIn(ExperimentalUuidApi::class)
        fun random(): DeviceId = DeviceId(Uuid.random().toString())
    }
}
```

- **Purpose**: однозначный идентификатор физического устройства; persisted локально, публикуется в `/links/{linkId}/devices/{deviceId}`.
- **Lifetime**: создаётся при первом запуске приложения. Persists across app updates. Reset on factory reset / app data clear.

### `PublicKey` (X25519)

```kotlin
@JvmInline @Serializable
value class PublicKey(val bytes: ByteArray) {
    init { require(bytes.size == 32) { "X25519 public key must be 32 bytes" } }
    override fun equals(other: Any?): Boolean = ...  // byte-by-byte
    override fun hashCode(): Int = bytes.contentHashCode()
}
```

- **Purpose**: 32-byte X25519 public key для encryption (sealing CEK).
- **Note**: serializable (публикуется в Firestore base64-encoded).

### `SigningPublicKey` (Ed25519)

```kotlin
@JvmInline @Serializable
value class SigningPublicKey(val bytes: ByteArray) {
    init { require(bytes.size == 32) { "Ed25519 public key must be 32 bytes" } }
    override fun equals(other: Any?): Boolean = ...
    override fun hashCode(): Int = bytes.contentHashCode()
}
```

- **Purpose**: 32-byte Ed25519 public key для signature verification.
- **Note**: serializable; публикуется вместе с X25519 PublicKey в `/links/{linkId}/devices/{deviceId}`.

### `PrivateKey` (opaque, X25519)

```kotlin
sealed interface PrivateKey {
    val alias: String   // Android Keystore alias (resolved via AES-wrap)

    /** Не Serializable. НЕ должен покидать устройство. */
}
```

- **Purpose**: opaque handle к приватному X25519 ключу. Сам Keystore не поддерживает X25519 нативно — используется AES-wrap (AES-ключ в Keystore оборачивает X25519 priv bytes, которые хранятся в EncryptedSharedPreferences).
- **Lifetime**: создаётся при первом запуске; удаляется при revoke link OR app data clear.
- **NOT Serializable. NOT extractable bytes.**

### `SigningPrivateKey` (opaque, Ed25519)

```kotlin
sealed interface SigningPrivateKey {
    val alias: String   // Android Keystore alias

    /** Не Serializable. НЕ должен покидать устройство. */
}
```

- **Purpose**: opaque handle к приватному Ed25519 ключу. Android Keystore поддерживает Ed25519 нативно с API 31+; на API 30 fallback на AES-wrap (тот же паттерн, что для X25519).
- **Lifetime**: same as PrivateKey.

### `DeviceKeyPair` (X25519 — encryption)

```kotlin
data class DeviceKeyPair(
    val publicKey: PublicKey,
    val privateKey: PrivateKey,
)
```

- Internal struct. Only construction site — `AsymmetricCrypto.generateX25519Pair()`.

### `DeviceSigningKeyPair` (Ed25519 — signing)

```kotlin
data class DeviceSigningKeyPair(
    val publicKey: SigningPublicKey,
    val privateKey: SigningPrivateKey,
)
```

- Internal struct. Only construction site — `DigitalSignature.generateEd25519Pair()`.

### `DeviceIdentity`

```kotlin
@Serializable
data class DeviceIdentity(
    val schemaVersion: Int = 1,
    val deviceId: DeviceId,
    val publicKey: PublicKey,                  // X25519 — encryption
    val signingPublicKey: SigningPublicKey,    // Ed25519 — signing
    val signedTimestamp: Long,                 // epoch millis, inside signed payload — freshness check
    val signature: ByteArray,                  // Ed25519 signature над {deviceId, publicKey, signingPublicKey, signedTimestamp}
    val createdAt: Long,                       // epoch millis (Firestore-set on write)
)
```

- **Purpose**: serializable form of device identity, published to Firestore `/links/{linkId}/devices/{deviceId}`.
- **signature**: подпись над {deviceId || publicKey || signingPublicKey || signedTimestamp} приватным Ed25519 ключом владельца. Защита от подмены через compromise Firestore document.
- **signedTimestamp**: epoch millis on signature creation. Security Rule rejects publication if `now - signedTimestamp > 7 days` (replay-attack mitigation).
- **Wire format**: см. [contracts/device-identity.md](contracts/device-identity.md).

### `ContentEncryptionKey` (CEK)

```kotlin
class ContentEncryptionKey(internal val bytes: ByteArray) : AutoCloseable {
    init { require(bytes.size == 32) { "CEK must be 32 bytes" } }

    /** Zeroes out the bytes; throws if accessed afterwards. */
    override fun close() { bytes.fill(0) }
}

/** Kotlin idiom: гарантирует CEK.close() даже при exception */
inline fun <T> ContentEncryptionKey.use(block: (ContentEncryptionKey) -> T): T =
    try { block(this) } finally { this.close() }
```

- **Purpose**: разовый симметричный ключ для шифрования **одного** blob'a.
- **Lifecycle**: создаётся `AeadCipher.generateCEK()` → используется для AEAD.encrypt() → sealed для каждого recipient → закрывается (zero'ed). После использования НЕ хранится.
- **NOT Serializable.**
- **Note**: использовать `.use { }` block везде — обнуление в finally обязательно (CHK-STATE-005).

### `Recipient`

```kotlin
@Serializable
data class Recipient(
    val deviceId: DeviceId,
    val sealedCEK: ByteArray,  // crypto_box_seal output: ephemeral_pub || encrypted_cek; libsodium size = 32 + 32 + 16 = 80 bytes
) {
    override fun equals(...) = ...  // including bytes
}
```

- **Purpose**: один элемент `recipients[]` массива в envelope. Описывает: «для этого deviceId зашифрованный CEK выглядит так».

### `EncryptedEnvelope`

```kotlin
@Serializable
data class EncryptedEnvelope(
    val schemaVersion: Int,             // 1
    val cipherSuiteId: String,          // "xchacha20poly1305_x25519_sealed_v1"
    val nonce: ByteArray,               // 24 bytes for XChaCha20 (192-bit extended nonce)
    val recipients: List<Recipient>,
    val ciphertext: ByteArray,
    val mac: ByteArray,                 // 16 bytes Poly1305 (часто комбинируется с ciphertext в libsodium; отдельный для test clarity)
    val metadata: Map<String, ByteArray> = emptyMap(),  // freeform CBOR map; client specs (012+) определяют ключи
) {
    init {
        require(recipients.isNotEmpty()) { "envelope must have at least 1 recipient" }
        require(nonce.size == 24) { "XChaCha20 nonce must be 24 bytes" }
    }
}
```

- **Purpose**: serializable форма зашифрованного blob'a, persisted в Firebase Storage. Wire format — см. [contracts/crypto-envelope.md](contracts/crypto-envelope.md).
- **metadata**: freeform map для AAD (associated authenticated data). Envelope в 011 нейтрален к содержанию — client specs определяют свои ключи. **Внимание клиенту**: sensitive поля (например, label документа из спека 012) MUST шифроваться внутри ciphertext, **не** класться в metadata — metadata выходит из envelope в plaintext.
- **Invariants**: recipients ≥ 1 (в 011 всегда 1); nonce размер фиксирован cipherSuiteId.

### `CryptoError` (sealed)

```kotlin
sealed interface CryptoError {
    data class KeyNotFound(val alias: String, val cause: Throwable? = null) : CryptoError
    data class MacFailed(val uuid: Uuid) : CryptoError
    data class BlobMissing(val uuid: Uuid) : CryptoError
    data class CipherSuiteUnsupported(val suiteId: String) : CryptoError
    data class RecipientNotFound(val deviceId: DeviceId) : CryptoError
    data class SignatureVerifyFailed(val deviceId: DeviceId) : CryptoError
    data class MalformedEnvelope(val uuid: Uuid, val cause: Throwable? = null) : CryptoError  // CBOR parse failure, schema mismatch
    data class StorageFailure(val cause: Throwable) : CryptoError
    data class KeystoreFailure(val cause: Throwable) : CryptoError
}
```

- **Purpose**: typed error hierarchy. Все ошибки возвращаются как `Result<T, CryptoError>`, никогда не Exception. Каждая ошибка — categorical metadata (без plaintext/keys).

### `BlobReference`

```kotlin
data class BlobReference(
    val uuid: Uuid,
    val linkId: LinkId,
    val refSource: String,           // "config-current" | "history-slot-0" .. "history-slot-9" | "pending-draft"
    val refUpdatedAt: Long,          // epoch millis
)
```

- **Purpose**: один ledger entry. Считает источники reference на blob.

---

## §2. Ports (commonMain interfaces)

### `AeadCipher`

```kotlin
interface AeadCipher {
    /** Encrypts plaintext with key using AEAD (XChaCha20-Poly1305).
     *  Returns ciphertext including authentication tag. aad fed into MAC as associated data. */
    fun encrypt(plaintext: ByteArray, key: ContentEncryptionKey, nonce: ByteArray, aad: ByteArray): ByteArray

    /** Decrypts. Returns Result<ByteArray, CryptoError.MacFailed>. */
    fun decrypt(ciphertext: ByteArray, key: ContentEncryptionKey, nonce: ByteArray, aad: ByteArray): Result<ByteArray, CryptoError>

    fun randomNonce(): ByteArray  // 24 bytes for XChaCha20
    fun generateCEK(): ContentEncryptionKey
}
```

### `AsymmetricCrypto`

```kotlin
interface AsymmetricCrypto {
    fun generateX25519Pair(alias: String): DeviceKeyPair

    /** crypto_box_seal — anonymous-sender hybrid encryption */
    fun sealCEK(cek: ContentEncryptionKey, recipientPub: PublicKey): ByteArray

    /** Unseals CEK. Returns Result<CEK, CryptoError.MacFailed>. */
    fun unsealCEK(sealedCEK: ByteArray, ownPair: DeviceKeyPair): Result<ContentEncryptionKey, CryptoError>
}
```

### `DigitalSignature` *(new in 011 rev. 2)*

```kotlin
interface DigitalSignature {
    fun generateEd25519Pair(alias: String): DeviceSigningKeyPair

    /** Ed25519 sign — returns 64-byte signature */
    fun sign(data: ByteArray, ownPair: DeviceSigningKeyPair): ByteArray

    /** Ed25519 verify — returns Result<Unit, CryptoError.SignatureVerifyFailed>. */
    fun verify(data: ByteArray, signature: ByteArray, pubKey: SigningPublicKey): Result<Unit, CryptoError>
}
```

- **Used by**:
  - 011: sign Pub publication payload (FR-006).
  - Future spec 013 (symmetric-pairing-bidirectional-control): signing identity proofs для single-ceremony onboarding UX (semantic двусторонних pairings уже supported в 011, см. spec.md C-1 rev. 4).
  - Future TBD-Jitsi: room join JWT signing.
  - Future TBD-Vendor: HMAC/JWT для b2b API.
  - Future TBD-Hardware: device attestation.

### `HashFunction` *(new in 011 rev. 2)*

```kotlin
interface HashFunction {
    /** BLAKE2b-256 hash of input. Returns 32 bytes. */
    fun hash(data: ByteArray): ByteArray
}
```

- **Used by**:
  - 011: integrity checks (например, fingerprint Pub-ключа для logging без выноса самого ключа).
  - Future spec 012: дедупликация blob'ов по content-hash (если admin дважды добавит то же фото).
  - Future TBD-Jitsi: room key fingerprint для отображения safety numbers.

### `SecureKeystore`

```kotlin
interface SecureKeystore {
    fun generateAndStoreEncryption(alias: String): DeviceKeyPair
    fun generateAndStoreSigning(alias: String): DeviceSigningKeyPair
    fun loadEncryption(alias: String): Result<DeviceKeyPair, CryptoError>      // KeyNotFound
    fun loadSigning(alias: String): Result<DeviceSigningKeyPair, CryptoError>  // KeyNotFound
    fun delete(alias: String)
    fun exists(alias: String): Boolean
}
```

- **Note**: AndroidKeystoreSecureKeystore adapter использует разные стратегии:
  - X25519 priv: AES-wrap (AES-ключ в Keystore + wrapped X25519 в EncryptedSharedPreferences). Keystore не поддерживает X25519 нативно.
  - Ed25519 priv: native Keystore с API 31+; AES-wrap fallback на API 30.

### `RecipientResolver`

```kotlin
fun interface RecipientResolver {
    suspend fun resolveRecipients(linkId: LinkId): List<DeviceIdentity>
}
```

- **Note**: `fun interface` (single abstract method). См. spec.md §Clarifications C-8 — documented justification для single-impl interface (3 будущие реализации в спеках 013/014/015).

### `EncryptedMediaStorage`

```kotlin
interface EncryptedMediaStorage {
    suspend fun upload(linkId: LinkId, uuid: Uuid, envelope: EncryptedEnvelope): Result<Unit, CryptoError>
    suspend fun download(linkId: LinkId, uuid: Uuid): Result<EncryptedEnvelope, CryptoError>  // BlobMissing | MalformedEnvelope | StorageFailure
    suspend fun delete(linkId: LinkId, uuid: Uuid): Result<Unit, CryptoError>
    suspend fun exists(linkId: LinkId, uuid: Uuid): Boolean
    suspend fun list(linkId: LinkId): List<Uuid>  // for housekeeping reconciler
}
```

### `DeviceIdentityRepository`

```kotlin
interface DeviceIdentityRepository {
    suspend fun publishOwn(linkId: LinkId, identity: DeviceIdentity): Result<Unit, CryptoError>
    suspend fun fetchPeer(linkId: LinkId, peerDeviceId: DeviceId): Result<DeviceIdentity, CryptoError>  // SignatureVerifyFailed if Ed25519 verification fails
    suspend fun listAll(linkId: LinkId): List<DeviceIdentity>  // для групп (future spec 014)
}
```

- **Note**: `fetchPeer` MUST verify Ed25519 signature над payload через `DigitalSignature.verify()` ДО возврата identity. Если verification fails → `SignatureVerifyFailed` — никакого fallback, никакого «возможно подделано но всё равно работает».

---

## §3. Local persistence (SQLDelight schemas)

### `BlobReferenceLedger.sq`

Расширяет существующую `ConfigSyncDatabase` из спека 008. Других таблиц в 011 нет (PrivateMediaCache — это спек 012).

```sql
CREATE TABLE BlobReferenceLedger (
    uuid TEXT NOT NULL,
    linkId TEXT NOT NULL,
    refSource TEXT NOT NULL,        -- "config-current" | "history-slot-N" | "pending-draft"
    refUpdatedAt INTEGER NOT NULL,  -- epoch millis
    PRIMARY KEY (uuid, refSource)
);

CREATE INDEX idx_blob_ref_uuid ON BlobReferenceLedger(uuid);
CREATE INDEX idx_blob_ref_link ON BlobReferenceLedger(linkId);

-- Queries:
addRef:
INSERT OR REPLACE INTO BlobReferenceLedger(uuid, linkId, refSource, refUpdatedAt)
VALUES (:uuid, :linkId, :source, :now);

removeRef:
DELETE FROM BlobReferenceLedger WHERE uuid = :uuid AND refSource = :source;

countRefs:
SELECT COUNT(*) FROM BlobReferenceLedger WHERE uuid = :uuid;

allUuidsForLink:
SELECT DISTINCT uuid FROM BlobReferenceLedger WHERE linkId = :linkId;

deleteByLink:
DELETE FROM BlobReferenceLedger WHERE linkId = :linkId;
```

- **Reference sources**:
  - `"config-current"` — упоминание в `/config/current`.
  - `"history-slot-0"` через `"history-slot-9"` — упоминания в history snapshots (спек 009 retention 10).
  - `"pending-draft"` — admin кеширует upload до push `/config` (чтобы retry не создавал новый CEK).
- **Update triggers** (в 011 — только инфраструктура; **реальные триггеры с photoRef** появятся в спеке 012):
  - admin'side `ConfigEditor.pushPending()` → after success → recount refs.
  - admin'side history capture / eviction (спек 009 retention 10) → recount.
  - admin'side revoke (спек 007 FR-033) → deleteByLink + Storage cleanup.

### Schema versioning

- Расширение `ConfigSyncDatabase` (существующая DB из спека 008) — bump SQLDelight schema version: 1 → 2.
- Migration: добавить одну CREATE TABLE statement + indexes для BlobReferenceLedger.
- Backward-compat: existing tables не меняются — добавление новой таблицы safe per SQLDelight migration mechanism.
- **PrivateMediaCache** (для расшифрованных blob bytes) добавится в спеке 012 как версия 2 → 3.

### Clear-data edge case *(new in 011 rev. 2 — CHK-FR-015)*

Если пользователь делает «настройки Android → приложение → очистить данные», вся SQLite DB стирается, включая BlobReferenceLedger. Эффект:
- Все локальные references на blob'ы теряются.
- При следующей синхронизации /config от Firestore — references восстанавливаются для `"config-current"`, но **не** для `"history-slot-N"` (history лежит локально, не в Firestore — спек 009).
- Background reconciler видит «blob X в Storage без references» → schedule delete через 24h.
- **Проблема**: blob может быть нужен через history rollback, но reconciler этого не знает.

**Mitigation**: при detect clear-data (отсутствует sentinel-row в DB после migration) reconciler MUST wait **7 дней** перед запуском, чтобы:
- /config от Firestore успел синхронизироваться и заполнить `"config-current"` refs.
- History snapshots успели пере-захватиться или явно потеряться (acceptable — clear-data = пользователь согласился потерять историю).
- 7 дней с запасом покрывают типичные офлайн-сценарии (отпуск, командировка).

**Implementation**: sentinel-row в системной таблице DB. При первом запуске после clear-data — записать `clearDataAt = now` и проверять `now - clearDataAt > 7 days` перед reconciliation. Раздокументировано в research.md §5.

---

## §4. Data flow lifecycle (только 011 scope — без PrivateMediaResolver/UI)

```text
[Time T0] устройство первый запуск приложения (любой link, может быть ещё без link)
   │
   ▼
SecureKeystore.exists("launcher_device_priv_x25519_v1")?
   │  ── No (first launch) ──►  SecureKeystore.generateAndStoreEncryption("launcher_device_priv_x25519_v1")
   │                            SecureKeystore.generateAndStoreSigning("launcher_device_priv_ed25519_v1")
   │                            own DeviceId сгенерирован (UUID v4) + сохранён в SharedPreferences
   │                            own DeviceIdentity (deviceId + X25519 Pub + Ed25519 Pub + signature) подготовлена в памяти
   │
[Time T1] устройство pairing с peer (спек 007 flow)
   │
   ▼
After consent.allow:
   ownPair = SecureKeystore.loadSigning("launcher_device_priv_ed25519_v1")
   signedTimestamp = now (epoch millis)
   payload = serialize(deviceId, publicKey, signingPublicKey, signedTimestamp)
   signature = DigitalSignature.sign(payload, ownPair)
   identity = DeviceIdentity(schemaVersion=1, deviceId, publicKey, signingPublicKey, signedTimestamp, signature, createdAt=Firestore-set)
   DeviceIdentityRepository.publishOwn(linkId, identity)
        ─► Firestore /links/{linkId}/devices/{ownDeviceId}
        ─► Storage Rules: write only if request.auth.uid owns this deviceId + signedTimestamp freshness check (now - signedTimestamp < 7 days)
   (Same flow on peer side)
   │
[Time T2] (этот flow в 011 — синтетический smoke; в спеке 012 — реальный admin add contact photo)
   │
   ▼
DeviceIdentityRepository.fetchPeer(linkId, peerDeviceId)
   ◄─ from Firestore /links/{linkId}/devices/{peerDeviceId}
   │  DigitalSignature.verify(payload, signature, peerSigningPub) — MUST pass
   │  если verify fails → SignatureVerifyFailed, остановить flow
   │
   ▼
CEK = AeadCipher.generateCEK()
CEK.use { cek ->
    nonce = AeadCipher.randomNonce()
    ciphertext = AeadCipher.encrypt(plaintextBytes, cek, nonce, aad=cborEncode(metadata))
    sealedCEK = AsymmetricCrypto.sealCEK(cek, peerIdentity.publicKey)
    envelope = EncryptedEnvelope(schemaVersion=1, cipherSuite=..., nonce, recipients=[Recipient(peerDeviceId, sealedCEK)], ciphertext, mac, metadata)
}  // CEK.bytes обнулены в finally
   │
   ▼
EncryptedMediaStorage.upload(linkId, newUuid, envelope)
   ─► Firebase Storage /links/{linkId}/private-media/{newUuid}
   │
   ▼
BlobReferenceLedger.addRef(newUuid, linkId, "pending-draft", now)
   │
   ▼ (в 011 — синтетический smoke завершён; в 012 — push /config с photoRef)
   │
[Time T3] peer receives FCM (через спек 007 channel)
   │  (в 011 — manual smoke кнопка «Decrypt test blob»; в 012 — ConfigApplier через PrivateMediaResolver)
   ▼
EncryptedMediaStorage.download(linkId, newUuid) → envelope
   │ if MalformedEnvelope или BlobMissing → fail gracefully (CryptoError, не crash)
   ▼
SecureKeystore.loadEncryption("launcher_device_priv_x25519_v1") → ownPair
   ▼
envelope.recipients.find { it.deviceId == ownDeviceId } → Recipient (constant-time iteration — research.md §2)
   │ если not found → RecipientNotFound
   ▼
AsymmetricCrypto.unsealCEK(recipient.sealedCEK, ownPair) → Result<CEK, MacFailed>
   ▼
cek.use { CEK ->
    plaintext = AeadCipher.decrypt(envelope.ciphertext, CEK, envelope.nonce, aad=cborEncode(envelope.metadata)) → Result<bytes, MacFailed>
}  // CEK.bytes обнулены
   │
   ▼ (в 011 — Toast hex для manual smoke; в 012 — render in tile через PrivateMediaResolver)

[Time T4] reference cleanup (US-4)
   │ (триггеры реальных references — в спеке 012; в 011 — синтетика)
   ▼
BlobReferenceLedger.countRefs(newUuid) →
   ── 0 refs ──► schedule WorkManager job (24h delay grace, exp backoff 1m/5m/30m/2h/12h, max 5 attempts)
   ── ≥1 ref ──► keep blob
   │
[Time T5, 24h later] WorkManager fires
   │
   ▼
re-check refCount; if still 0 →
   EncryptedMediaStorage.delete(linkId, newUuid)
   │
[Time T6] revoke link (спек 007 FR-033)
   │
   ▼
Link.KNOWN_SUBCOLLECTIONS extended with "devices", "private-media"
LinkRegistry.revoke(linkId):
   - delete /links/{linkId}/* в Firestore (existing)
   - **NEW**: enumerate Storage /links/{linkId}/private-media/* → delete each
   - BlobReferenceLedger.deleteByLink(linkId)
   - SecureKeystore.delete("launcher_device_priv_x25519_v1_link_<linkId>")  // если per-link key strategy chosen
   - SecureKeystore.delete("launcher_device_priv_ed25519_v1_link_<linkId>")

[Edge case] Clear-data на admin device
   │
   ▼
At reconciler start: read sentinel-row `clearDataAt`
   if now - clearDataAt < 7 days → SKIP reconciliation (wait for /config + history re-population)
   else → run normally
```

---

## §5. Validation rules (cross-cutting)

| Rule | Where enforced | Test |
|---|---|---|
| Envelope `recipients` array MUST contain ≥1 entry | `EncryptedEnvelope.init` | `EnvelopeInitTest` |
| Each Recipient.deviceId in envelope MUST be unique | `EncryptedEnvelope.init` | `EnvelopeInitTest` |
| `sealedCEK` size MUST be 80 bytes (libsodium crypto_box_seal output) | `Recipient.init` | `RecipientWireFormatTest` |
| `PublicKey.bytes` MUST be exactly 32 bytes (X25519) | `PublicKey.init` | `PublicKeyInitTest` |
| `SigningPublicKey.bytes` MUST be exactly 32 bytes (Ed25519) | `SigningPublicKey.init` | `SigningPublicKeyInitTest` |
| `ContentEncryptionKey.bytes` MUST be exactly 32 bytes | `ContentEncryptionKey.init` | `CEKInitTest` |
| `DeviceId.value` MUST be UUIDv4 | `DeviceId.init` | `DeviceIdInitTest` |
| `cipherSuiteId` MUST be recognised OR `decrypt` returns `CipherSuiteUnsupported` | `LibsodiumAeadCipher.decrypt` | `CipherSuiteUnsupportedTest` |
| `DeviceIdentity.signature` MUST verify against Ed25519 Pub OR `fetchPeer` returns `SignatureVerifyFailed` | `DeviceIdentityRepository.fetchPeer` | `DeviceIdentitySignatureTest` |
| `DeviceIdentity.signedTimestamp` MUST be within 7 days of `now` OR Storage Rules reject publication | Firestore Security Rules + client-side validate | `DeviceIdentityFreshnessTest` |
| Envelope CBOR parse error → `MalformedEnvelope`, NOT crash | `EncryptedMediaStorage.download` | `MalformedEnvelopeTest` |
| `BlobReferenceLedger.refUpdatedAt` MUST be monotonic | DB invariant | `LedgerMonotonicityTest` |
| Recipient search MUST be constant-time (no early-return based on deviceId match) | `unsealCEK` flow | `ConstantTimeRecipientSearchTest` |
| Reconciler MUST skip if clear-data sentinel < 7 days old | `BackgroundReconciler.run` | `ClearDataGraceTest` |

---

## §6. Type relationships diagram

```text
DeviceIdentity (signed wire format)
├─ schemaVersion: 1
├─ deviceId
├─ publicKey (X25519)            ──┐
├─ signingPublicKey (Ed25519)    ──┤── (signed payload)
├─ signedTimestamp               ──┘
├─ signature (Ed25519 over payload)
└─ createdAt (Firestore-set)

DeviceKeyPair (X25519)            DeviceSigningKeyPair (Ed25519)
├─ publicKey                      ├─ publicKey
└─ privateKey ──► alias           └─ privateKey ──► alias
       │                                  │
       ▼                                  ▼
   SecureKeystore (AES-wrap)          SecureKeystore (native API 31+ / AES-wrap API 30)

EncryptedEnvelope
├─ schemaVersion
├─ cipherSuiteId
├─ nonce
├─ recipients: List<Recipient>
│       └─ Recipient
│          ├─ deviceId
│          └─ sealedCEK (crypto_box_seal output)
├─ ciphertext
├─ mac
└─ metadata: Map<String, ByteArray> (freeform — клиент определяет)

Storage object
└─ /links/{linkId}/private-media/{uuid} = EncryptedEnvelope (CBOR-serialized)

Local persistence (SQLDelight)
└─ BlobReferenceLedger (refCount tracker — sources: config-current, history-slot-N, pending-draft)

[Out of 011 scope — moved to spec 012]
  PrivateMediaReference (string wrapper "private:<uuid>")
  PrivateMediaResolver (IconStorage namespace dispatch для `private:`)
  PrivateMediaCache (расшифрованные bytes cache)
  BlobMetadata.kind (Image/Document discriminator)
```

---

<!-- novice summary -->

## TL;DR (простым языком)

**Что в этом документе.** Точные структуры данных, которые появятся в коде. Это уровень «вот именно такие классы и таблицы базы данных мы напишем для криптофундамента».

**Главные новые сущности в 011:**

1. **`DeviceIdentity`** — «удостоверение устройства»: уникальный ID + два публичных ключа (один для шифрования X25519, второй для подписи Ed25519) + временная метка + цифровая подпись. Публикуется в Firebase, чтобы peer-устройства могли его прочитать, проверить подпись (anti-tamper), и зашифровать что-то для тебя.

2. **`EncryptedEnvelope`** — «конверт» с зашифрованным файлом. Внутри: версия формата, тип шифра, массив получателей (для каждого свой запечатанный CEK), зашифрованные байты, MAC-печать, и **нейтральная metadata-карта** (envelope в 011 не знает, что внутри — клиенты в 012+ определяют свои поля).

3. **`CryptoError`** — типизированные ошибки: «ключа нет», «MAC не сошёлся», «blob потерян», «cipher suite будущей версии», «нас нет в списке получателей», **«подпись не сошлась» (новое)**, **«envelope битый» (новое)**, «Storage упал», «Keystore упал». Все ошибки возвращаются как Result, не Exception.

4. **`BlobReference`** — счётчик «сколько раз этот blob упоминается» в одной из трёх категорий: текущий /config, history snapshots, pending drafts.

**Главные «розетки» (ports / interfaces) — 8 штук:**
- `AeadCipher` (XChaCha20-Poly1305)
- `AsymmetricCrypto` (X25519 + sealing)
- **`DigitalSignature` (Ed25519) — новое**
- **`HashFunction` (BLAKE2b) — новое**
- `SecureKeystore` (Android Keystore wrapping)
- `RecipientResolver` (кому шифровать)
- `EncryptedMediaStorage` (загрузка/скачивание)
- `DeviceIdentityRepository` (публикация публичных ключей)

У каждой port — две реализации (настоящая на libsodium/Firebase/Keystore + тестовая fake), с первого commit'a.

**Одна новая таблица в локальной БД** (SQLDelight, расширяет существующую из спека 008):
- `BlobReferenceLedger` — счётчик ссылок на blob'ы.
- **PrivateMediaCache переехал в спек 012** (он только для клиента, который реально расшифровывает фото).

**Жизненный цикл в 011** (см. диаграмму в §4):
- T0: устройство генерирует две пары ключей при первом запуске (X25519 + Ed25519).
- T1: pairing → оба публикуют свои Pub в Firestore с подписью + временной меткой.
- T2: encrypt тестовый блок (в 011 это синтетика для smoke; в 012 — реальное фото).
- T3: decrypt тестовый блок (в 011 — manual smoke кнопка; в 012 — резолвер плитки).
- T4-T5: ref counting → cleanup через 24h.
- T6: revoke link → всё чистится (Storage + ключи + ledger).
- **Edge case (новое)**: если admin сделал «очистить данные», reconciler ждёт 7 дней, прежде чем чистить, чтобы дать /config пере-синхронизироваться.

**14 валидаций** (§5) — включая 3 новые: signature verify, signedTimestamp freshness, CBOR malformed detection, constant-time recipient search, clear-data grace.

**Что переехало в спек 012 (не в этом документе):**
- `PrivateMediaReference` — обёртка над строкой `"private:<uuid>"`.
- `PrivateMediaResolver` — реализация IconStorage для `private:` namespace.
- `PrivateMediaCache` — кеш расшифрованных байтов.
- `BlobMetadata.kind` (Image/Document) — discriminator content type'а; envelope 011 нейтрален.
