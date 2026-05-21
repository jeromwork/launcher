# Contract: `EncryptedEnvelope` Wire Format

**Version:** 1.0.0 · **Status:** Stable from спек 011 · **Owner:** spec 011
**Storage**: per-blob, persisted в Firebase Storage object `/links/{linkId}/private-media/{uuid}`
**Test**: `CryptoEnvelopeWireFormatTest`

---

## Purpose

Описывает формат envelope для одного зашифрованного blob'a (фотография контакта, фотография документа, или любые будущие приватные байты). Envelope — самодостаточный: содержит всё нужное для расшифровки получателями.

**Главная инвариант**: envelope membership-agnostic — он ничего не знает о том, что в проекте есть «пары», «группы» или «multi-device». Он знает только «список получателей произвольной длины», каждый со своим зашифрованным CEK. См. [spec.md §Clarifications C-2, C-3](../spec.md).

---

## Format

Сериализация — **CBOR** (Concise Binary Object Representation, RFC 8949) через kotlinx.serialization-cbor.

**Почему CBOR, а не JSON:**
- Envelope содержит много byte arrays (nonce, ciphertext, mac, sealedCEK). В JSON это base64 — ~33% overhead. В CBOR — нативные binary strings, no overhead.
- Detached MAC verification: парсер должен прочитать header перед попыткой расшифровки → CBOR header parsing быстрее JSON.
- Альтернатива (отброшена): protobuf — требует extra `.proto` file + codegen, overhead в build complexity. CBOR через kotlinx даёт schema без `.proto`.

### Top-level schema

| Field | CBOR type | Required | Notes |
|---|---|---|---|
| `schemaVersion` | unsigned int | ✓ | Currently `1`. Bump only on breaking change (CLAUDE.md rule 5). |
| `cipherSuiteId` | tstr | ✓ | Currently `"xchacha20poly1305_x25519_sealed_v1"`. Identifies AEAD + asymmetric scheme combination. |
| `nonce` | bstr | ✓ | 24 bytes (XChaCha20 extended nonce). |
| `recipients` | array | ✓ | Length ≥ 1. Each entry — `Recipient` (see below). |
| `ciphertext` | bstr | ✓ | AEAD-encrypted plaintext + auth tag combined (libsodium combined-mode output). |
| `metadata` | map | ✓ | Plaintext metadata (used as AAD). See `BlobMetadata` schema below. |

**Note on `mac`**: in libsodium combined-mode (`crypto_secretbox_*_easy`), authentication tag is **prepended to ciphertext** в одном байт-массиве. Поэтому в wire-format есть только `ciphertext` (which includes the tag). В domain-уровне `EncryptedEnvelope` имеет отдельное поле `mac` для test clarity — but при serialization combined into ciphertext field.

### `Recipient` schema

| Field | CBOR type | Required | Notes |
|---|---|---|---|
| `deviceId` | tstr | ✓ | UUIDv4 string. Receiver MUST find own deviceId here. |
| `sealedCEK` | bstr | ✓ | Output of libsodium `crypto_box_seal` = 80 bytes (32 ephemeral pub + 32 ciphertext + 16 MAC). |

### `BlobMetadata` schema

| Field | CBOR type | Required | Notes |
|---|---|---|---|
| `kind` | tstr | ✓ | `"image"` or `"document"`. Helps Managed pick correct viewer. |
| `createdAt` | unsigned int (epoch millis) | ✓ | Server-set on upload. Used in AAD. |
| `labelOpt` | tstr | ✗ | For documents (US-2): user-given label like "Паспорт". Max 24 chars. Null for contact photos. |

---

## AAD (Associated Authenticated Data)

AAD is data that is authenticated but **not encrypted** — used in AEAD scheme to bind ciphertext to context.

