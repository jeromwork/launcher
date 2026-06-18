# Wire Format Contract: `KeyBlob` schemaVersion 1

**Spec**: [../spec.md](../spec.md) | **Plan**: [../plan.md](../plan.md) | **Data model**: [../data-model.md](../data-model.md)

**Semantic version**: 1.0.0
**Schema version**: 1
**Status**: Draft (will be frozen at F-CRYPTO 1.0.0 release)
**Storage location**: `/data/data/<pkg>/files/keys/<keyId.raw>.blob` on Android (per-app sandbox)

---

## Purpose

`KeyBlob` — persisted format wrapper для **обёрнутых private keys** в `SecureKeyStore`. Не пересекает device boundary (не отправляется на сервер, не идёт в QR / deep-link). Но переживает **обновления приложения** — поэтому подлежит [CLAUDE.md rule 5](../../../CLAUDE.md) (wire format versioning).

---

## JSON shape

### Top-level object (schemaVersion=1)

```json
{
  "schemaVersion": 1,
  "algorithm": "X25519",
  "createdAt": "2026-06-17T10:30:00Z",
  "retiredAt": null,
  "replacedBy": null,
  "wrappedKey": "<base64-bytes>",
  "iv": "<base64-12-bytes>",
  "wrapKeyAlias": "family-crypto-wrap-key-v1"
}
```

### Field specifications

