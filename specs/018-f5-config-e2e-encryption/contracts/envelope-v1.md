# Contract: Envelope Wire Format v1 (F-5b)

**Spec**: [../spec.md](../spec.md) | **Plan**: [../plan.md](../plan.md) | **Data model**: [../data-model.md §0](../data-model.md#0-f-5b-amendment-2026-06-20-envelope-architecture)

**Replaces**: [sealed-config-v1.md](sealed-config-v1.md) (legacy — single-recipient symmetric).
**Implements**: hybrid envelope per [`specs/011-contacts-and-e2e-encrypted-media/spec.md` §C-2/§C-3](../../011-contacts-and-e2e-encrypted-media/spec.md#L125).

---

## Wire format

```kotlin
@Serializable
data class Envelope(
    val schemaVersion: Int = 1,
    val algorithm: String = "envelope-xchacha20poly1305-x25519-v1",

    @Serializable(with = ByteArrayBase64Serializer::class)
    val ciphertext: ByteArray,

    @Serializable(with = ByteArrayBase64Serializer::class)
    val nonce: ByteArray,

    @Serializable(with = ByteArrayBase64Serializer::class)
    val aad: ByteArray,

    /** DeviceId.value → Base64-encoded sealed CEK (80 bytes per recipient). */
    val recipientKeys: Map<String, @Serializable(with = ByteArrayBase64Serializer::class) ByteArray>
)
```

**Source of truth**: [`family.keys.api.Envelope`](../../../core/keys/src/commonMain/kotlin/family/keys/api/Envelope.kt).

## Field semantics

| Field | Type | Required | Description |
|---|---|---|---|
| `schemaVersion` | `Int` (≥ 1) | yes | Monotonically increasing. Future bumps for breaking changes. |
| `algorithm` | `String` | yes | Current: `"envelope-xchacha20poly1305-x25519-v1"`. New strings for new schemes (additive). |
| `ciphertext` | `ByteArray` | yes | XChaCha20-Poly1305 AEAD output under per-blob CEK. |
| `nonce` | `ByteArray` (24 B) | yes | XChaCha20-Poly1305 IETF nonce. Random per encryption. |
| `aad` | `ByteArray` | yes | `"family-storage::v1::{namespace}::{key}"` — verified at decryption against caller-recomputed value (context-confusion defence). |
| `recipientKeys` | `Map<String, ByteArray>` | yes (non-empty) | DeviceId → libsodium `crypto_box_seal` output for that recipient. Exactly 80 bytes per entry: `ephemeralPub(32) + ciphertext(32) + mac(16)`. |

## Wire-format invariants (CLAUDE.md rule 5)

1. **At least one recipient**: `recipientKeys.isNotEmpty()`.
2. **No duplicate DeviceIds**: enforced at construction
   ([Envelope `init` + EnvelopeConfigCipherImpl seal pre-check](../../../core/keys/src/commonMain/kotlin/family/keys/impl/EnvelopeConfigCipherImpl.kt)).
3. **Nonce size = 24 bytes** (XChaCha20-Poly1305 IETF requirement).
4. **Sealed CEK size = 80 bytes** per recipient (libsodium `crypto_box_seal` over 32-byte CEK).
5. **AAD recomputation**: reader recomputes AAD from `(namespace, key,
   schemaVersion)` and compares with stored. Mismatch → `AeadAuthFailed`
   (same surface as tampered ciphertext — no leakage).

## Encryption flow

```
seal(plaintext, recipients: List<RecipientPubKey>, aad):
    cek ← random 32 bytes
    (nonce, ct) ← XChaCha20-Poly1305.encrypt(plaintext, cek, aad)
    recipientKeys ← {}
    for r in recipients:
        sealedCek ← crypto_box_seal(cek, r.pubKey)    # libsodium
        recipientKeys[r.deviceId] ← sealedCek
    zeroize(cek)
    return Envelope(schemaVersion=1, algorithm=ALG_V1, ct, nonce, aad, recipientKeys)
```

## Decryption flow

```
open(envelope, myPrivKey, myDeviceId, expectedAad):
    if envelope.schemaVersion > SCHEMA_MAX:        return AlgorithmUnsupported  # H-3
    if envelope.algorithm != ALG_V1:               return AlgorithmUnsupported
    if envelope.nonce.size != 24:                  return InvalidInput
    if envelope.aad != expectedAad:                return AeadAuthFailed         # context confusion
    sealedCek ← envelope.recipientKeys[myDeviceId]
    if sealedCek == null:                          return NotARecipient
    if sealedCek.size != 80:                       return InvalidInput
    cek ← crypto_box_seal_open(sealedCek, myPrivKey)   # raises DecryptionFailed → AeadAuthFailed
    plaintext ← XChaCha20-Poly1305.decrypt(envelope.ciphertext+nonce, cek, envelope.aad)
    zeroize(cek)
    return plaintext
```

## Threat model coverage

| Threat | Defence |
|---|---|
| **Server-side read** (Firestore staff, leaked SA key) | Plaintext never on server; CEK sealed per recipient pub key. |
| **Tampered ciphertext** | AEAD MAC validates; `AeadAuthFailed`. |
| **Tampered sealed CEK** | libsodium `crypto_box_seal_open` validates internal MAC; `AeadAuthFailed`. |
| **Cross-context replay** (move envelope from `/users/u/data/cfg-a` to `/users/u/data/cfg-b`) | AAD includes both `namespace` and `key`; mismatch → `AeadAuthFailed`. |
| **Downgrade attack** (`schemaVersion = 0` forgery) | `schemaVersion >= 1` invariant at construction + H-3 early reject; also Firestore Rules enforce monotonic update. |
| **Future-version forgery** (`schemaVersion = 99`) | H-3 early reject before AEAD layer invoked. |
| **Non-recipient device tries to decrypt** | `recipientKeys[myDeviceId] == null` → `NotARecipient`. |
| **Revoked helper still reads new envelope** | Helper not included in fresh write's recipient list; previous envelopes remain readable (accepted residual). |
| **Helper-edits-owner-config without grant** | Firestore Security Rules deny write; envelope never reaches storage. |

## Backward compatibility

This is **v1**. Future versions (v2+) add fields additively (new optional keys
in JSON) or bump `algorithm` string to enable distinct decryption path. Old
clients reject unknown `algorithm` with `AlgorithmUnsupported`, surfacing
"please update" UI.

## Test coverage

- Roundtrip + multi-recipient: [`EnvelopeConfigCipherRoundtripTest`](../../../core/keys/src/commonTest/kotlin/family/keys/EnvelopeConfigCipherRoundtripTest.kt).
- Facade-level + opacity grep: [`EnvelopeRemoteStorageTest`](../../../core/keys/src/commonTest/kotlin/family/keys/EnvelopeRemoteStorageTest.kt).
- JSON roundtrip: [`WireFormatJsonTest.envelopeRoundtrip`](../../../core/keys/src/commonTest/kotlin/family/keys/WireFormatJsonTest.kt).
