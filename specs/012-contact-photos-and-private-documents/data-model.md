# Data Model: Contact Photos and Private Documents

**Spec**: [spec.md](spec.md) · **Plan**: [plan.md](plan.md)
**Date**: 2026-05-26

Domain types, ports, and persistence layout introduced by spec 012. Reuses crypto-foundation ports from 011 без модификации.

---

## §1. Domain enums and value types

### `PrivateMediaKind`

```kotlin
// core/api/media/PrivateMediaKind.kt
package com.launcher.api.media

import kotlinx.serialization.Serializable

/**
 * Media payload type carried in envelope `metadata.kind`.
 *
 * Wire value lowercase string ("image", "document"). Reader 011 envelope нейтрален —
 * client (spec 012+) определяет registry значений (см. contracts/metadata-kind-registry.md).
 *
 * Extension: новые kind'ы добавляются additive (Future: audio, video, file).
 */
@Serializable
enum class PrivateMediaKind(val wireValue: String) {
    Image("image"),
    Document("document"),
    ;

    companion object {
        fun fromWire(value: String): PrivateMediaKind? =
            entries.firstOrNull { it.wireValue == value }
    }
}
```

### `MediaPickResult`

```kotlin
// core/api/media/MediaPickResult.kt
package com.launcher.api.media

/**
 * Unified picker result — caller никогда не видит URI / Intent / ContentResolver.
 * Anti-Corruption Layer per CLAUDE.md rule 2.
 *
 * [bytes] — raw файла bytes (после decompression если нужно).
 * [mimeType] — content type ("image/jpeg", "image/png", ...).
 * [sourceLabel] — optional display label источника ("WhatsApp", "Camera"), для debug/log.
 */
data class MediaPickResult(
    val bytes: ByteArray,
    val mimeType: String,
    val sourceLabel: String? = null,
)
```

### `MediaPickerError`

```kotlin
// core/api/media/MediaPickerError.kt
package com.launcher.api.media

/**
 * Categorical errors from MediaPicker. NOT human-readable strings — UI maps each
 * к localised Russian message.
 */
sealed interface MediaPickerError {
    /** User закрыл picker без выбора. NOT an error per se, но caller должен handle. */
    data object Cancelled : MediaPickerError

    /** User выбрал файл с MIME type, не соответствующим [MediaPicker.Kind] параметру. */
    data class InvalidMimeType(val actual: String, val expected: String) : MediaPickerError

    /** I/O failure при чтении выбранного файла (corrupt URI, permission revoke в moment, etc.). */
    data class IOError(val cause: Throwable) : MediaPickerError

    /** Файл больше [SIZE_CAP_BYTES] cap'а. */
    data class FileTooLarge(val actualBytes: Long, val maxBytes: Long) : MediaPickerError
}
```

---

## §2. New ports (`commonMain`)

### `PrivateMediaUploader` (facade)

```kotlin
// core/api/media/PrivateMediaUploader.kt
package com.launcher.api.media

import com.launcher.api.crypto.CryptoError
import com.launcher.api.link.LinkId
import com.launcher.api.result.Outcome
import com.launcher.api.config.IconRef  // String "private:<uuid>"

/**
 * Single entry point для **encrypt → upload → register reference** flow.
 *
 * **DO NOT** call AeadCipher / EncryptedMediaStorage / BlobReferenceLedger directly
 * из UI или business logic. Используй этот facade.
 * См. docs/dev/private-media-architecture.md.
 */
interface PrivateMediaUploader {
    /**
     * @param bytes — raw plaintext (фото или документ; should be ≤ 500 KB after admin-side compression).
     * @param kind — payload type (Image | Document); записывается в envelope metadata.
     * @param linkId — целевой link (admin pair).
     * @return Outcome<IconRef("private:<uuid>"), CryptoError> где Success содержит
     *         iconId namespace `private:<uuid>` готовый для записи в /config.
     */
    suspend fun upload(
        bytes: ByteArray,
        kind: PrivateMediaKind,
        linkId: LinkId,
    ): Outcome<IconRef, CryptoError>
}
```

### `PrivateMediaResolver` (facade + IconStorage namespace dispatch)

