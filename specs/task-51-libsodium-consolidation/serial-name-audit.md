# @SerialName Audit (TASK-51 Phase 3)

**Generated**: 2026-06-26
**Branch**: `task-51-libsodium-consolidation`
**Purpose**: Pre-rename audit ensuring every wire-format type carries an explicit
`@SerialName("TypeName")` annotation so the upcoming `family.* → cryptokit.*`
namespace rename (Phase 4) does NOT cause byte drift in kotlinx.serialization
output. Without explicit `@SerialName`, kotlinx serializer falls back to FQN,
which changes on rename → Firestore documents become unreadable.

Tied to: FR-004, FR-016, Risk #2 in plan.md, SC-013.

---

## Audit table

Locations scanned:

- `core/crypto/src/commonMain/kotlin/family/crypto/api/values/` (cryptokit.crypto.api.values target types)
- `core/src/commonMain/kotlin/com/launcher/api/crypto/` (pairing.api target types — data-model §2)

Legend:
- **W** = wire-format type (gets serialized, persists to Firestore / disk / wire).
- **N** = not wire-format (opaque container / interface / object / contains private key).

| Type | Location | Kind | @Serializable? | @SerialName? | Action |
|---|---|---|---|---|---|
| `DeviceId` | `com/launcher/api/crypto/DeviceId.kt` | W (value class wrapper, UUID string) | yes | **MISSING** | Add `@SerialName("DeviceId")` |
| `DeviceIdentity` | `com/launcher/api/crypto/DeviceIdentity.kt` | W (Firestore `/links/{linkId}/devices/{deviceId}`) | yes | **MISSING** | Add `@SerialName("DeviceIdentity")` |
| `PublicKey` | `com/launcher/api/crypto/PublicKey.kt` | W (embedded in DeviceIdentity) | yes | **MISSING** | Add `@SerialName("PublicKey")` |
| `SigningPublicKey` | `com/launcher/api/crypto/SigningPublicKey.kt` | W (embedded in DeviceIdentity) | yes | **MISSING** | Add `@SerialName("SigningPublicKey")` |
| `EncryptedEnvelope` | `com/launcher/api/crypto/EncryptedEnvelope.kt` | W (spec 011 wire-format, Backblaze blob storage) | yes | **MISSING** | Add `@SerialName("EncryptedEnvelope")` |
| `Recipient` | `com/launcher/api/crypto/Recipient.kt` | W (embedded in EncryptedEnvelope) | yes | **MISSING** | Add `@SerialName("Recipient")` |
| `DeviceKeyPair` | `com/launcher/api/crypto/DeviceKeyPair.kt` | N (contains PrivateKey, never serialized) | no | — | none |
| `DeviceSigningKeyPair` | `com/launcher/api/crypto/DeviceSigningKeyPair.kt` | N (contains SigningPrivateKey) | no | — | none |
| `PrivateKey` / `SigningPrivateKey` | `com/launcher/api/crypto/PrivateKey.kt`, `SigningPrivateKey.kt` | N (opaque sealed interfaces) | no | — | none |
| `InMemoryPrivateKey` / `InMemorySigningPrivateKey` | `com/launcher/api/crypto/InMemoryPrivateKeys.kt` | N (test-only) | no | — | none |
| `ContentEncryptionKey` | `com/launcher/api/crypto/ContentEncryptionKey.kt` | N (zeroize-on-close, never serialized) | no | — | none |
| `DeviceIdentityRepository` | `com/launcher/api/crypto/DeviceIdentityRepository.kt` | N (interface / port) | no | — | none |
| `EncryptedMediaStorage` | `com/launcher/api/crypto/EncryptedMediaStorage.kt` | N (interface / port) | no | — | none |
| `RecipientResolver` | `com/launcher/api/crypto/RecipientResolver.kt` | N (functional interface) | no | — | none |
| `CryptoEnvelopeWireFormat` | `com/launcher/api/crypto/CryptoEnvelopeWireFormat.kt` | N (`object` with size constants) | no | — | none |
| `Ciphertext` | `family/crypto/api/values/Ciphertext.kt` | N (value class — opaque envelope bytes, AEAD adapter handles raw bytes; not used through kotlinx.serialization) | no | — | none |
| `KeyBlob` | `family/crypto/api/values/KeyBlob.kt` | W (spec 016 on-disk wrapped key wire format) | yes | **MISSING** | Add `@SerialName("KeyBlob")` |
| `KeyId` | `family/crypto/api/values/KeyId.kt` | N (value class wrapper, raw String key) | no | — | none |
| `KeyPair` | `family/crypto/api/values/KeyPair.kt` | N (contains private key bytes) | no | — | none |
| `SharedSecret` | `family/crypto/api/values/SharedSecret.kt` | N (32-byte ECDH output, transient, never serialized) | no | — | none |
| `SealedBlob` | `family/crypto/api/values/SealedBlob.kt` | N (libsodium `crypto_box_seal` output, opaque bytes — adapter handles raw) | no | — | none |
| `Signature` | `family/crypto/api/values/Signature.kt` | N (Ed25519 detached signature, raw bytes) | no | — | none |

