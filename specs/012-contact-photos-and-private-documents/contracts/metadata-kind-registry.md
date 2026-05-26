# Contract: Envelope `metadata.kind` Registry

**Version:** 1.0.0 · **Status:** Draft (spec 012) — first real usage of envelope metadata
**Owner:** spec 012 extends spec 011 envelope contract
**Tests:** `EnvelopeMetadataKindTest`, `EnvelopeMetadataPrivacyTest`

---

## Purpose

[Spec 011 envelope contract](../../011-contacts-and-e2e-encrypted-media/contracts/crypto-envelope.md) определяет `metadata: Map<String, ByteArray>` как freeform — envelope-level нейтрален к содержанию. Spec 012 — первый client — фиксирует registry значений для ключа `kind`.

**Privacy invariant** (FR-006, SC-008): `metadata` появляется в envelope **в plaintext** (используется как AAD для AEAD binding'а). Sensitive labels («Паспорт») MUST шифроваться **внутри ciphertext**, НЕ в metadata.

---

## Key: `kind`

Optional UTF-8 string identifying the payload type. Used for client-side dispatch (например, плитка контакта vs DocumentViewer rendering).

### Registered values

| Wire value | Spec | Semantics |
|---|---|---|
| `"image"` | 012 | Photo of a contact's face / generic image. Plaintext ciphertext = raw JPEG bytes (или future WebP/HEIC). |
| `"document"` | 012 | Photo of a personal document (passport, medical card, insurance). Plaintext ciphertext = JSON `DocumentPayload(imageBytes, label?)` — label inside ciphertext, NOT in metadata. |
| (future) `"audio"` | TBD | Reserved for voice messages. Plaintext = compressed audio (Opus/AAC). Not in 012. |
| (future) `"video"` | TBD | Reserved for video messages. Streaming/chunked variant — TODO spec. Not in 012. |

### Validation

| Reader behaviour | Case |
|---|---|
| Known value (registered) | Dispatch to appropriate caller (image → Coil decode; document → DocumentPayload decode internal JSON) |
| Unknown value (forward-compat) | Treat as opaque — return raw bytes; caller decides (probably emits `PartialReason.UnknownSlotKind` или эквивалент per spec). DO NOT crash. |
| Missing `kind` (legacy/smoke blobs) | Treat as `"image"` default (legacy compatibility — smoke blobs от 011 без metadata). |

---

## Privacy invariant (CRITICAL)

**`metadata` map выходит из envelope в plaintext.** Server (Backblaze B2 + Cloudflare Worker) видит `metadata.kind = "document"`. Это OK — server и так видит факт upload'а блоба, добавление "document" категории не раскрывает identity.

**`metadata` NOT TO contain**:
- Sensitive label («Паспорт», «Медкарта», «Свидетельство о рождении»).
- Personal identifiers (имя бабушки, номер документа, ИНН).
- Contact references (с кем шарится — это видно через recipients[], но не через metadata).

**Sensitive data MUST go inside ciphertext**, e.g.:

```kotlin
// PLAINTEXT (внутри AeadCipher.encrypt → ciphertext):
@Serializable
data class DocumentPayload(
    val imageBytes: ByteArray,
    val label: String?,  // sensitive — здесь, не в metadata
)

// ENCRYPT:
val payload = DocumentPayload(jpegBytes, "Паспорт")
val payloadCbor = Cbor.encodeToByteArray(payload)
val ciphertext = aeadCipher.encrypt(payloadCbor, cek, nonce, aad = envelopeMetadata)

// envelope.metadata = {"kind": "document"}  // только kind, без label
```

### Privacy test (mandatory, FR-006/SC-008)

```kotlin
@Test
fun no_label_leak_in_plaintext_metadata() {
    val sensitiveLabel = "TestLabelLeak_123"
    val envelope = uploader.upload(jpegBytes, PrivateMediaKind.Document, linkId).bytesWithLabel(sensitiveLabel)

    // Parse plaintext envelope (without decrypting ciphertext)
    val parsed: Map<String, Any> = Cbor.decodeFromByteArray(envelope)
    val metadata: Map<String, ByteArray> = parsed["metadata"] as Map<String, ByteArray>

    metadata.values.forEach { bytes ->
        val asString = bytes.decodeToString()
        assertFalse(asString.contains(sensitiveLabel), "Sensitive label LEAKED in plaintext metadata!")
    }
}
```

Эту проверку **обязательно** включить в CI как gate test. Если когда-нибудь зелёная — privacy invariant сохранена.

---

## Extension policy (для future specs)

### Adding new `kind` values

**Forward-compatible** — добавление нового value (`"audio"`, `"video"`) не требует bump'a envelope schemaVersion. Old readers получают unknown value, treat opaque, эмитят diagnostic. Procedure:

1. Update this contract — добавить row в registered values table.
2. Implement encoder + decoder для нового payload format'а (например, `AudioPayload(opusBytes, durationMs)`).
3. Update `PrivateMediaKind` enum (`Audio`, `Video`, ...) — additive.
4. Add roundtrip test для new kind.

### Removing / renaming kind values

**Breaking change.** Required:
1. Bump envelope `cipherSuiteId` (per [011 envelope contract](../../011-contacts-and-e2e-encrypted-media/contracts/crypto-envelope.md) breaking-change policy).
2. Migration: existing blobs с старым kind могут потребовать re-encryption (если bytes изменяются).

Это **one-way door** (CLAUDE.md rule 3) — никогда не делать legkomyslenno.

---

## Tests (commonTest)

```kotlin
class EnvelopeMetadataKindTest {

    @Test
    fun roundtrip_image_metadata() {
        val envelope = buildEnvelope(metadata = mapOf("kind" to "image".toByteArray()))
        val parsed = decodeEnvelope(envelope.bytes)
        assertEquals("image", parsed.metadata["kind"]?.decodeToString())
    }

    @Test
    fun roundtrip_document_metadata() {
        val envelope = buildEnvelope(metadata = mapOf("kind" to "document".toByteArray()))
        val parsed = decodeEnvelope(envelope.bytes)
        assertEquals("document", parsed.metadata["kind"]?.decodeToString())
    }

    @Test
    fun forward_compat_unknown_kind() {
        val envelope = buildEnvelope(metadata = mapOf("kind" to "audio".toByteArray()))
        val parsed = decodeEnvelope(envelope.bytes)
        // Reader не падает, возвращает opaque bytes
        assertNotNull(parsed)
    }

    @Test
    fun missing_kind_treated_as_image_legacy() {
        val envelope = buildEnvelope(metadata = emptyMap())
        // Default behavior (legacy 011 smoke blobs без metadata)
        // Caller dispatches как image
    }
}
```

---

## Cross-references

- [011/crypto-envelope.md §metadata](../../011-contacts-and-e2e-encrypted-media/contracts/crypto-envelope.md) — base envelope contract, "envelope нейтрален к metadata".
- [spec.md FR-006, SC-008](../spec.md) — privacy invariant requirement.
- [data-model.md §1 PrivateMediaKind](../data-model.md) — enum sync с этим registry.

---

## TL;DR (для новичка)

**Что это**: список разрешённых значений ключа `kind` в `metadata` зашифрованного envelope'а. Сейчас 2 значения: `"image"` (фото лица) и `"document"` (фото документа). В будущем добавим audio/video.

**Зачем**: server видит `kind` — он понимает, что это (документ vs обычное фото), но не видит **что именно** (паспорт vs снилс vs обложка тетради). Sensitive подпись («Паспорт») мы зашифровываем **внутри ciphertext**, не в metadata.

**Почему ключ `kind` open в plaintext**: потому что server и так видит факт upload'а blob'а — добавление categorical hint'а не добавляет ничего к infoleak. А client'у это нужно для правильного rendering'а (плитка контакта vs DocumentViewer).