```kotlin
// core/api/media/PrivateMediaResolver.kt
package com.launcher.api.media

import com.launcher.api.capability.IconResolution
import com.launcher.api.capability.IconStorage

/**
 * Single entry point для **lookup → download → decrypt → cache → render** flow.
 *
 * Реализует [IconStorage] для namespace `"private:"` (обещание спека 006,
 * см. specs/006-provider-capabilities-and-health/contracts/icon-id-namespace.md:48).
 *
 * **DO NOT** call AeadCipher / EncryptedMediaStorage directly из UI.
 */
interface PrivateMediaResolver : IconStorage {
    /**
     * IconStorage.resolve("private:<uuid>") implementation:
     * 1. LocalMediaStore.read(uuid) → if present, return IconResolution.Bitmap (≤ 100 мс).
     * 2. Если нет — EncryptedMediaStorage.download → AeadCipher.decrypt → LocalMediaStore.write → return Bitmap (≤ 3 секунды first time).
     * 3. На любую CryptoError — return IconResolution.Placeholder + emit PartialReason.MediaDecryptFailed.
     *
     * Decrypt происходит **ровно один раз** на blob. Следующие resolves — мгновенно из LocalMediaStore.
     */
    // resolve(iconId) inherited from IconStorage
}
```

### `LocalMediaStore` (port)

```kotlin
// core/api/media/LocalMediaStore.kt
package com.launcher.api.media

import com.launcher.api.result.Outcome

/**
 * Persistent app-private storage для **расшифрованных** media файлов.
 *
 * Location (per `contracts/local-media-store-layout.md`):
 *   `Context.filesDir/private-media/<uuid>` (на Android adapter'е).
 *
 * **Files contain plaintext PII** (фото лиц, документов). MUST быть excluded из
 * cloud-backup и device-transfer через `res/xml/data_extraction_rules.xml`.
 * См. plan.md R5 (regret conditions) и specs/.../contracts/local-media-store-layout.md.
 */
interface LocalMediaStore {
    /** @return File если есть в store, null если нет. */
    suspend fun read(uuid: String): LocalMediaFile?

    /** @return File handle указывающий на записанный файл. */
    suspend fun write(uuid: String, bytes: ByteArray): LocalMediaFile

    /** Idempotent: delete non-existing = no error. */
    suspend fun delete(uuid: String)

    /** @return true если файл существует в store. */
    suspend fun exists(uuid: String): Boolean

    /** Сумма размеров всех файлов в store (для observability / future quota). */
    suspend fun totalSizeBytes(): Long
}

/**
 * Abstraction over a local media file. Не выставляет `java.io.File` или Android `Uri` в domain.
 * Implementation может возвращать platform-specific handle (File / NSURL / etc.).
 */
expect class LocalMediaFile {
    val sizeBytes: Long
    val lastAccessedAt: Long  // epoch millis
}
```

### `MediaPicker` (port)

```kotlin
// core/api/media/MediaPicker.kt
package com.launcher.api.media

import com.launcher.api.result.Outcome

/**
 * Domain port для выбора media файлов с устройства.
 * Anti-Corruption Layer для system Photo Picker / SAF (per CLAUDE.md rule 2).
 *
 * Возвращает **unified bytes** — caller никогда не видит Uri / Intent / ContentResolver.
 *
 * Adapter (SystemPhotoPickerAdapter) внутри себя выбирает реализацию по API level:
 *   - 33+ → ACTION_PICK_IMAGES
 *   - 29-32 → androidx PhotoPicker compat
 *   - 26-28 → SAF ACTION_OPEN_DOCUMENT + copy в temp file
 */
interface MediaPicker {
    enum class Kind { Image, Video, Any }
    enum class Mode { Gallery, Folders }

    /**
     * @param kind — type filter (Image rejects video MIME types, etc.).
     * @param maxItems — max files user can select (в spec 012 всегда 1).
     * @param mode — UI mode (Gallery — flat timeline; Folders — album navigation).
     * @return Outcome<List<MediaPickResult>, MediaPickerError>. Cancellation = Failure(Cancelled).
     */
    suspend fun pick(
        kind: Kind,
        maxItems: Int = 1,
        mode: Mode = Mode.Gallery,
    ): Outcome<List<MediaPickResult>, MediaPickerError>

    companion object {
        /** Hard cap на размер файла (500 KB matches Storage Rules cap из спека 011). */
        const val SIZE_CAP_BYTES: Long = 500 * 1024
    }
}
```

---

## §3. Extension of existing `Tile` (owned by spec 008)

### `Tile.DocumentTile` (new sealed variant)

