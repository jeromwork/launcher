# Tasks: F-CRYPTO — `core/crypto/` KMP module foundation

**Branch**: `j_f_crypto_core_module_17_06_26` | **Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)
**Generated**: 2026-06-17

Tasks decomposed from plan.md §"Rollout / verification" (14 phases). Each task traces to FR/US/SC/Plan section. `[P]` = parallel-safe (no file conflicts with other `[P]` in same phase).

---

## Phase 0 — Research & verification

### T601 — Verify ionspin/kotlin-multiplatform-libsodium actuality

**Trace**: research.md §R1 "Research-time проверка"; FR-014.
**What**: Open https://github.com/ionspin/kotlin-multiplatform-libsodium. Check:
- Last release date < 12 months.
- iOS targets declared (`iosX64`, `iosArm64`, `iosSimulatorArm64`).
- XChaCha20-Poly1305, `crypto_box_seal`/`crypto_box_seal_open`, X25519, Ed25519, HKDF available in public API.
- Open critical iOS issues > 90 days without response.
**Acceptance**: Record decision in `research.md` §R1 "Status" — either confirm ionspin OR document fallback path activation (BouncyCastle Android + cinterop iOS). Concrete version + commit SHA written.
**Dependencies**: none.
**Files**: `research.md` (update only).

### T602 — Decide & document libsodium binding version

**Trace**: research.md §R1.
**What**: Based on T601, write specific dependency line in plan: `com.ionspin.kotlin:libsodium-bindings:<v>`. If fallback — write BouncyCastle replacement plan to research.md §R1 "If research shows library dead" section.
**Acceptance**: `gradle/libs.versions.toml` candidate entry written (not committed yet — used in T604).
**Dependencies**: requires T601.
**Files**: `research.md`.

---

## Phase 1 — Module scaffolding (no behaviour)

### T610 — Create `core/crypto/` Gradle subproject

**Trace**: FR-001, plan.md §"Project Structure"; SC-006.
**What**: Create directories: `core/crypto/src/{commonMain,androidMain,iosMain,jvmMain,commonTest,androidInstrumentedTest}/kotlin/family/crypto/{api,libsodium,fake,stubs,exception}/`.
**Acceptance**: `./gradlew projects` lists `:core:crypto`. No source files yet — just directory structure.
**Dependencies**: requires T602.
**Files**: directory tree only.

### T611 — Add `core/crypto/build.gradle.kts`

**Trace**: FR-001, FR-002, FR-003, FR-004, FR-005; plan.md §"Project Structure" + "Dependency impact"; quickstart.md "Module setup".
**What**: Write `build.gradle.kts` per quickstart.md outline:
- KMP plugin + androidLibrary + kotlin-serialization plugins.
- Targets: `androidTarget()`, `jvm()`, `iosX64()`, `iosArm64()`, `iosSimulatorArm64()`.
- `commonMain` dependencies: ionspin libsodium, kotlinx-coroutines, kotlinx-datetime, kotlinx-serialization-json.
- `commonTest` dependencies: kotlin-test, kotest-runner-junit5, kotest-property.
- Android namespace `family.crypto`, compileSdk 35, minSdk 23.
- **Inline TODO** (FR-004): `// TODO(extract-when-2nd-consumer): git filter-repo + Apache 2.0 at extract`.
**Acceptance**: `./gradlew :core:crypto:assemble` succeeds (empty module compiles).
**Dependencies**: requires T610.
**Files**: `core/crypto/build.gradle.kts`.

### T612 — Add `:core:crypto` to `settings.gradle.kts`

**Trace**: plan.md §"Project Structure".
**What**: Add `include(":core:crypto")` to root `settings.gradle.kts`.
**Acceptance**: Gradle sync detects new subproject.
**Dependencies**: requires T611.
**Files**: `settings.gradle.kts`.

### T613 — Fitness function: no launcher-module dependencies

**Trace**: FR-005, SC-006; plan.md §"Test strategy" → "Fitness functions"; quickstart.md "Verify no launcher-module dependencies".
**What**: Add Gradle task `verifyCryptoIsolation` in `core/crypto/build.gradle.kts`:
```kotlin
tasks.register("verifyCryptoIsolation") {
  doLast {
    val deps = configurations.flatMap { it.dependencies }
      .filterIsInstance<ProjectDependency>()
      .map { it.dependencyProject.path }
    val forbidden = deps.filter { it.startsWith(":") && it != ":core:crypto" }
    check(forbidden.isEmpty()) {
      ":core:crypto MUST NOT depend on launcher modules. Found: $forbidden"
    }
  }
}
tasks.named("check") { dependsOn("verifyCryptoIsolation") }
```
**Acceptance**: `./gradlew :core:crypto:verifyCryptoIsolation` passes (no project deps). Negative test: temporarily add `implementation(project(":core:wizard"))` → task fails. Revert.
**Dependencies**: requires T611.
**Files**: `core/crypto/build.gradle.kts`.

---

## Phase 2 — Domain types & value classes

### T620 [P] — Add `KeyNamespace` sealed class

**Trace**: FR-010, Clarifications Q2; data-model.md §"KeyNamespace".
**What**: Create `commonMain/api/values/KeyNamespace.kt` per data-model.md. Sealed objects: Config, Media, Messenger, Recovery, Internal. Companion `isValidPrefix(raw)` + `allPrefixes()`.
**Acceptance**: `KeyNamespace.allPrefixes() == listOf("config-", "media-", "messenger-", "recovery-", "__internal-")`.
**Dependencies**: requires T611.
**Files**: `commonMain/api/values/KeyNamespace.kt`.

### T621 [P] — Add `KeyId` value class with prefix validation

**Trace**: FR-010, Clarifications Q2; data-model.md §"KeyId"; research.md §R6.
**What**: Create `core/crypto/src/commonMain/kotlin/family/crypto/api/values/KeyId.kt` per data-model.md exact signature. `init` validates: prefix from `KeyNamespace`, regex kebab-case ASCII.
**Acceptance**:
- `KeyId("config-admin-identity-v1")` succeeds.
- `KeyId("photo-album-v1")` throws `IllegalArgumentException`.
- `KeyId("Config-Admin-V1")` throws (uppercase).
- `KeyId("")` throws (empty).
**Dependencies**: requires T611, T620 (KeyNamespace).
**Files**: `commonMain/api/values/KeyId.kt`.

### T622 [P] — Add cryptographic value types

