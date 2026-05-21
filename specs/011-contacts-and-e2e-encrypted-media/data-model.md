# Data Model: Contacts Photos and E2E Encrypted Private Media

**Spec**: [spec.md](spec.md)
**Plan**: [plan.md](plan.md)
**Date**: 2026-05-21

Этот документ описывает domain-уровни типы (Kotlin commonMain), persisted local types (SQLDelight schemas), и их семантику. Wire-format типы — отдельно в [contracts/](contracts/).

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

- **Purpose**: однозначный идентификатор физического устройства; persisted в Android Keystore (alias-связан), публикуется в `/links/{linkId}/devices/{deviceId}`.
- **Lifetime**: создаётся при первом запуске приложения. Persists across app updates. Reset on factory reset / app data clear.

### `PublicKey`

```kotlin
@JvmInline @Serializable
value class PublicKey(val bytes: ByteArray) {
    init { require(bytes.size == 32) { "X25519 public key must be 32 bytes" } }
    override fun equals(other: Any?): Boolean = ...  // byte-by-byte
    override fun hashCode(): Int = bytes.contentHashCode()
}
```

- **Purpose**: 32-byte X25519 public key.
- **Note**: serializable (публикуется в Firestore base64-encoded).

### `PrivateKey` (opaque)

```kotlin
sealed interface PrivateKey {
    val alias: String   // Android Keystore alias

    /** Не Serializable. НЕ должен покидать устройство. */
}
```

- **Purpose**: opaque handle к приватному ключу в SecureKeystore. Само значение байт **не доступно** через интерфейс — только через `AsymmetricCrypto.unsealCEK(envelope, privKey)`.
- **Lifetime**: создаётся при первом запуске; удаляется при revoke link OR app data clear.
- **NOT Serializable. NOT extractable bytes.**

### `DeviceKeyPair`

```kotlin
data class DeviceKeyPair(
    val publicKey: PublicKey,
    val privateKey: PrivateKey,
)
```

- Internal struct. Only construction site — `AsymmetricCrypto.generateKeyPair()`.

### `DeviceIdentity`

```kotlin
@Serializable
data class DeviceIdentity(
    val schemaVersion: Int = 1,
    val deviceId: DeviceId,
    val publicKey: PublicKey,
    val createdAt: Timestamp,
)
```

- **Purpose**: serializable form of device identity, published to Firestore `/links/{linkId}/devices/{deviceId}`.
- **Wire format**: см. [contracts/device-identity.md](contracts/device-identity.md).

### `ContentEncryptionKey` (CEK)

```kotlin
class ContentEncryptionKey(internal val bytes: ByteArray) : AutoCloseable {
    init { require(bytes.size == 32) { "CEK must be 32 bytes" } }

    /** Zeroes out the bytes; throws if accessed afterwards. */
    override fun close() { bytes.fill(0) }
}
```

- **Purpose**: разовый симметричный ключ для шифрования **одного** blob'a.
- **Lifecycle**: создаётся `AsymmetricCrypto.generateCEK()` → используется для AEAD.encrypt() → sealed для каждого recipient → закрывается (zero'ed). После использования НЕ хранится.
- **NOT Serializable.**

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
    val schemaVersion: Int,         // 1
    val cipherSuiteId: String,      // "xchacha20poly1305_x25519_sealed_v1"
    val nonce: ByteArray,           // 24 bytes for XChaCha20 (192-bit extended nonce)
    val recipients: List<Recipient>,
    val ciphertext: ByteArray,
    val mac: ByteArray,             // 16 bytes Poly1305 (часто комбинируется с ciphertext в libsodium; отдельный для test clarity)
    val metadata: BlobMetadata,
) {
    init {
        require(recipients.isNotEmpty()) { "envelope must have at least 1 recipient" }
        require(nonce.size == 24) { "XChaCha20 nonce must be 24 bytes" }
    }
}
```

- **Purpose**: serializable форма зашифрованного blob'a, persisted в Firebase Storage в формате — см. [contracts/crypto-envelope.md](contracts/crypto-envelope.md).
- **Invariants**: recipients ≥ 1 (в 011 всегда 1); nonce размер фиксирован cipherSuiteId.

### `BlobMetadata`

```kotlin
@Serializable
data class BlobMetadata(
    val kind: BlobKind,             // "image" | "document" — content-type marker, not used for crypto
    val createdAt: Timestamp,
    val labelOpt: String? = null,   // for documents; null for contact photos
)