| Field | Type | Required | Description | Constraints |
|---|---|---|---|---|
| `schemaVersion` | `Int` | Yes | Schema version of this blob. | Must be 1 for current format. |
| `algorithm` | `String` | Yes | Algorithm of the wrapped private key. | Enum: `"X25519"`, `"Ed25519"`, `"AES-256"` (reserved for future). |
| `createdAt` | `String` (ISO-8601 Instant) | Yes | When the key was generated. | UTC, no timezone offset; format `YYYY-MM-DDTHH:MM:SSZ` or with fractional seconds. |
| `retiredAt` | `String?` (ISO-8601) | No | When the key was retired (rotation). | null = active; non-null = read-only key, no new encryption with it. |
| `replacedBy` | `String?` (KeyId) | No | If retired, which KeyId superseded this one. | Must be a valid KeyId per [KeyId format spec](#keyid-format). |
| `wrappedKey` | `String` (base64) | Yes | AES-256-GCM ciphertext of private key bytes. | Length depends on wrapped key (32 bytes plain → ~48 bytes after GCM tag). |
| `iv` | `String` (base64, exactly 12 bytes) | Yes | GCM nonce/IV for `wrappedKey`. | Exactly 12 bytes after base64-decode. Must be unique per (wrapKeyAlias × wrappedKey). |
| `wrapKeyAlias` | `String` | Yes | Android Keystore alias used to wrap. | For wrap-key rotation tracking. Defaults to `"family-crypto-wrap-key-v1"`. |

### KeyId format (referenced in `replacedBy`)

```
<prefix><name>(-<version>)?
```

- Prefix: one of `config-`, `media-`, `messenger-`, `recovery-`, `__internal-`.
- Name: kebab-case ASCII (lowercase letters, digits, hyphens).
- Version: optional `-v1`, `-v2`, etc. suffix.

**Examples**:
- `config-admin-identity-v1`
- `media-dek-photo-album-1-v1`
- `recovery-passphrase-derived-v1`
- `__internal-hkdf-device-salt-v1`

---

## Read/write algorithm

### Write

```
1. Receive (keyId: KeyId, privateKey: ByteArray, algorithm: String) from caller.
2. Open Android Keystore, get wrap key by alias (create if missing).
3. cipher = Cipher.getInstance("AES/GCM/NoPadding"), init(ENCRYPT_MODE, wrapKey).
4. wrappedKeyBytes = cipher.doFinal(privateKey)
5. ivBytes = cipher.iv (12 bytes for GCM)
6. blob = KeyBlob(
     schemaVersion = 1,
     algorithm = algorithm,
     createdAt = Clock.System.now(),
     retiredAt = null,
     replacedBy = null,
     wrappedKey = wrappedKeyBytes,
     iv = ivBytes,
     wrapKeyAlias = "family-crypto-wrap-key-v1"
   )
7. jsonBytes = Json.encodeToString(KeyBlob.serializer(), blob).encodeToByteArray()
8. File("/data/data/<pkg>/files/keys/${keyId.raw}.blob").writeBytes(jsonBytes)
```

### Read

```
1. Receive keyId: KeyId.
2. file = File("/data/data/<pkg>/files/keys/${keyId.raw}.blob")
3. IF !file.exists() → return null  (key not present or wiped)
4. jsonBytes = file.readBytes()
5. blob = Json.decodeFromString(KeyBlob.serializer(), jsonBytes.decodeToString())
6. IF blob.schemaVersion > CURRENT_SCHEMA_VERSION (=1) →
     throw UnsupportedSchemaVersion(found=blob.schemaVersion, known=1)
7. wrapKey = AndroidKeystore.getKey(blob.wrapKeyAlias)
8. IF wrapKey == null →
     throw KeystoreInvalidated("wrap key alias ${blob.wrapKeyAlias} not found")
9. cipher = Cipher.getInstance("AES/GCM/NoPadding")
10. cipher.init(DECRYPT_MODE, wrapKey, GCMParameterSpec(128, blob.iv))
11. plainBytes = cipher.doFinal(blob.wrappedKey)
12. return plainBytes
```

### Migration (future v1 → v2)

When v2 is introduced (e.g., adding `keyAttestation: ByteArray?` field):

```
1. read blob from file
2. if schemaVersion == 1:
     blob = migrateV1ToV2(blob)  // adds keyAttestation = null
3. proceed as v2
4. on first save → bump schemaVersion to 2
```

Migration function is `additive`-only for minor bumps. Breaking changes require major bump (2.x.x).

---

## Test fixtures

### `commonTest/resources/key-blob/v1-sample.json`

```json
{
  "schemaVersion": 1,
  "algorithm": "X25519",
  "createdAt": "2026-06-17T10:30:00Z",
  "retiredAt": null,
  "replacedBy": null,
  "wrappedKey": "ZGV0ZXJtaW5pc3RpYy10ZXN0LXdyYXBwZWQta2V5LWJ5dGVzAAAAAAAAAAA=",
  "iv": "AAAAAAAAAAAAAAAA",
  "wrapKeyAlias": "family-crypto-wrap-key-v1"
}
```

**Properties**:
- Deterministic content (no random/now timestamps).
- `wrappedKey`/`iv` are dummy bytes (not real encrypted) — for parsing tests only, not for decryption.
- This file is **frozen** at F-CRYPTO 1.0.0 release. Any future read code MUST parse it successfully.

### `commonTest/resources/key-blob/v1-retired-sample.json`

Same shape with `retiredAt` and `replacedBy` populated:

```json
{
  "schemaVersion": 1,
  "algorithm": "X25519",
  "createdAt": "2026-01-01T00:00:00Z",
  "retiredAt": "2026-06-17T10:30:00Z",
  "replacedBy": "config-admin-identity-v2",
  "wrappedKey": "ZGV0ZXJtaW5pc3RpYy10ZXN0LXdyYXBwZWQta2V5LWJ5dGVzAAAAAAAAAAA=",
  "iv": "AAAAAAAAAAAAAAAA",
  "wrapKeyAlias": "family-crypto-wrap-key-v1"
}
```

---

## Roundtrip test contract

```kotlin
// commonTest/wireformat/KeyBlobRoundtripTest.kt
@Test fun `keyBlob serializes and deserializes to identical bytes`() {
  val original = KeyBlob(
    schemaVersion = 1,
    algorithm = "X25519",
    createdAt = Instant.parse("2026-06-17T10:30:00Z"),
    retiredAt = null,
    replacedBy = null,
    wrappedKey = byteArrayOf(1, 2, 3),
    iv = ByteArray(12),
    wrapKeyAlias = "family-crypto-wrap-key-v1"
  )
  val json = Json.encodeToString(KeyBlob.serializer(), original)
  val parsed = Json.decodeFromString(KeyBlob.serializer(), json)
  assertEquals(original.schemaVersion, parsed.schemaVersion)
  assertEquals(original.algorithm, parsed.algorithm)
  assertContentEquals(original.wrappedKey, parsed.wrappedKey)
  // ... etc.
}
```

## Backward-compat read test contract

```kotlin
// commonTest/wireformat/KeyBlobBackwardCompatReadTest.kt
@Test fun `v1 sample fixture reads successfully on current code`() {
  val jsonBytes = readResource("/key-blob/v1-sample.json")
  val blob = Json.decodeFromString(KeyBlob.serializer(), jsonBytes.decodeToString())
  assertEquals(1, blob.schemaVersion)
  assertEquals("X25519", blob.algorithm)
  assertNull(blob.retiredAt)
}

@Test fun `unknown schemaVersion throws UnsupportedSchemaVersion`() {
  val jsonText = """{"schemaVersion":999,"algorithm":"X25519","createdAt":"...","wrappedKey":"...","iv":"...","wrapKeyAlias":"..."}"""
  assertFailsWith<CryptoException.UnsupportedSchemaVersion> {
    KeyBlobReader.read(jsonText)
  }
}
```

## Cross-platform byte parity test

```kotlin
// commonTest/wireformat/KeyBlobCrossPlatformParityTest.kt
@Test fun `serialized bytes are identical on android and jvm`() {
  val blob = KeyBlob(/* deterministic fixed values */)
  val jsonBytes = Json.encodeToString(KeyBlob.serializer(), blob).encodeToByteArray()
  // Expected bytes are hardcoded — generated once and checked into source.
  val expectedHex = "7b22736368656d6156657273696f6e223a312c...".hexToByteArray()
  assertContentEquals(expectedHex, jsonBytes)
}
```

---

## Backward compatibility policy

Per Clarifications Q8 + Article XIII + CLAUDE.md rule 5:

- **Within major version 1.x**: every minor release MUST be able to read all previous 1.x blobs.
- **Major bump 1.x → 2.0**: migrator MUST be written **before** release. Migrator path is in production code, exercised by unit test.
- **Forward compat**: reading future blob (`schemaVersion > known`) MUST throw `UnsupportedSchemaVersion`, not silent-default.

---

## Privacy / security notes

- `wrappedKey` content is **opaque ciphertext** — viewing the blob file does not reveal the key without TEE cooperation.
- `algorithm` field reveals "this is X25519 / Ed25519 key" — that's metadata, not key material. Acceptable.
- `createdAt` reveals timing — acceptable (no PII).
- `wrapKeyAlias` reveals device-key version — acceptable.
- **No PII** in any field.
- File path `/data/data/<pkg>/files/keys/<keyId>.blob` is in app sandbox; only this app + root can read.

---

## What is NOT in this contract

- **AEAD ciphertext envelope** (nonce + libsodium ciphertext): not a wire format that consumers can serialize/migrate themselves. F-CRYPTO returns it as opaque `Ciphertext` value class. If F-5 (Config E2E) puts these bytes in Firestore document — F-5 spec must define its own envelope wrapper with schemaVersion.
- **`EscrowBundle`** (for spec 017 social recovery): F-CRYPTO declares the type, but real serialization format is defined in spec 017.
- **Public keys** for pairing QR codes: defined in pairing-related spec (007), not here.

---

## TL;DR простым языком

Это **формат файла**, в котором хранятся «обёрнутые» приватные ключи на телефоне. Например, файл `config-admin-identity-v1.blob` содержит:

```json
{
  "schemaVersion": 1,           ← версия формата (важно для миграции в будущем)
  "algorithm": "X25519",         ← какой именно ключ обёрнут
  "createdAt": "2026-06-17...",  ← когда создан
  "retiredAt": null,             ← null = ключ ещё используется
  "replacedBy": null,            ← null = не заменён
  "wrappedKey": "...",           ← зашифрованные байты ключа
  "iv": "...",                   ← одноразовый код для расшифровки
  "wrapKeyAlias": "..."          ← какой TEE-ключ его обернул
}
```

**Зачем нужен документ контракта**: чтобы через год, когда формат поменяется (например, добавится поле «отпечаток TEE»), мы знали, что старые файлы должны читаться новой версией приложения **обязательно**. Кладём фиксированный пример `v1-sample.json` в тесты — он замораживается на момент 1.0.0 и навсегда служит проверкой «новая версия кода читает старые файлы».

**Что НЕ описано здесь**: формат самих зашифрованных данных, которые F-5 будет отправлять в Firestore (например, бабушкин config) — это работа спеки F-5. F-CRYPTO даёт «непрозрачные» байты.