**For 011**, AAD = CBOR-serialized `BlobMetadata`. This ensures:
- Cannot swap two blobs by their metadata (e.g., a "photo" can't be presented as "document" without breaking MAC).
- Cannot tamper with `createdAt` without invalidation.

**Decoder algorithm**:
1. Parse top-level envelope CBOR.
2. Re-serialize `metadata` to CBOR bytes → AAD.
3. Pass AAD to `AeadCipher.decrypt(ciphertext, key, nonce, aad)`.
4. Libsodium verifies MAC over `(ciphertext || aad)`.

---

## Example (hex-encoded for readability)

```
A6                                                # map(6)
   6D 73 63 68 65 6D 61 56 65 72 73 69 6F 6E    # text "schemaVersion"
   01                                             # uint(1)
   6E 63 69 70 68 65 72 53 75 69 74 65 49 64     # text "cipherSuiteId"
   78 27 78 63 68 61 63 68 61 32 30 70 6F 6C ... # text "xchacha20poly1305_x25519_sealed_v1"
   65 6E 6F 6E 63 65                              # text "nonce"
   58 18                                          # bstr(24)
      A1 B2 C3 ... (24 bytes random nonce)
   6A 72 65 63 69 70 69 65 6E 74 73              # text "recipients"
   81                                             # array(1)
      A2                                           # map(2)
         68 64 65 76 69 63 65 49 64               # text "deviceId"
         78 24 31 32 33 65 34 35 36 37 ...        # text UUIDv4
         69 73 65 61 6C 65 64 43 45 4B            # text "sealedCEK"
         58 50                                     # bstr(80)
            ... (80 bytes crypto_box_seal output)
   6A 63 69 70 68 65 72 74 65 78 74              # text "ciphertext"
   59 00 32 ...                                   # bstr(length): encrypted blob
   68 6D 65 74 61 64 61 74 61                    # text "metadata"
   A3                                             # map(3)
      64 6B 69 6E 64                              # text "kind"
      65 69 6D 61 67 65                           # text "image"
      69 63 72 65 61 74 65 64 41 74               # text "createdAt"
      1B 00 00 01 8E 5C 4A 00 00                  # uint(epoch millis)
      68 6C 61 62 65 6C 4F 70 74                  # text "labelOpt"
      F6                                          # null
```

---

## Cipher suite ID — registry

Identifies the AEAD + asymmetric scheme combination used. Forward-compat — readers MUST throw `CipherSuiteUnsupported` on unknown values (do not crash, do not skip MAC verification).

| `cipherSuiteId` | AEAD | Asymmetric | Status | Spec |
|---|---|---|---|---|
| `xchacha20poly1305_x25519_sealed_v1` | XChaCha20-Poly1305 (libsodium `crypto_secretbox`) | X25519 + `crypto_box_seal` | **Current** | spec 011 |
| (future) `xchacha20poly1305_x25519_authenticated_v1` | XChaCha20-Poly1305 | X25519 + `crypto_box` (authenticated sender) | Reserved | spec ~018 possibly |
| (future) `aes256gcm_x25519_sealed_v1` | AES-256-GCM | X25519 + `crypto_box_seal` | Reserved | спек ~020 (post-Lazysodium hypothetical) |

**Adding a new cipherSuiteId**: amend this contract. **Removing**: not allowed for ≥ 1 major release; mark deprecated first.

---

## Validation rules

Decoder MUST:
1. Reject envelope where `schemaVersion` not in {1}. Throw `CipherSuiteUnsupported` (yes, weird name — schemaVersion и cipherSuite parallel-evolved here; rename to `SchemaUnsupported` in future spec if separation matters).
2. Reject envelope where `cipherSuiteId` not in known registry. Throw `CipherSuiteUnsupported`.
3. Reject envelope where `nonce` length != expected for this cipher suite (24 bytes for XChaCha20).
4. Reject envelope where `recipients` array is empty. Throw `EnvelopeInvalid`.
5. Reject envelope where any `Recipient.sealedCEK` length != 80. Throw `EnvelopeInvalid`.
6. Reject envelope where own `deviceId` not in `recipients`. Throw `RecipientNotFound`.
7. Verify MAC during decrypt. Throw `MacFailed` if mismatch.

---

## Backward compatibility policy (CLAUDE.md §5)

- **Adding new optional fields** to envelope top-level: OK without bump (CBOR readers skip unknown keys).
- **Adding new `cipherSuiteId` value**: forward-compat (old readers throw `CipherSuiteUnsupported` gracefully, do not crash).
- **Renaming or removing fields**: schemaVersion bump 1 → 2 + reader-migration code (spec ~018 candidate).
- **Changing semantics of existing field**: same — bump + migration.

---

## Roundtrip tests (commonTest)

| Test | What | Phase |
|---|---|---|
| `roundtrip_singleRecipient` | Encode → decode → assert deep-equal; single recipient = 011 common case | 2 |
| `roundtrip_multiRecipient` | 3 recipients; verifies massiv handling for future ~016/017 | 2 |
| `roundtrip_documentKind` | metadata.kind = "document", labelOpt = "Паспорт" | 2 |
| `backwardCompat_v0_synthetic` | Test fixture v0 (synthetic) → reader returns proper error or graceful fallback | 2 |
| `forwardCompat_unknownCipherSuite` | Envelope with cipherSuiteId="future_suite_v1" → reader throws CipherSuiteUnsupported | 2 |
| `forwardCompat_extraField` | Envelope with extra map key → reader ignores it cleanly | 2 |
| `aadBinding` | Tamper with metadata.kind → decrypt throws MacFailed | 2 |
| `cek_zeroized` | ContentEncryptionKey.close() actually zeroes byte array (defensive) | 2 |

**Fixtures**: `core/src/commonTest/resources/wire-format/`:
- `crypto-envelope-v1-single-recipient.cbor`
- `crypto-envelope-v1-multi-recipient.cbor`
- `crypto-envelope-v1-document.cbor`
- `crypto-envelope-v0-synthetic.cbor` (synthetic, for backward-compat test)

---

## Test vectors

Beyond roundtrip, use **official libsodium test vectors** to verify the underlying primitives:

| Vector source | What it tests |
|---|---|
| libsodium official `test/default/box_seal.exp` | crypto_box_seal correctness |
| libsodium official `test/default/aead_xchacha20poly1305.exp` | XChaCha20-Poly1305 correctness |
| RFC 8439 test vectors | ChaCha20 stream cipher core |
| RFC 7748 test vectors | X25519 scalar multiplication |

Copy vectors into `core/src/commonTest/resources/libsodium-vectors/` for offline reproducibility.

---

<!-- novice summary -->

## TL;DR (простым языком)

**Что в этом файле.** Точное описание «как выглядит зашифрованный файл» — какие поля внутри, в каком порядке, какого размера.

**Главные поля:**
- `schemaVersion: 1` — версия формата (если будем менять — поднимем до 2).
- `cipherSuiteId: "xchacha20poly1305_x25519_sealed_v1"` — какие именно алгоритмы шифрования. Эта строка позволит нам в будущем поменять шифр, не выкидывая старые файлы.
- `nonce` — 24 случайных байт, чтобы одинаковые фото шифровались по-разному.
- `recipients: []` — список получателей. Для каждого — кому (deviceId) и зашифрованный для него ключ (sealedCEK). В нашем случае всегда 1 получатель, но массив поддерживает любую длину (для будущих групп).
- `ciphertext` — собственно зашифрованные байты фото.
- `metadata` — открытые метаданные: это «фото» или «документ», когда создано, как называется (для документов). Эти данные тоже защищены MAC'ом — их нельзя подменить.

**Формат сериализации — CBOR**, не JSON. Потому что фото = бинарные данные, а CBOR умеет их хранить компактно (в JSON пришлось бы base64-кодить, что на ~33% больше).

**8 roundtrip-тестов** проверяют, что мы можем encode → decode → получить точно то же самое; и что если что-то подменили — расшифровка явно падает.

**Тестируем не на наших данных, а на официальных тест-векторах libsodium** — это значит, что наша реализация даёт **точно те же результаты**, что и эталонная C-библиотека. Если в будущем там что-то пофиксят — мы синхронно увидим расхождение.