Note: value-class wrappers around raw bytes (`Ciphertext`, `SealedBlob`, `Signature`, `SharedSecret`) are NOT `@Serializable`. They flow through ports as raw byte envelopes; kotlinx.serialization does NOT touch them. Adding `@Serializable` to them is OUT OF SCOPE for Phase 3 — would be a new public-surface change without compelling reason.

`KeyId` (value class wrapping a validated kebab-case String) is similarly NOT serialized via kotlinx today — used as map key in `SecureKeyStore` API.

---

## Missing @SerialName — list for T002

7 types need `@SerialName("<TypeName>")` annotation added:

1. `com.launcher.api.crypto.DeviceId` → `@SerialName("DeviceId")`
2. `com.launcher.api.crypto.DeviceIdentity` → `@SerialName("DeviceIdentity")`
3. `com.launcher.api.crypto.PublicKey` → `@SerialName("PublicKey")`
4. `com.launcher.api.crypto.SigningPublicKey` → `@SerialName("SigningPublicKey")`
5. `com.launcher.api.crypto.EncryptedEnvelope` → `@SerialName("EncryptedEnvelope")`
6. `com.launcher.api.crypto.Recipient` → `@SerialName("Recipient")`
7. `family.crypto.api.values.KeyBlob` → `@SerialName("KeyBlob")`

All names match the simple class name today, so the wire-format byte layout
does not change as a result of adding the annotation (kotlinx-serialization
default would have used the simple class name for these top-level types
**only** because the class is at package root and not polymorphic; explicit
annotation locks the contract irrespective of FQN). Critically — after Phase 4
namespace rename, the explicit `@SerialName` keeps the on-wire identifier
constant.

---

## Baseline golden vectors (T003)

**Baseline (pre-rename, 2026-06-26)**

- **Command**: `./gradlew :core:keys:jvmTest --tests "*EnvelopeConfigCipherRoundtripTest"`
- **Result**: PASS — `BUILD SUCCESSFUL in 7s`
- **Commit hash (post-T002, pre-rename)**: `7eb6fa391920dd6903741b56a378a0cc8467e115`
- **Test class**: `family.keys.EnvelopeConfigCipherRoundtripTest` (17 test methods including
  `singleRecipientRoundtrip`, `multiRecipientThreeDevicesEachCanOpen`,
  `crossUserDelegationOwnerAndHelperBothOpen`, `envelopeMapKeysSurviveJsonRoundtripImplicitlyByEqualityCheck`,
  `ciphertextDiffersOnEachSealEvenForSamePlaintextAndRecipients`,
  `tamperedCiphertextReturnsAeadAuthFailed`, `tamperedSealedCekReturnsAeadAuthFailed`,
  `aadMismatchReturnsAeadAuthFailed`, `nonRecipientCannotOpenReturnsNotARecipient`,
  `duplicateRecipientIdsRejected`, `emptyRecipientListRejected`,
  `futureSchemaVersionRejected`, `unknownAlgorithmRejected`,
  `oversizedPlaintextRejected`, `atMaxPlaintextSizeAccepted`,
  `emptyPlaintextRoundtrip`, `newRecipient`).

This is the **sentinel** for Phase 4 T015 post-rename verification.
After the Phase 4 namespace rename (`family.* → cryptokit.*`), re-running the
same command MUST still PASS — that proves the explicit `@SerialName` annotations
added in T002 successfully decoupled the on-wire identifier from the FQN class
path, and Firestore documents written before the rename remain readable.

If T015 fails after rename → @SerialName audit incomplete → return to Phase 3,
identify the type whose wire-format key changed, add `@SerialName(...)`, re-run.