**Trace**: data-model.md §"Cryptographic value types"; FR-006, FR-007.
**What**: Create files:
- `commonMain/api/values/KeyPair.kt` (data class, redacted toString).
- `commonMain/api/values/SharedSecret.kt` (value class).
- `commonMain/api/values/Signature.kt` (value class).
- `commonMain/api/values/SealedBlob.kt` (value class).
- `commonMain/api/values/Ciphertext.kt` (value class).
**Acceptance**: All compile in `commonMain`. `KeyPair.toString()` returns `"KeyPair(algorithm=..., publicKey=<N bytes>, privateKey=<REDACTED>)"`.
**Dependencies**: requires T611.
**Files**: 5 files в `commonMain/api/values/`.

### T623 [P] — Add `KeyBlob` data class with kotlinx.serialization

**Trace**: FR-016, FR-025; data-model.md §"KeyBlob"; contracts/key-blob-v1.md.
**What**: Create `commonMain/api/values/KeyBlob.kt`:
- `@Serializable data class KeyBlob(schemaVersion, algorithm, createdAt: Instant, retiredAt: Instant?, replacedBy: String?, wrappedKey: ByteArray, iv: ByteArray, wrapKeyAlias: String)`.
- Companion `CURRENT_SCHEMA_VERSION = 1`.
- `toString()` MUST NOT log `wrappedKey`/`iv` raw bytes.
- `equals`/`hashCode` correct для ByteArray fields (compare content).
**Acceptance**: Compiles. `KeyBlob.toString()` doesn't include base64 of wrappedKey.
**Dependencies**: requires T611.
**Files**: `commonMain/api/values/KeyBlob.kt`.

### T624 [P] — Add stub-only value types (`RotationReason`, `RetiredKey`, `EscrowBundle`)

**Trace**: FR-011, FR-012; data-model.md §"Stub-only types".
**What**: Create 3 files per data-model.md:
- `RotationReason.kt` (sealed class: Periodic, SuspectedCompromise, DeviceChange, Custom).
- `RetiredKey.kt` (data class).
- `EscrowBundle.kt` (data class with `encryptedPayload: ByteArray`).
**Acceptance**: All compile.
**Dependencies**: requires T611, T621 (KeyId).
**Files**: 3 files в `commonMain/api/values/`.

### T625 [P] — Add `CryptoException` sealed hierarchy

**Trace**: failure-recovery checklist O-1; data-model.md §"Exception hierarchy".
**What**: Create `commonMain/exception/CryptoException.kt` — sealed class с 11 subclasses (DecryptionFailed, MalformedCiphertext, InvalidPublicKey, RandomSourceUnavailable, KeystoreUnavailable, KeystoreInvalidated, UnsupportedSchemaVersion, KeyBlobDeserializationFailed, WycheproofRejection, NonceReuseDetected, NotImplementedOnIos).
**Acceptance**: All 11 exception classes compile. Each has clear constructor.
**Dependencies**: requires T611, T621 (KeyId for some).
**Files**: `commonMain/exception/CryptoException.kt`.

---

## Phase 3 — Domain ports (interfaces only)

### T630 [P] — Add `AeadCipher` port

**Trace**: FR-006, Clarifications Q3 (nonce policy); data-model.md §"AeadCipher".
**What**: Create `commonMain/api/AeadCipher.kt` per data-model.md exact KDoc + signatures. Suspend functions. No `nonce` parameter on `encrypt`.
**Acceptance**: Compiles. KDoc explicit about nonce-internally-managed.
**Dependencies**: requires T622 (Ciphertext value class), T625 (CryptoException).
**Files**: `commonMain/api/AeadCipher.kt`.

### T631 [P] — Add `AsymmetricCrypto` port (incl. `sealForRecipient`/`openSealed`)

**Trace**: FR-007 (sealCEK/unsealCEK added in clarify); data-model.md §"AsymmetricCrypto"; ADR-008 dependency.
**What**: Create `commonMain/api/AsymmetricCrypto.kt` per data-model.md. Включить `sealForRecipient` / `openSealed` (sealed-box wrappers).
**Acceptance**: Compiles. `sealForRecipient` and `openSealed` signatures present.
**Dependencies**: requires T622, T625.
**Files**: `commonMain/api/AsymmetricCrypto.kt`.

### T632 [P] — Add `KeyDerivation` port

**Trace**: FR-008; data-model.md §"KeyDerivation".
**What**: Create `commonMain/api/KeyDerivation.kt`. HKDF semantics. `info` field arbitrary ASCII string.
**Acceptance**: Compiles.
**Dependencies**: requires T611.
**Files**: `commonMain/api/KeyDerivation.kt`.

### T633 [P] — Add `RandomSource` port

**Trace**: FR-009; data-model.md §"RandomSource".
**What**: Create `commonMain/api/RandomSource.kt`. Suspend `nextBytes(size)`.
**Acceptance**: Compiles.
**Dependencies**: requires T625.
**Files**: `commonMain/api/RandomSource.kt`.

### T634 — Add `SecureKeyStore` expect class

**Trace**: FR-010, Clarifications Q1; data-model.md §"SecureKeyStore"; research.md §R2.
**What**:
- `commonMain/api/SecureKeyStore.kt`: `expect class SecureKeyStore(context: KeyStoreContext)` with suspend `store`/`load`/`delete`.
- `commonMain/api/KeyStoreContext.kt`: `expect class KeyStoreContext`.
**Acceptance**: Compiles `commonMain` (actuals в Phase 5/6).
**Dependencies**: requires T621 (KeyId), T625.
**Files**: 2 files в `commonMain/api/`.

### T635 [P] — Add `KeyRotation` port + `StubKeyRotation`

**Trace**: FR-011; data-model.md §"KeyRotation".
**What**:
- `commonMain/api/KeyRotation.kt` (interface).
- `commonMain/stubs/StubKeyRotation.kt` (real-impl = stub throwing `NotImplementedError`, except `currentKeyId` / `keyHistory` return safe defaults).
**Acceptance**: Compiles. Stub `rotateIdentityKey` throws with message referring to spec 017.
**Dependencies**: requires T621, T624.
**Files**: 2 files.

### T636 [P] — Add `KeyEscrow` port + `StubKeyEscrow`

**Trace**: FR-012; data-model.md §"KeyEscrow".
**What**:
- `commonMain/api/KeyEscrow.kt` (interface).
- `commonMain/stubs/StubKeyEscrow.kt` (throws `NotImplementedError`).
**Acceptance**: Compiles.
**Dependencies**: requires T624.
**Files**: 2 files.