```kotlin
// core/api/config/Tile.kt — EXTEND existing sealed hierarchy
package com.launcher.api.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface Tile {
    val id: ElementId

    @Serializable
    @SerialName("contact")
    data class ContactTile(
        override val id: ElementId,
        val contactId: ElementId,
        // ... existing fields ...
    ) : Tile

    @Serializable
    @SerialName("app")
    data class AppTile(
        override val id: ElementId,
        val packageName: String,
        val label: String,
    ) : Tile

    @Serializable
    @SerialName("flow")
    data class FlowTile(
        override val id: ElementId,
        val flowId: ElementId,
        val label: String,
    ) : Tile

    /** NEW in spec 012. Документ-плитка с фото-превью. */
    @Serializable
    @SerialName("document")
    data class DocumentTile(
        override val id: ElementId,
        val documentRef: String,  // "private:<uuid>" — IconRef namespace
        val label: String,         // sanitised 1..40 graphemes
    ) : Tile

    companion object {
        /** Label validation для DocumentTile.label. */
        fun validateLabel(raw: String): Outcome<String, ValidationError> = TODO("...")
    }
}
```

**Wire format** (JSON discriminated by `kind`):

```json
{
  "id": "t1234567-1234-4321-8765-abcdefabcdef",
  "kind": "document",
  "documentRef": "private:f1111111-2222-4333-9444-555555555555",
  "label": "Паспорт"
}
```

**Backward-compatibility** (pre-production свобода, Q2 deviation):
- Old reader без `DocumentTile` support → emits `PartialReason.UnknownSlotKind` (state-applied.md:67).
- No `schemaVersion` bump (см. contracts/tile-document-kind.md).

---

## §4. Extension of envelope `metadata` (owned by spec 011)

`EncryptedEnvelope.metadata` — `Map<String, ByteArray>`, freeform per [011/contracts/crypto-envelope.md](../011-contacts-and-e2e-encrypted-media/contracts/crypto-envelope.md). Spec 012 определяет первое реальное использование:

| Key | Value type | Cardinality | Spec | Notes |
|---|---|---|---|---|
| `kind` | bstr (UTF-8 from `PrivateMediaKind.wireValue`) | optional | 012 | `"image"` или `"document"`. Используется AAD (binds ciphertext к context). |

**Privacy invariant** (FR-006, SC-008, see `contracts/metadata-kind-registry.md`):
- Sensitive labels («Паспорт», «Медкарта», «Снилс»...) — НЕ в metadata. Они шифруются **внутри ciphertext** через JSON envelope:
  ```kotlin
  // Внутри ciphertext:
  data class DocumentPayload(val imageBytes: ByteArray, val label: String?)
  ```
  Encoded → AeadCipher.encrypt → envelope.ciphertext.