@Serializable
enum class BlobKind { Image, Document }
```

- **Purpose**: non-crypto metadata, выходит в envelope сразу за header'ом, шифруется вместе с ciphertext (AAD = envelope header). Помогает Managed понять, что показывать (photo viewer vs document viewer).

### `PrivateMediaReference`

```kotlin
@JvmInline @Serializable
value class PrivateMediaReference(val iconId: String) {
    init {
        require(iconId.startsWith("private:")) { "PrivateMediaReference must start with 'private:'" }
        require(IconRef.isValid(iconId)) { "must match namespace regex" }
    }
    val uuid: Uuid get() = Uuid.parse(iconId.substringAfter("private:"))

    companion object {
        @OptIn(ExperimentalUuidApi::class)
        fun random(): PrivateMediaReference =
            PrivateMediaReference("private:${Uuid.random()}")
    }
}
```

- **Purpose**: type-safe wrapper над string `"private:<uuid>"` который ходит в `Contact.photoRef` и `Slot.iconId`.
- **Compatibility**: совместим с существующим String-based `iconId` field (см. спек 006 [icon-id-namespace.md](../006-provider-capabilities-and-health/contracts/icon-id-namespace.md) — namespace convention).

### `CryptoError` (sealed)

```kotlin
sealed interface CryptoError {
    data class KeyNotFound(val alias: String, val cause: Throwable? = null) : CryptoError
    data class MacFailed(val uuid: Uuid) : CryptoError
    data class BlobMissing(val uuid: Uuid) : CryptoError
    data class CipherSuiteUnsupported(val suiteId: String) : CryptoError
    data class RecipientNotFound(val deviceId: DeviceId) : CryptoError
    data class StorageFailure(val cause: Throwable) : CryptoError
    data class KeystoreFailure(val cause: Throwable) : CryptoError
}
```

- **Purpose**: typed error hierarchy для `PartialReason.media_decrypt_failed` mapping. Каждый case mapping to subcategory в structured log (FR-041).

---

## §2. Ports (commonMain interfaces)

### `AeadCipher`

```kotlin
interface AeadCipher {
    /** Encrypts plaintext with key using AEAD (XChaCha20-Poly1305).
     *  Returns ciphertext including authentication tag. aad fed into MAC as associated data. */
    fun encrypt(plaintext: ByteArray, key: ContentEncryptionKey, nonce: ByteArray, aad: ByteArray): ByteArray

    /** Decrypts. Throws CryptoError.MacFailed if MAC mismatch. */
    fun decrypt(ciphertext: ByteArray, key: ContentEncryptionKey, nonce: ByteArray, aad: ByteArray): ByteArray

    fun randomNonce(): ByteArray  // 24 bytes for XChaCha20
    fun generateCEK(): ContentEncryptionKey
}
```

### `AsymmetricCrypto`

```kotlin
interface AsymmetricCrypto {
    fun generateKeyPair(alias: String): DeviceKeyPair

    /** crypto_box_seal — anonymous-sender hybrid encryption */
    fun sealCEK(cek: ContentEncryptionKey, recipientPub: PublicKey): ByteArray

    /** Unseals CEK. Throws CryptoError.MacFailed if integrity check fails. */
    fun unsealCEK(sealedCEK: ByteArray, ownPair: DeviceKeyPair): ContentEncryptionKey
}
```

### `SecureKeystore`

```kotlin
interface SecureKeystore {
    fun generateAndStore(alias: String): DeviceKeyPair
    fun loadKeyPair(alias: String): DeviceKeyPair    // throws KeyNotFound
    fun delete(alias: String)
    fun exists(alias: String): Boolean
}
```

### `RecipientResolver`

```kotlin
fun interface RecipientResolver {
    suspend fun resolveRecipients(linkId: LinkId): List<DeviceIdentity>
}
```

- **Note**: `fun interface` (single abstract method). См. spec.md §Clarifications C-8 — documented justification для single-impl interface.

### `EncryptedMediaStorage`

```kotlin
interface EncryptedMediaStorage {
    suspend fun upload(linkId: LinkId, uuid: Uuid, envelope: EncryptedEnvelope)
    suspend fun download(linkId: LinkId, uuid: Uuid): EncryptedEnvelope  // throws CryptoError.BlobMissing
    suspend fun delete(linkId: LinkId, uuid: Uuid)
    suspend fun exists(linkId: LinkId, uuid: Uuid): Boolean
    suspend fun list(linkId: LinkId): List<Uuid>  // for housekeeping reconciler
}
```

### `DeviceIdentityRepository`

```kotlin
interface DeviceIdentityRepository {
    suspend fun publishOwn(linkId: LinkId, identity: DeviceIdentity)
    suspend fun fetchPeer(linkId: LinkId, peerDeviceId: DeviceId): DeviceIdentity?
    suspend fun listAll(linkId: LinkId): List<DeviceIdentity>  // for groups (future spec ~016)
}
```

---

## §3. Local persistence (SQLDelight schemas)

### `PrivateMediaCache.sq`

Расширяет существующую `ConfigSyncDatabase` из спека 008.

```sql
CREATE TABLE PrivateMediaCache (
    uuid TEXT PRIMARY KEY,        -- UUID v4 без 'private:' prefix
    linkId TEXT NOT NULL,
    decryptedBytes BLOB NOT NULL, -- расшифрованные bytes
    decryptedAt INTEGER NOT NULL, -- epoch millis
    accessedAt INTEGER NOT NULL,  -- epoch millis, для LRU
    sizeBytes INTEGER NOT NULL
);