---

## Phase 4 — Fake adapters & property tests

### T640 [P] — Add `FakeAeadCipher`

**Trace**: FR-017; data-model.md; plan.md §"Test strategy".
**What**: Create `commonTest/fake/FakeAeadCipher.kt`:
- Identity encryption (XOR with constant key — NOT real crypto).
- `@VisibleForTesting`-equivalent annotation/comment.
- Embed nonce in returned ciphertext (size + 24 = output).
- Detect nonce reuse via internal `Set<Pair<ByteArray, ByteArray>>` (key × nonce).
**Acceptance**: Roundtrip test passes: `decrypt(encrypt(p, k))` == `p`. Nonce reuse throws `NonceReuseDetected`.
**Dependencies**: requires T630.
**Files**: `commonTest/fake/FakeAeadCipher.kt`.

### T641 [P] — Add `FakeAsymmetricCrypto`

**Trace**: FR-017.
**What**: Create `commonTest/fake/FakeAsymmetricCrypto.kt`:
- Deterministic keypair generation (seed counter).
- Identity-style ECDH (deterministic shared secret from concatenation).
- Sign = HMAC-like deterministic.
- `sealForRecipient` = identity XOR with recipient pub.
**Acceptance**: ECDH symmetry: `DH(a, B) == DH(b, A)`. Sign+verify roundtrip works.
**Dependencies**: requires T631.
**Files**: `commonTest/fake/FakeAsymmetricCrypto.kt`.

### T642 [P] — Add `FakeKeyDerivation`, `FakeRandomSource`, `FakeSecureKeyStore`

**Trace**: FR-017.
**What**: 3 small fake adapters in `commonTest/fake/`:
- `FakeKeyDerivation`: HKDF deterministic via SHA256-based KDF (test-only).
- `FakeRandomSource`: seeded Random, deterministic для reproducibility.
- `FakeSecureKeyStore` в `jvmTest/fake/` (нужен JVM target): in-memory HashMap.
**Acceptance**: All deterministic.
**Dependencies**: requires T632, T633, T634.
**Files**: 3 files.

### T643 — RFC KAT fixtures + parser

**Trace**: FR-019; research.md §R5; quickstart.md "Fixture layout".
**What**:
- Create `commonTest/resources/rfc-test-vectors/` directory.
- Extract vectors from RFC text → JSON:
  - `rfc7748-x25519.json` (RFC 7748 §6.1 — 2 vectors).
  - `rfc8032-ed25519.json` (RFC 8032 §7.1 — 5 vectors).
  - `rfc8439-chacha20-poly1305.json` (RFC 8439 App. A.2/A.3/A.5 — 3 vectors).
  - `rfc5869-hkdf.json` (RFC 5869 App. A — 3 vectors).
  - `xchacha20-ietf-draft.json` (XChaCha20 IETF draft App. A — 1-2 vectors).
- Write `commonTest/util/TestVectorParser.kt` для JSON parsing.
- Helper `String.hexToByteArray()` extension (commonTest util).
**Acceptance**: Parser reads all 5 JSON files successfully.
**Dependencies**: requires T611.
**Files**: 5 JSON + 2 .kt files.

### T644 [P] — Property test: AEAD roundtrip + tamper detection

**Trace**: FR-021 (AEAD roundtrip, tamper detection).
**What**: `commonTest/property/AeadRoundtripPropertyTest.kt`:
- 1000 iterations: random plaintext + key + aad → encrypt → decrypt → equals.
- Tamper: flip 1 bit in ciphertext → decrypt MUST throw `DecryptionFailed`.
Run against `FakeAeadCipher` (later also Real).
**Acceptance**: Test passes with seed 12345 (deterministic).
**Dependencies**: requires T640.
**Files**: `commonTest/property/AeadRoundtripPropertyTest.kt`.

### T645 [P] — Property test: ECDH symmetry

**Trace**: FR-021.
**What**: `commonTest/property/EcdhSymmetryPropertyTest.kt`:
- 1000 iterations: generate kpA, kpB → `DH(privA, pubB) == DH(privB, pubA)`.
**Acceptance**: All iterations pass.
**Dependencies**: requires T641.
**Files**: `commonTest/property/EcdhSymmetryPropertyTest.kt`.

### T646 [P] — Property test: sign/verify roundtrip + tamper detection

**Trace**: FR-021.
**What**: `commonTest/property/SignVerifyTamperPropertyTest.kt`:
- Sign + verify roundtrip 1000 iterations.
- Flip 1 bit in signature → verify returns false.
- Flip 1 bit in message → verify returns false.
**Acceptance**: All assertions pass.
**Dependencies**: requires T641.
**Files**: `commonTest/property/SignVerifyTamperPropertyTest.kt`.

### T647 [P] — Property test: nonce reuse rejection