- Metadata MUST содержать только **non-sensitive** classification (kind = image/document — отделить «фото лица контакта» от «фото документа» server-side сервер всё равно не различит без расшифровки content'а, поэтому это OK).

---

## §5. Persistence — `LocalMediaStore` layout (НЕ wire format)

```text
Context.filesDir/
└── private-media/
    ├── f1111111-2222-4333-9444-555555555555      # raw decrypted bytes (no extension — content-agnostic)
    ├── 22222222-3333-4444-5555-666666666666
    └── ...
```

**Details**:
- Filename = `uuid` строка (lowercase UUIDv4, matches namespace `private:<uuid>`).
- Content = расшифрованные raw bytes (JPEG byte stream если kind=image; для документа — JPEG в нашей text реализации).
- No extension — content-type определяется через `metadata.kind` envelope'а во время decrypt'а, потом игнорируется.
- App-private: `Context.MODE_PRIVATE` permissions (default для `filesDir`).

**MUST be excluded from cloud-backup и device-transfer** через `res/xml/data_extraction_rules.xml`. См. `contracts/local-media-store-layout.md`.

**Not a wire format** (rule 5 не применяется):
- Bytes не покидают устройство.
- File format = whatever encoder admin использовал перед upload'ом (JPEG обычно). На устройстве — read-only artifact.
- При смене encoding в admin'е (например, future WebP) — старые файлы остаются JPEG; новые — WebP; `LocalMediaStore.read` возвращает raw bytes, caller (Coil или Compose Image) decode'ит автоматически.

---

## §6. Reused ports from spec 011 (без модификации)

Spec 012 переиспользует через фасады:

| Port | Owner | Use in 012 |
|---|---|---|
| `AeadCipher` | 011 | Encrypt/decrypt blob payload |
| `AsymmetricCrypto` | 011 | Seal CEK для recipients |
| `EncryptedMediaStorage` | 011 | Upload/download/delete envelope в Backblaze B2 |
| `DigitalSignature` | 011 | Sign upload requests (если 011 это делает) |
| `HashFunction` | 011 | Опционально для blob integrity |
| `SecureKeystore` | 011 | Хранение device key для расшифровки |
| `BlobReferenceLedger` | 011 | refCount management при overwrite / delete |
| `RecipientResolver` | 011 | Получение list of recipients для envelope.recipients[] |
| `DeviceIdentityRepository` | 011 | Own deviceId для resolve.recipientLookup |

**Spec 012 НЕ модифицирует ни один из них.** Все 8 портов + ledger переиспользуются as-is. Это валидация scope 011 (CLAUDE.md rule 4 — никаких unused абстракций).

---

## §7. Diagnostic events emitted by spec 012

| Event | When | Payload (no PII) |
|---|---|---|
| `media.upload.success` | Successful PrivateMediaUploader.upload | `{kind, sizeBytes, linkId}` (linkId = own pair, no other identity) |
| `media.upload.fail` | Failed upload | `{kind, errorCategory: StorageFailure|MalformedEnvelope|QuotaExceeded}` |
| `media.resolve.cache_miss` | First show — download required | `{uuid_prefix=first8chars}` |
| `media.resolve.cache_hit` | Repeat show — LocalMediaStore.read | `{uuid_prefix}` |
| `media.decrypt.fail` | CryptoError on resolve | `{subcategory: mac_failed|blob_missing|key_not_found|recipient_not_found, uuid_prefix}` |
| `media.partial_apply` | `MediaDecryptFailed` added to /state.partialApplyReasons | `{count_in_list}` |

`uuid_prefix` (первые 8 символов) — for correlation в логах без раскрытия full UUID. Random UUIDv4 первые 8 chars = 32 bits entropy, недостаточно для re-identification, OK для debug.

---

## §8. Test fakes (commonTest)

### `FakeMediaPicker`

```kotlin
class FakeMediaPicker : MediaPicker {
    private val queue = ArrayDeque<MediaPickResult>()

    fun enqueueResult(bytes: ByteArray, mimeType: String = "image/jpeg") {
        queue.addLast(MediaPickResult(bytes, mimeType, sourceLabel = "test"))
    }

    fun enqueueFailure(error: MediaPickerError) { ... }

    override suspend fun pick(...) = ...
}
```

### `FakeLocalMediaStore` (in-memory)

```kotlin
class FakeLocalMediaStore : LocalMediaStore {
    private val store = mutableMapOf<String, ByteArray>()

    override suspend fun read(uuid: String): LocalMediaFile? = ...
    override suspend fun write(uuid: String, bytes: ByteArray): LocalMediaFile { store[uuid] = bytes; ... }
    override suspend fun delete(uuid: String) { store.remove(uuid) }
    override suspend fun exists(uuid: String) = store.containsKey(uuid)
    override suspend fun totalSizeBytes(): Long = store.values.sumOf { it.size.toLong() }
}
```

---

## §9. KDoc directives on crypto ports (mandatory plan-phase additions)

В рамках Article XI §8 «Reuse before invention» — добавить KDoc на следующие порты:

| Port (existing, owned by 011) | KDoc to add |
|---|---|
| `AeadCipher` | `> NOT for direct use from UI / business logic. Go through [PrivateMediaUploader] / [PrivateMediaResolver] facades. See docs/dev/private-media-architecture.md.` |
| `AsymmetricCrypto` | (same) |
| `EncryptedMediaStorage` | (same) |
| `DigitalSignature` | (same) |
| `HashFunction` | (same) |
| `SecureKeystore` | (same) |

Это **не нарушает существующий код** (только добавляет comments). Проверяется в `speckit-analyze` (SC-009).

---

## Summary

Spec 012 adds:
- **6 new domain types** (`PrivateMediaKind`, `MediaPickResult`, `MediaPickerError`, `LocalMediaFile` expect class) + **4 new ports** (`PrivateMediaUploader`, `PrivateMediaResolver`, `LocalMediaStore`, `MediaPicker`).
- **1 new sealed variant** в существующем `Tile` (`DocumentTile`).
- **1 new envelope metadata key** (`kind`).
- **1 new persistent local layout** (`Context.filesDir/private-media/<uuid>`).
- **6 KDoc directives** на existing crypto ports (no code change, doc only).

**Zero new wire-format `schemaVersion` bumps** (pre-production deviation Q2).
**Zero new external SDK** (только androidx.documentfile ~50 KB).
**Zero new runtime permissions**.