CREATE INDEX idx_private_media_cache_link ON PrivateMediaCache(linkId);
CREATE INDEX idx_private_media_cache_accessed ON PrivateMediaCache(accessedAt);

-- Queries:
get:
SELECT decryptedBytes, sizeBytes FROM PrivateMediaCache
WHERE uuid = :uuid;

put:
INSERT OR REPLACE INTO PrivateMediaCache(uuid, linkId, decryptedBytes, decryptedAt, accessedAt, sizeBytes)
VALUES (:uuid, :linkId, :bytes, :now, :now, :size);

touch:
UPDATE PrivateMediaCache SET accessedAt = :now WHERE uuid = :uuid;

evictLRU:
DELETE FROM PrivateMediaCache
WHERE uuid IN (
  SELECT uuid FROM PrivateMediaCache
  WHERE linkId = :linkId
  ORDER BY accessedAt ASC
  LIMIT :countToEvict
);

deleteByLink:
DELETE FROM PrivateMediaCache WHERE linkId = :linkId;
```

- **TTL**: NO automatic TTL (расшифрованный blob полезен пока ссылается из /config).
- **Eviction**: LRU при превышении budget (10 entries OR 5 MB total per link).
- **Plaintext-on-disk**: decryptedBytes хранится **в plaintext** в local SQLite (что нормально, потому что данные уже распакованы для рендера + SQLite приватна для app per Android sandbox). **FR-052 формулировка**: «MUST NOT persist plaintext в externally-accessible content provider» — SQLite of this app is NOT externally accessible.

### `BlobReferenceLedger.sq`

```sql
CREATE TABLE BlobReferenceLedger (
    uuid TEXT NOT NULL,
    linkId TEXT NOT NULL,
    refSource TEXT NOT NULL,        -- "config-current" | "history-slot-N"
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
- **Update triggers**:
  - admin'side `ConfigEditor.pushPending()` (спек 008) → after success → recount всех refs.
  - admin'side history capture (спек 009) → recount.
  - admin'side history eviction (спек 009 retention 10) → recount.
  - admin'side revoke (спек 007 FR-033) → deleteByLink + Storage cleanup.

### Schema versioning

- Расширения `ConfigSyncDatabase` (существующая DB из спека 008) — bump SQLDelight schema version: 1 → 2.
- Migration: добавить две CREATE TABLE statements + indexes.
- Backward-compat: existing tables не меняются — добавление новых таблиц safe per SQLDelight migration mechanism.

---

## §4. Data flow lifecycle

```text
[Time T0] admin first launch app (any link, can be no link yet)
   │
   ▼
SecureKeystore.exists("launcher_device_priv_v1")?
   │  ── No (first launch) ──►  SecureKeystore.generateAndStore("launcher_device_priv_v1")
   │                            DeviceIdentity created with own DeviceId + Pub
   │                            Stored locally в SharedPreferences (own DeviceIdentity for re-publication)
   │
[Time T1] admin pairs with Managed (спек 007 flow)
   │
   ▼
After consent.allow:
   DeviceIdentityRepository.publishOwn(linkId, ownDeviceIdentity)
        ─► Firestore /links/{linkId}/devices/{adminDeviceId}
   (Same on Managed side — publishes own DeviceIdentity)
   │
[Time T2] admin adds contact "Маша" with photo (US-1)
   │
   ▼
DeviceIdentityRepository.fetchPeer(linkId, managedDeviceId)
   ◄─ from Firestore /links/{linkId}/devices/{managedDeviceId}
   │
   ▼
AsymmetricCrypto.generateCEK() → ContentEncryptionKey
AeadCipher.encrypt(photoBytes, CEK, nonce, aad=metadata) → ciphertext
AsymmetricCrypto.sealCEK(CEK, peerPub) → sealedCEK
Compose EncryptedEnvelope(schemaVersion=1, cipherSuite=..., recipients=[Recipient(managedDeviceId, sealedCEK)], ...)
CEK.close() — bytes wiped
   │
   ▼
EncryptedMediaStorage.upload(linkId, newUuid, envelope)
   ─► Firebase Storage /links/{linkId}/private-media/{newUuid}
   │
   ▼
BlobReferenceLedger.addRef(newUuid, linkId, "config-current", now)
   │
   ▼
/config.contacts[].photoRef = PrivateMediaReference("private:<newUuid>").iconId
push /config (existing спек 008 flow)
   │
[Time T3] Managed receives FCM, ConfigApplier processes
   │
   ▼
ConfigApplier sees photoRef = "private:<newUuid>"
   ▼
IconStorage.resolve("private:<newUuid>") →
   PrivateMediaResolver.resolve("private:<newUuid>"):
   │
   ├─► PrivateMediaCache.get(newUuid) → Hit? Return Drawable(bytes)
   │
   └─► Miss → lazy decrypt path:
       EncryptedMediaStorage.download(linkId, newUuid) → envelope
       SecureKeystore.loadKeyPair("launcher_device_priv_v1") → ownPair
       envelope.recipients.find { it.deviceId == ownDeviceId } → Recipient
       AsymmetricCrypto.unsealCEK(recipient.sealedCEK, ownPair) → CEK
       AeadCipher.decrypt(envelope.ciphertext, CEK, envelope.nonce, aad) → plaintext
       PrivateMediaCache.put(newUuid, plaintext, sizeBytes=plaintext.size)
       Return Drawable(plaintext)
       CEK.close()
   │
[Time T4] admin removes "Маша" from layout (US-3)
   │
   ▼
push new /config without Маша
   │
   ▼
BlobReferenceLedger.removeRef(newUuid, linkId, "config-current")
BlobReferenceLedger.countRefs(newUuid) →
   ── 0 refs ──► schedule WorkManager job (24h delay grace)
   ── ≥1 ref (history snapshot still references) ──► keep blob
   │
[Time T5, 24h later] WorkManager fires
   │
   ▼
re-check refCount; if still 0 →
   EncryptedMediaStorage.delete(linkId, newUuid)
   PrivateMediaCache.delete(newUuid) (на admin'e — на Managed cache TTL отдельно)
   │
[Time T6] admin revokes link (спек 007 FR-033)
   │
   ▼
Link.KNOWN_SUBCOLLECTIONS extended with "private-media"
LinkRegistry.revoke(linkId):
   - delete /links/{linkId}/* в Firestore (existing)
   - **NEW**: enumerate Storage /links/{linkId}/private-media/* → delete each
   - BlobReferenceLedger.deleteByLink(linkId)
   - PrivateMediaCache.deleteByLink(linkId)
   - SecureKeystore.delete("launcher_device_priv_v1_link_$linkId")  // if per-link key strategy chosen
```

---

## §5. Validation rules (cross-cutting)

| Rule | Where enforced | Test |
|---|---|---|
| `iconId` matching `"private:<uuid>"` MUST resolve to PrivateMediaResolver | `ChainedIconStorage` dispatch | `IconStorageNamespaceDispatchTest` |
| Envelope `recipients` array MUST contain ≥1 entry | `EncryptedEnvelope.init` | `EnvelopeInitTest` |
| Each Recipient.deviceId in envelope MUST be unique | `EncryptedEnvelope.init` | `EnvelopeInitTest` |
| `sealedCEK` size MUST be 80 bytes (libsodium crypto_box_seal output) | `Recipient.init` | `RecipientWireFormatTest` |
| `PublicKey.bytes` MUST be exactly 32 bytes (X25519) | `PublicKey.init` | `PublicKeyInitTest` |
| `ContentEncryptionKey.bytes` MUST be exactly 32 bytes | `ContentEncryptionKey.init` | `CEKInitTest` |
| `DeviceId.value` MUST be UUIDv4 | `DeviceId.init` | `DeviceIdInitTest` |
| `cipherSuiteId` MUST be recognised OR resolve throws `CipherSuiteUnsupported` | `LibsodiumAeadCipher.decrypt` | `CipherSuiteUnsupportedTest` |
| Blob size MUST be ≤ 500 KB before encryption | `FirebaseEncryptedMediaStorage.upload` | `BlobSizeCapTest` |
| `PrivateMediaCache.sizeBytes` SUM per link ≤ 5 MB OR ≤ 10 entries | `LruEvictionPolicy` | `CacheEvictionTest` |
| BlobReferenceLedger `refUpdatedAt` MUST be monotonic | DB invariant | `LedgerMonotonicityTest` |

---

## §6. Type relationships diagram

```text
                    DeviceIdentity
                    ┌─ deviceId
                    ├─ publicKey
                    └─ createdAt

DeviceKeyPair                 EncryptedEnvelope
├─ publicKey                  ├─ schemaVersion
└─ privateKey ──► alias       ├─ cipherSuiteId
       │                      ├─ nonce
       ▼                      ├─ recipients: List<Recipient>
   SecureKeystore             │       └─ Recipient
   (Android Keystore)         │          ├─ deviceId
                              │          └─ sealedCEK
                              ├─ ciphertext
                              ├─ mac
                              └─ metadata: BlobMetadata
                                          ├─ kind (Image | Document)
                                          ├─ createdAt
                                          └─ labelOpt

Storage object                Config (existing in 008)
└─ /links/{linkId}/           └─ contacts[].photoRef = PrivateMediaReference.iconId
     private-media/{uuid}         OR slots[].iconId (for documents)
   = EncryptedEnvelope                 │
                                       ▼
                                IconStorage.resolve(iconId)
                                       │
                                       ▼
                              ChainedIconStorage dispatches by namespace
                                       │
                                       ▼
                              PrivateMediaResolver
                                       │
                                       ├─► PrivateMediaCache (hit) → Drawable
                                       │
                                       └─► Miss → EncryptedMediaStorage.download
                                              │
                                              ▼
                                       AsymmetricCrypto.unsealCEK
                                              │
                                              ▼
                                       AeadCipher.decrypt
                                              │
                                              ▼
                                       Cache + return Drawable

Local-only persistence (SQLDelight):
                              PrivateMediaCache (LRU, decryptedBytes)
                              BlobReferenceLedger (refCount tracker)
```

---

<!-- novice summary -->

## TL;DR (простым языком)

**Что в этом документе.** Точные структуры данных, которые появятся в коде. Это уровень «вот именно такие классы и таблицы базы данных мы напишем».

**Главные новые сущности:**

1. **`DeviceIdentity`** — «удостоверение устройства»: уникальный ID + публичный ключ + дата создания. Публикуется в Firebase, чтобы другие устройства могли его прочитать и зашифровать что-то для тебя.

2. **`EncryptedEnvelope`** — «конверт» с зашифрованным файлом. Внутри: версия формата (`schemaVersion`), какой шифр (`cipherSuiteId`), массив получателей (`recipients` — список «для кого зашифрован ключ»), зашифрованные байты, MAC (печать «никто не подменил»), и metadata (картинка это или документ).

3. **`PrivateMediaReference`** — оборачивает строку `private:<uuid>`, которую видим в `/config`. Просто тип-обёртка, чтобы не путать с обычными строками.

4. **`CryptoError`** — типизированные ошибки: «ключа нет», «MAC не сошёлся», «blob потерян в Storage», «cipher suite будущей версии», «нас нет в списке получателей». Каждая ошибка маппится в подкатегорию для admin диагностики.

**Главные новые «розетки» (interfaces / ports):** 6 штук — `AeadCipher`, `AsymmetricCrypto`, `SecureKeystore`, `RecipientResolver`, `EncryptedMediaStorage`, `DeviceIdentityRepository`. У каждой port — две реализации (настоящая + тестовая).

**Две новые таблицы в локальной БД** (SQLDelight, расширяет существующую из спека 008):
- `PrivateMediaCache` — кеш расшифрованных фото на устройстве (чтобы не расшифровывать каждый раз при показе плитки). LRU с лимитом 5 MB / 10 entries per pair.
- `BlobReferenceLedger` — счётчик «сколько раз этот blob упоминается» (current /config + history snapshots). Когда счётчик = 0 + 24 часа прошло → blob удаляется из Storage.

**Жизненный цикл фото от admin'a до бабушки** (см. диаграмму в §4):
- T0: admin генерирует пару ключей при первом запуске.
- T1: pairing → оба публикуют свои Pub в Firestore.
- T2: admin добавляет фото → шифрует → грузит → пушит /config.
- T3: бабушка получает FCM → читает /config → видит `private:<uuid>` → расшифровывает → кеширует → показывает.
- T4-T5: admin удаляет контакт → через 24 часа blob удаляется.
- T6: revoke link → всё чистится (Storage + ключи + кеш).

**Валидаций 10 штук** (§5) — все проверки, которые срабатывают при создании объектов или вызове методов. Большинство — простые «размер должен быть 32 байта» / «UUID должен быть валидным».