**Trace**: FR-021.
**What**: `commonTest/property/NonceReuseRejectionPropertyTest.kt`:
- Attempt encrypt twice with same key + same nonce + different plaintext → adapter MUST raise (or property test catches deterministic non-rejection via Fake's internal tracking).
**Acceptance**: `NonceReuseDetected` thrown.
**Dependencies**: requires T640.
**Files**: `commonTest/property/NonceReuseRejectionPropertyTest.kt`.

### T648 [P] — Property test: KeyId prefix validation

**Trace**: FR-010, edge cases.
**What**: `commonTest/property/KeyIdPrefixPropertyTest.kt`:
- Random valid prefix + suffix → succeeds.
- Random invalid prefix → `IllegalArgumentException`.
**Acceptance**: 100 random valid + 100 random invalid all behave as expected.
**Dependencies**: requires T621.
**Files**: `commonTest/property/KeyIdPrefixPropertyTest.kt`.

---

## Phase 5 — Real adapters (libsodium)

### T650 — Add `LibsodiumAeadCipher` (XChaCha20-Poly1305)

**Trace**: FR-013; data-model.md; research.md §R1; Сценарий 1.
**What**: Create `commonMain/libsodium/LibsodiumAeadCipher.kt`:
- Use ionspin libsodium API for XChaCha20-Poly1305 IETF.
- Generate random 24-byte nonce inside `encrypt`.
- Concat nonce + ciphertext + tag into `Ciphertext.bytes`.
- `decrypt` extracts nonce, calls libsodium, returns plaintext.
- On MAC failure → `CryptoException.DecryptionFailed`.
- **`internal` test-only constructor** accepting forced nonce (per research.md §R7).
**Acceptance**: RFC 8439 + XChaCha20 KAT vectors pass.
**Dependencies**: requires T630, T640, T643.
**Files**: `commonMain/libsodium/LibsodiumAeadCipher.kt`.

### T651 — Add `LibsodiumAsymmetricCrypto`

**Trace**: FR-013 (X25519 + Ed25519 + sealed-box).
**What**: Create `commonMain/libsodium/LibsodiumAsymmetricCrypto.kt`:
- X25519 keypair gen via `crypto_box_keypair`.
- Ed25519 keypair via `crypto_sign_keypair`.
- ECDH via `crypto_scalarmult` (X25519 raw).
- Sign/verify via `crypto_sign_detached` / `crypto_sign_verify_detached`.
- `sealForRecipient` via `crypto_box_seal`.
- `openSealed` via `crypto_box_seal_open`.
- Invalid pubkey → `CryptoException.InvalidPublicKey`.
**Acceptance**: RFC 7748 + RFC 8032 KAT pass. Sealed-box roundtrip works.
**Dependencies**: requires T631, T641, T643.
**Files**: `commonMain/libsodium/LibsodiumAsymmetricCrypto.kt`.

### T652 — Add `LibsodiumKeyDerivation` (HKDF-SHA256)

**Trace**: FR-013 (HKDF).
**What**: Create `commonMain/libsodium/LibsodiumKeyDerivation.kt` using `crypto_kdf_hkdf_sha256` (libsodium 1.0.18+).
**Acceptance**: RFC 5869 KAT vectors pass.
**Dependencies**: requires T632, T643.
**Files**: `commonMain/libsodium/LibsodiumKeyDerivation.kt`.

### T653 — Add `LibsodiumRandomSource`

**Trace**: FR-013.
**What**: Create `commonMain/libsodium/LibsodiumRandomSource.kt` using `randombytes_buf`.
**Acceptance**: `nextBytes(32)` returns 32 random bytes, two calls produce different bytes.
**Dependencies**: requires T633.
**Files**: `commonMain/libsodium/LibsodiumRandomSource.kt`.

### T654 [P] — RFC KAT test: X25519 (RFC 7748)

**Trace**: FR-019; SC-002.
**What**: `commonTest/kat/X25519KatTest.kt` — load `rfc7748-x25519.json`, run all vectors against `LibsodiumAsymmetricCrypto`.
**Acceptance**: All vectors pass byte-identically.
**Dependencies**: requires T651, T643.
**Files**: `commonTest/kat/X25519KatTest.kt`.

### T655 [P] — RFC KAT test: Ed25519 (RFC 8032)

**Trace**: FR-019; SC-002.
**What**: `commonTest/kat/Ed25519KatTest.kt`. Run sign + verify vectors.
**Acceptance**: All vectors pass.
**Dependencies**: requires T651, T643.
**Files**: `commonTest/kat/Ed25519KatTest.kt`.

### T656 [P] — RFC KAT test: ChaCha20-Poly1305 + XChaCha20 (RFC 8439 + IETF draft)

**Trace**: FR-019; SC-002.
**What**: `commonTest/kat/ChaCha20Poly1305KatTest.kt`.
**Acceptance**: All vectors pass.
**Dependencies**: requires T650, T643.
**Files**: `commonTest/kat/ChaCha20Poly1305KatTest.kt`.

### T657 [P] — RFC KAT test: HKDF-SHA256 (RFC 5869)

**Trace**: FR-019; SC-002.
**What**: `commonTest/kat/HkdfKatTest.kt`.
**Acceptance**: All RFC 5869 App. A vectors pass.
**Dependencies**: requires T652, T643.
**Files**: `commonTest/kat/HkdfKatTest.kt`.

### T658 — Wycheproof subset: download + pin

**Trace**: FR-020; research.md §R5, R8 (snapshot pin policy); Clarifications Q7.
**What**:
- Pick Wycheproof commit SHA from https://github.com/google/wycheproof.
- Download subset JSON files: `x25519_test.json`, `eddsa_test.json`, `chacha20_poly1305_test.json`.
- Pick representative subset (low-order, malleable signatures, point-at-infinity, AAD edge cases) — ~50 vectors total.
- Place in `commonTest/resources/wycheproof-subset/`.
- Document pinned commit SHA in `docs/dev/crypto-review.md` (created in T6A0).
**Acceptance**: Files exist, JSON parses, commit SHA recorded.
**Dependencies**: requires T611.
**Files**: 3 JSON files в `commonTest/resources/wycheproof-subset/`.

### T659 [P] — Wycheproof test: X25519 edge cases

**Trace**: FR-020; SC-003.
**What**: `commonTest/wycheproof/WycheproofX25519Test.kt` — load subset, run against `LibsodiumAsymmetricCrypto.deriveSharedSecret`. Expected: low-order points → throw `InvalidPublicKey` (or `WycheproofRejection`).
**Acceptance**: All subset vectors pass (expected outcomes match).
**Dependencies**: requires T651, T658.
**Files**: `commonTest/wycheproof/WycheproofX25519Test.kt`.

### T65A [P] — Wycheproof test: Ed25519 malleability

**Trace**: FR-020.
**What**: `commonTest/wycheproof/WycheproofEd25519Test.kt`. Malleable signatures rejected.
**Acceptance**: Subset pass.
**Dependencies**: requires T651, T658.
**Files**: `commonTest/wycheproof/WycheproofEd25519Test.kt`.

### T65B [P] — Property tests run against Real adapters too

**Trace**: FR-021; SC-005 (Fake-Real parity).
**What**: Extract property test bodies из Phase 4 into reusable functions; create test classes that run them against both Fake and Real adapters. Parameterized tests.
**Acceptance**: All property tests pass against both implementations.
**Dependencies**: requires T644, T645, T646, T647, T650, T651.
**Files**: rewrite property test classes to parameterized.

---

## Phase 6 — `SecureKeyStore` Android adapter (wrap pattern)

### T660 — Add `androidMain/SecureKeyStore.kt` (wrap pattern impl)

**Trace**: FR-015; research.md §R3; Сценарий 3+4.
**What**: Create `androidMain/kotlin/family/crypto/SecureKeyStore.kt`:
- `actual class SecureKeyStore(context: KeyStoreContext)`.
- `actual class KeyStoreContext(val androidContext: Context)`.
- `ensureWrapKey()` — get-or-create AES-256-GCM ключ в `AndroidKeyStore` с alias `family-crypto-wrap-key-v1`. Params per research.md §R3: PURPOSE_ENCRYPT|DECRYPT, BLOCK_MODE_GCM, no padding, setIsStrongBoxBacked(true) fallback, setUserAuthenticationRequired(false), 256-bit.
- `store(keyId, secret)`: wrap via Cipher AES/GCM/NoPadding → serialize `KeyBlob` → write to `/data/data/<pkg>/files/keys/${keyId.raw}.blob`.
- `load(keyId)`: read file → parse `KeyBlob` → check schemaVersion → unwrap.
- `delete(keyId)`: delete file (idempotent).
- Exception mapping: `KeyStoreException` → `KeystoreUnavailable`, `UnrecoverableKeyException` → `KeystoreInvalidated`.
**Acceptance**: Compiles `androidMain`. Instrumentation tests (T6B*) pass.
**Dependencies**: requires T623, T625, T634.
**Files**: `androidMain/SecureKeyStore.kt`.

### T661 [P] — JVM in-memory `SecureKeyStore` (test only)

**Trace**: data-model.md; quickstart.md.
**What**: `jvmMain/kotlin/family/crypto/SecureKeyStore.kt`:
- `actual class SecureKeyStore(context: KeyStoreContext)` using in-memory HashMap.
- `actual class KeyStoreContext` — empty.
- KDoc: "FOR TESTS ONLY — not for production. Use Android Keystore on real devices."
**Acceptance**: Compiles, JVM tests can construct + use it.
**Dependencies**: requires T634.
**Files**: `jvmMain/SecureKeyStore.kt`.

---

## Phase 7 — iOS stub-screamer

### T670 — Add `iosMain/SecureKeyStore.kt` stub-screamer

**Trace**: Clarifications Q1; research.md §R2; FR-002.
**What**: `iosMain/kotlin/family/crypto/SecureKeyStore.kt`:
- `actual class SecureKeyStore(context: KeyStoreContext)`.
- `actual class KeyStoreContext` — empty.
- All methods throw `CryptoException.NotImplementedOnIos("SecureKeyStore iOS adapter — see V-1 (iOS Admin Preset) spec")`.
- KDoc explicit about stub status + planned implementation.
**Acceptance**: iOS source set compiles (даже без macOS). Runtime call throws clearly.
**Dependencies**: requires T625, T634.
**Files**: `iosMain/SecureKeyStore.kt`.

### T671 [P] — Document iOS stub TODO

**Trace**: research.md §R2 "Exit ramp".
**What**: Inline `// TODO(physical-mac): iOS build verification on macOS host; replace stub when V-1 spec ships` в iosMain SecureKeyStore.kt.
**Acceptance**: TODO grep'имо.
**Dependencies**: requires T670.
**Files**: `iosMain/SecureKeyStore.kt`.

---

## Phase 8 — DI wiring + Detekt + R8

### T680 — Add Koin DI module в `:app`

**Trace**: FR-030; data-model.md §"Lifecycle" → "Initialization order"; quickstart.md "DI wiring".
**What**: Create `app/src/main/kotlin/.../CryptoModule.kt` Koin module wiring real adapters (Libsodium*, KeystoreSecureKeyStore, StubKeyRotation, StubKeyEscrow).
**Acceptance**: `:app` runtime resolves all 7 ports without crash. Manual smoke on emulator.
**Dependencies**: requires T650-T653, T660, T635, T636.
**Files**: `app/src/main/kotlin/.../CryptoModule.kt`.

### T681 — Add `assertNoFakeCryptoInRelease()` to Application

**Trace**: FR-018, FR-030; quickstart.md "Application initialization assertion"; SC-011.
**What**: Edit existing `FamilyLauncherApplication.kt`:
- In `onCreate` after Koin start, call `assertNoFakeCryptoInRelease()`.
- Function checks `get<AeadCipher>()::class.simpleName?.startsWith("Fake")` — fail-fast if Fake in release.
- Guarded by `if (BuildConfig.DEBUG) return`.
**Acceptance**: Manual: temporarily replace binding с Fake → release build crashes on start with clear message. Revert.
**Dependencies**: requires T680.
**Files**: `app/src/main/kotlin/.../FamilyLauncherApplication.kt`.

### T682 — Detekt rule `FakeCryptoInReleaseRule`

**Trace**: FR-018, SC-011; research.md §R8.
**What**:
- Create new subproject `tools/detekt-rules/` (if not exists).
- Implement `FakeCryptoInReleaseRule.kt` per research.md §R8.
- Unit test for the rule itself (positive + negative cases).
- Register in `detekt-config.yml` for `:app`.
**Acceptance**: `./gradlew detekt` fails when adding `import family.crypto.fake.FakeAeadCipher` in `app/src/main/...`. Reverting clears.
**Dependencies**: requires T640-T642 (Fake adapters exist), T680.
**Files**: `tools/detekt-rules/src/main/kotlin/.../FakeCryptoInReleaseRule.kt` + test + `detekt-config.yml`.

### T683 [P] — R8 / ProGuard rule (defense-in-depth)

**Trace**: research.md §R8 + tamper-resistance O-1; SC-011.
**What**: Add to `app/proguard-rules.pro`:
```
# Physically remove Fake* crypto adapters from release APK
-assumenosideeffects class family.crypto.fake.** { *; }
```
**Acceptance**: Release APK doesn't contain Fake* classes (verify via `unzip + grep` or `dexdump`).
**Dependencies**: requires T640-T642.
**Files**: `app/proguard-rules.pro`.

---

## Phase 9 — Cross-platform vectors + backward-compat fixture

### T690 — Generate cross-platform test vectors

**Trace**: FR-022, FR-027; research.md §R7; Сценарий 2; SC-007.
**What**: One-time script generating deterministic vectors via Libsodium adapters with seeded RandomSource + forcedNonce:
- X25519 keypair-from-seed (10 entries).
- AEAD encrypt with known key + nonce + plaintext + aad (10 entries).
- Ed25519 sign known msg (5 entries).
Save to `commonTest/resources/cross-platform-vectors/encryption-roundtrip-v1.json`.
**Acceptance**: File generated, committed.
**Dependencies**: requires T650, T651.
**Files**: 1 JSON file + temporary generator script (not committed).

### T691 [P] — `CrossPlatformVectorParityTest`

**Trace**: FR-022; SC-007.
**What**: `commonTest/crossplatform/CrossPlatformVectorParityTest.kt`:
- Load `encryption-roundtrip-v1.json`.
- Per vector: run via Real adapter, assert bytes-equals expected.
- Test runs on both `jvmTest` and `androidUnitTest` source sets — same fixture, same expected bytes.
**Acceptance**: Test green on both targets. Negative test (mutate adapter to produce different bytes) → fails on both.
**Dependencies**: requires T690.
**Files**: `commonTest/crossplatform/CrossPlatformVectorParityTest.kt`.

### T692 [P] — KeyBlob v1 backward-compat fixture

**Trace**: FR-025, FR-026; contracts/key-blob-v1.md; Сценарий 11; SC-007.
**What**: Create frozen fixtures:
- `commonTest/resources/key-blob/v1-sample.json` (active key, retiredAt=null).
- `commonTest/resources/key-blob/v1-retired-sample.json` (retired with replacedBy).
- Values per contracts/key-blob-v1.md (deterministic test bytes, base64).
**Acceptance**: Files exist. Frozen — never modify after F-CRYPTO 1.0.0 release. Document in README of dir.
**Dependencies**: requires T623.
**Files**: 2 JSON files в `commonTest/resources/key-blob/`.

### T693 [P] — `KeyBlobRoundtripTest`

**Trace**: FR-027; contracts/key-blob-v1.md "Roundtrip test contract".
**What**: `commonTest/wireformat/KeyBlobRoundtripTest.kt`:
- Create KeyBlob → serialize → parse → assertEquals.
- ByteArray content comparison.
**Acceptance**: Test green.
**Dependencies**: requires T623.
**Files**: `commonTest/wireformat/KeyBlobRoundtripTest.kt`.

### T694 [P] — `KeyBlobBackwardCompatReadTest`

**Trace**: FR-026; contracts/key-blob-v1.md "Backward-compat read test contract"; Сценарий 11.
**What**: `commonTest/wireformat/KeyBlobBackwardCompatReadTest.kt`:
- Load `v1-sample.json` → parse via current code → assert schemaVersion=1, fields correct.
- Test unknown schemaVersion=999 → throws `UnsupportedSchemaVersion`.
**Acceptance**: Both tests green.
**Dependencies**: requires T623, T692, T625.
**Files**: `commonTest/wireformat/KeyBlobBackwardCompatReadTest.kt`.

### T695 [P] — `KeyBlobCrossPlatformParityTest`

**Trace**: FR-022, FR-027; contracts/key-blob-v1.md.
**What**: `commonTest/wireformat/KeyBlobCrossPlatformParityTest.kt`:
- Create KeyBlob with fixed values → encode → assert bytes match hardcoded expected hex.
- Runs on both `jvmTest` + `androidUnitTest`.
**Acceptance**: Identical bytes on both targets.
**Dependencies**: requires T623.
**Files**: `commonTest/wireformat/KeyBlobCrossPlatformParityTest.kt`.

---

## Phase A — `docs/dev/crypto-review.md` document

### T6A0 — Create `docs/dev/crypto-review.md`

**Trace**: FR-023, FR-024; Сценарий 9; SC-010.
**What**: Create `docs/dev/crypto-review.md` with sections:
- **Primitives**: XChaCha20-Poly1305 + X25519 + Ed25519 + HKDF-SHA256.
- **Industrial reference baseline**: links / mentions Signal, WhatsApp (Signal Protocol), WireGuard, age, Threema, Bitwarden Send.
- **Validation set**:
  - RFC KAT list (RFC 7748, 8032, 8439, 5869 + XChaCha20 IETF draft) with CI link.
  - Google Wycheproof subset (pinned commit SHA from T658).
  - Property-based tests (Kotest, 1000 iterations) description.
- **Explicit text**: "Friend crypto review снят как mandatory (решение 2026-06-17, F-CRYPTO mentor session). Заменён на measurable validation set выше."
- **Paid audit milestone**: cross-ref to [SRV-CRYPTO-003](../dev/server-roadmap.md#srv-crypto-003-paid-security-audit-milestone-f-crypto-billing-gate) — milestone перед запуском billing.
**Acceptance**: File exists, all sections present, `grep "SRV-CRYPTO-003" docs/dev/crypto-review.md` matches, `grep "Friend crypto review снят" docs/dev/crypto-review.md` matches.
**Dependencies**: requires T658 (commit SHA).
**Files**: `docs/dev/crypto-review.md`.

---

## Phase B — Android Keystore instrumentation tests

### T6B0 — `SecureKeyStorePersistenceTest`

**Trace**: SC-008; Сценарий 3.
**What**: `androidInstrumentedTest/SecureKeyStorePersistenceTest.kt`:
- store(keyId, secret) → load(keyId) → assert equal.
- Restart process simulation (via ApplicationProvider clear+re-init) → load returns same secret.
**Acceptance**: Test passes on emulator `pixel_5_api_34`.
**Dependencies**: requires T660.
**Files**: `androidInstrumentedTest/SecureKeyStorePersistenceTest.kt`.

### T6B1 [P] — `SecureKeyStoreNoPlaintextLeakTest`

**Trace**: SC-009; Сценарий 4.
**What**: Store known secret → read blob file bytes → scan for any 4-byte subsequence of original secret → assert NOT found.
**Acceptance**: No leak.
**Dependencies**: requires T660.
**Files**: `androidInstrumentedTest/SecureKeyStoreNoPlaintextLeakTest.kt`.

### T6B2 [P] — `SecureKeyStoreInvalidPrefixTest`

**Trace**: FR-010 edge case.
**What**: Attempt `KeyId("photo-album-v1")` (no `media-` prefix) → `IllegalArgumentException` at construction time.
**Acceptance**: Throws.
**Dependencies**: requires T621, T660.
**Files**: `androidInstrumentedTest/SecureKeyStoreInvalidPrefixTest.kt`.

### T6B3 [P] — `SecureKeyStoreUninstallScenarioTest`

**Trace**: Сценарий 5; Edge Case "App factory reset".
**What**: Simulate `clear data` via runtime clear of `files/keys/` AND removal of Keystore alias → load returns null (or throws KeystoreInvalidated, document choice).
**Acceptance**: Behavior matches Сценарий 5 step 6.
**Dependencies**: requires T660.
**Files**: `androidInstrumentedTest/SecureKeyStoreUninstallScenarioTest.kt`.

### T6B4 [P] — `TeeAttestationCheckTest` (warning, not hard fail in MVP)

**Trace**: permissions-platform O-2 (TODO FR-031 — TEE attestation).
**What**: On `SecureKeyStore` init, check `KeyInfo.isInsideSecureHardware`. If `false`, log warning (not crash). Test verifies attestation is read successfully on emulator.
**Acceptance**: Test reports attestation status. On emulator likely software-backed (warn). Real device — hardware-backed.
**Dependencies**: requires T660.
**Files**: `androidInstrumentedTest/TeeAttestationCheckTest.kt`.

---

## Phase C — Backup rules + manifest

### T6C0 — Update `app/src/main/res/xml/data_extraction_rules.xml`

**Trace**: security checklist O-2 (medium); research.md §R9.
**What**: Add `<exclude domain="file" path="keys/" />` в `<cloud-backup>` and `<device-transfer>` sections. Если file doesn't exist — create new with proper schema.
**Acceptance**: File contains exclude rules. Verify `AndroidManifest.xml` references `android:dataExtractionRules="@xml/data_extraction_rules"`.
**Dependencies**: none (works alongside crypto module).
**Files**: `app/src/main/res/xml/data_extraction_rules.xml` + possibly `AndroidManifest.xml`.

### T6C1 [P] — Verify legacy `backup_rules.xml` consistent

**Trace**: same as T6C0, для Android ≤ 11.
**What**: Update `app/src/main/res/xml/backup_rules.xml` (if exists) to exclude `files/keys/` for `<full-backup-content>`.
**Acceptance**: Both backup rule files exclude keys.
**Dependencies**: none.
**Files**: `app/src/main/res/xml/backup_rules.xml`.

---

## Phase D — Documentation updates

### T6D0 [P] — Update `docs/product/roadmap.md` F-3 done marker

**Trace**: roadmap currency.
**What**: Verify F-CRYPTO step status reflects current state. Update «Шаг 2: F-CRYPTO ... 🚧 InProgress» as needed.
**Acceptance**: Roadmap correct.
**Dependencies**: none.
**Files**: `docs/product/roadmap.md`.

### T6D1 [P] — Add `KeyBlob` term to `docs/product/glossary.md`

**Trace**: plan.md Required Context Review.
**What**: Add glossary entry для `KeyBlob` (1-2 lines describing format + cross-ref to contracts/key-blob-v1.md).
**Acceptance**: Entry exists.
**Dependencies**: requires T623.
**Files**: `docs/product/glossary.md`.

### T6D2 [P] — Update CHANGELOG (если есть)

**Trace**: routine.
**What**: Add entry for F-CRYPTO 1.0.0 (если CHANGELOG.md существует).
**Acceptance**: Entry added.
**Dependencies**: none.
**Files**: `CHANGELOG.md` (если есть).

---

## Phase E — Fitness functions activation

### T6E0 — Wire `verifyCryptoIsolation` into CI

**Trace**: SC-006; plan.md §"Test strategy" → "Fitness functions".
**What**: Add CI job calling `./gradlew :core:crypto:verifyCryptoIsolation`. Failure = red CI.
**Acceptance**: CI runs task on every PR.
**Dependencies**: requires T613.
**Files**: `.github/workflows/ci.yml` (или эквивалент CI config).

### T6E1 [P] — Wire Detekt into CI

**Trace**: SC-011.
**What**: Add CI job: `./gradlew detekt`.
**Acceptance**: CI green when no fake* imports in main; red on violation.
**Dependencies**: requires T682.
**Files**: `.github/workflows/ci.yml`.

### T6E2 [P] — Wire KMP tests into CI

**Trace**: SC-002, SC-003, SC-004.
**What**: CI jobs:
- `crypto-jvm-tests`: `./gradlew :core:crypto:jvmTest -PkotestPropertyIterations=1000`.
- `crypto-android-instrumentation`: `connectedDebugAndroidTest` on macOS runner with emulator.
**Acceptance**: Both jobs configured.
**Dependencies**: requires T650-T657, T660, T6B0-T6B4.
**Files**: `.github/workflows/ci.yml`.

---

## Phase F — Perf checkpoint

### T6F0 — Measure perf vs plan targets

**Trace**: plan.md §"Performance Goals" + "Perf checkpoint targets".
**What**: Run benchmarks on emulator `pixel_5_api_34`:
- AEAD encrypt/decrypt 1 KB (target < 5ms).
- X25519 keypair generate (< 50ms), ECDH derive (< 20ms).
- Ed25519 sign/verify (< 30ms each).
- HKDF derive 32 bytes (< 10ms).
- `SecureKeyStore.store` round-trip (< 100ms — real device only).
- `SecureKeyStore.load` (< 50ms).
Record results in `specs/016-f-crypto-core-module/perf-checkpoint.md`.
**Acceptance**: File created with measurements. If any > 2× target — investigate (optimize или revise plan).
**Dependencies**: requires all of Phase 5 + 6.
**Files**: `specs/016-f-crypto-core-module/perf-checkpoint.md`.

---

## Phase G — Acceptance verification trace

### T6G0 — Walk through `scenarios.md` 11 sценарии

**Trace**: scenarios.md acceptance trace.
**What**: For each of 11 scenarios in `scenarios.md`, verify checkboxes. Some automated, some manual. Recommended order per quickstart.md "Acceptance verification trace":
1. Сценарий 3 (первый запуск + identity ключ).
2. Сценарий 4 (ключи защищены).
3. Сценарии 1+2 (cross-platform encrypt/decrypt).
4. Сценарий 9 (`crypto-review.md` exists).
5. Сценарий 6 (libsodium swap dry-run — experimental branch, no merge).
6. Сценарий 7 (library extract dry-run — experimental, no merge).
7. Сценарий 11 (backward-compat read).
8. Сценарий 5 (clear data на эмуляторе).
9. Сценарии 8, 10 — verify primitives есть (deferred to spec 017 для full flow).
**Acceptance**: All checkboxes marked в scenarios.md.
**Dependencies**: requires all previous phases.
**Files**: `scenarios.md` (mark checkboxes).

---

## Phase H — Constitution Re-check + final analyze

### T6H0 — Re-run `procedure-constitution-check` after implementation

**Trace**: Article XVI; plan.md.
**What**: After all phases complete, re-run Constitution Check on final state (code + docs). Confirm gates still PASS.
**Acceptance**: 6 PASS, 2 N/A, 0 FAIL (same as plan-phase check).
**Dependencies**: requires all phases done.
**Files**: append re-check report to plan.md.

### T6H1 — Run `/speckit.analyze` before merge

**Trace**: Article XVI.
**What**: Run analyze skill для final cross-artifact + dangling-reference scan + checklist re-run.
**Acceptance**: All open issues closed or explicitly accepted.
**Dependencies**: requires T6H0.
**Files**: `specs/016-f-crypto-core-module/analyze-report.md`.

### T6H2 [P] — Manual review: verify FR-029 (no business logic в core/crypto/api/)

**Trace**: FR-029; cross-artifact-trace gap.
**What**: Manual review (grep + visual inspection) `core/crypto/src/commonMain/kotlin/family/crypto/api/` directory tree. Verify no consumer-specific business logic creeped in:
- No `ConfigCipher` class (это потребитель F-5).
- No `EnvelopeBuilder` для shared content (это потребитель спека 011).
- No media blob protocol logic.
- No Sender Keys / MLS messenger protocol.
**Acceptance**: Reviewer signs off в analyze-report.md "FR-029 manually verified — api/ contains only crypto primitives and value types, no business logic".
**Dependencies**: requires all of Phase 3.

---

## Phase I — Cleanup

### T6I0 — Delete stale TODOs

**Trace**: routine.
**What**: Search for `TODO(spec-016)` или `TODO(F-CRYPTO-temp)` markers in code, address or remove.
**Acceptance**: Grep empty.
**Dependencies**: requires all phases.
**Files**: various.

### T6I1 [P] — Commit + push final state

**Trace**: Article XVIII.
**What**: Final commit batch, push to remote, ensure draft PR updated.
**Acceptance**: Branch reflects merged-ready state.
**Dependencies**: requires T6H1.

### T6I2 — Convert draft PR to ready-for-review

**Trace**: Article XVIII.
**What**: After all checkpoints, convert PR `j_f_crypto_core_module_17_06_26` from draft to ready, request review.
**Acceptance**: PR ready.
**Dependencies**: requires T6I1.

---

## Summary

**Total**: 47 tasks across 10 phases (Phase 0..I).

**Phase breakdown**:
- Phase 0 (Research): 2 tasks
- Phase 1 (Scaffolding): 4 tasks
- Phase 2 (Domain types): 6 tasks (5 `[P]`)
- Phase 3 (Ports): 7 tasks (6 `[P]`)
- Phase 4 (Fakes + property): 9 tasks (8 `[P]` after first)
- Phase 5 (Real adapters + KAT): 11 tasks (lots of `[P]` after sequential setup)
- Phase 6 (SecureKeyStore Android): 2 tasks
- Phase 7 (iOS stub): 2 tasks
- Phase 8 (DI + Detekt + R8): 4 tasks
- Phase 9 (Cross-platform + fixtures): 6 tasks
- Phase A (docs/dev/crypto-review.md): 1 task
- Phase B (Instrumentation tests): 5 tasks
- Phase C (Backup rules): 2 tasks
- Phase D (Doc updates): 3 tasks
- Phase E (Fitness functions CI): 3 tasks
- Phase F (Perf checkpoint): 1 task
- Phase G (Acceptance trace): 1 task
- Phase H (Constitution re-check + analyze): 2 tasks
- Phase I (Cleanup): 3 tasks

(Count adjusted — actual: ~70 tasks. Marked with letters for clarity.)

**Critical path**: T601 → T611 → T620+T621+T625 → T630+T631+T632+T633+T634 → T650+T651 → T660 → T680 → T6A0 → T6F0 → T6G0 → T6H1.

**Parallel-safe groups**: most `[P]` tasks within same phase can run concurrently on independent files.

**Coverage** (cross-artifact-trace):
- **30 FRs covered**: FR-001..030 — at least one task each.
- **6 USs covered**: each user story has acceptance trace through Phase G.
- **12 SCs covered**: each SC has measurement / verification task.
- **9 Edge Cases covered**: via tests in Phases 4-6 + instrumentation в Phase B.

**Open issues** (для tracking):
- T658 commit SHA для Wycheproof должен быть chosen во время реализации.
- T6F0 perf measurements — если > 2× target, escalate.
- T6B4 TEE attestation — на эмуляторе software-backed; real device verification только manual.

---

## TL;DR простым языком

Это **список конкретных задач для реализации** F-CRYPTO. Около 70 задач, сгруппированных в 18 этапов:

1. **Этап 0** — проверить, жива ли библиотека libsodium (если нет — план Б).
2. **Этап 1** — создать новую папку `core/crypto/` с правильной структурой подпапок.
3. **Этап 2** — написать «типы данных» (имена ключей, классы ключей, форматы файлов).
4. **Этап 3** — написать интерфейсы (что криптография умеет делать).
5. **Этап 4** — написать «заглушки» (фейковые реализации для тестов) + написать тесты, которые проверяют свойства алгоритмов.
6. **Этап 5** — написать настоящие реализации поверх libsodium + загрузить тестовые векторы от RFC и Google для проверки.
7. **Этап 6** — Android-специфика: «сейф для ключей» через защищённый чип TEE.
8. **Этап 7** — iOS-заглушка, которая громко кричит «не готово» если её попробуют использовать.
9. **Этап 8** — подключить криптографию к приложению + защита от опасных ошибок (Detekt-правило + R8).
10. **Этап 9** — проверка совместимости между Android и JVM (одинаковые байты) + проверка совместимости форматов файлов между версиями приложения.
11. **Этап A** — написать документ `crypto-review.md` для аудиторов.
12. **Этап B** — тесты на Android-эмуляторе для «сейфа ключей».
13. **Этап C** — настроить Android backup так, чтобы он не отправлял ключи в Google Drive.
14. **Этап D** — обновить документацию проекта (roadmap, глоссарий).
15. **Этап E** — активировать автоматические проверки в CI.
16. **Этап F** — измерить производительность.
17. **Этап G** — пройти все 11 сценариев из `scenarios.md` и поставить галочки.
18. **Этапы H + I** — финальная проверка конституции + cleanup + merge.

**Многие задачи помечены `[P]`** — это значит, что их можно делать параллельно (если есть несколько разработчиков), они не конфликтуют по файлам.

**Критический путь** (что делать строго по порядку) — около 12 задач: настройка модуля → типы данных → ключевые интерфейсы → libsodium реализации → сейф ключей → DI → документация → проверки.

**После завершения всех задач** F-CRYPTO готов к merge'у, и можно начинать F-5 (ConfigDocument E2E) и другие потребители.
