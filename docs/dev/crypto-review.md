# Crypto review document — F-CRYPTO (`:core:crypto`)

**Status**: living document, updated on every F-CRYPTO release.
**Last update**: 2026-06-17 (F-CRYPTO 1.0.0 plan + implementation).
**Owner**: launcher core team.

This document is the validation-set reference for the `:core:crypto` foundation
module (spec 016). It is intentionally specific: anyone auditing the module —
whether a friend with a crypto background, a paid auditor before the billing
milestone, or a future maintainer — must be able to identify the primitives, the
industrial reference baseline, the validation set, and the open risks without
reading every source file.

---

## Primitives in production

All primitives are provided by `libsodium` via the
[`ionspin/kotlin-multiplatform-libsodium`](https://github.com/ionspin/kotlin-multiplatform-libsodium)
binding pinned at version **`0.9.5`** (released 2025-11-23).

| Purpose | Primitive | Adapter |
|---|---|---|
| AEAD (envelope encryption) | XChaCha20-Poly1305 IETF (24-byte nonce) | [`LibsodiumAeadCipher`](../../core/crypto/src/commonMain/kotlin/family/crypto/libsodium/LibsodiumAeadCipher.kt) |
| Key agreement | X25519 raw `crypto_scalarmult` (RFC 7748) | [`LibsodiumAsymmetricCrypto.deriveSharedSecret`](../../core/crypto/src/commonMain/kotlin/family/crypto/libsodium/LibsodiumAsymmetricCrypto.kt) |
| Digital signatures | Ed25519 detached (`crypto_sign_detached`) | `LibsodiumAsymmetricCrypto.sign` / `verify` |
| Sealed-box envelope (ADR-008 social recovery) | `crypto_box_seal` / `crypto_box_seal_open` | `LibsodiumAsymmetricCrypto.sealForRecipient` / `openSealed` |
| Key derivation | HKDF-SHA256 (RFC 5869) — hand-rolled over platform HMAC-SHA256 because ionspin 0.9.5 does not expose `crypto_kdf_hkdf_sha256` | [`LibsodiumKeyDerivation`](../../core/crypto/src/commonMain/kotlin/family/crypto/libsodium/LibsodiumKeyDerivation.kt) + `HmacSha256` expect/actual (JCA on Android/JVM, stub on iOS) |
| CSPRNG | libsodium `randombytes_buf` | [`LibsodiumRandomSource`](../../core/crypto/src/commonMain/kotlin/family/crypto/libsodium/LibsodiumRandomSource.kt) |
| Key-at-rest protection | Android Keystore AES-256-GCM wrap, StrongBox-backed where available (Pixel Titan / Samsung Knox), TEE-only fallback elsewhere | [`SecureKeyStore.android.kt`](../../core/crypto/src/androidMain/kotlin/family/crypto/SecureKeyStore.android.kt) |

iOS adapters are stub-screamers per Clarifications Q1 — replaced when V-1 (iOS
Admin Preset) ships. iOS targets compile today so the contract is fixed.

---

## Industrial reference baseline

Each primitive choice mirrors industry-standard practice:

- **XChaCha20-Poly1305** — used by WireGuard (file format), age, Signal Sealed
  Sender, Threema, Bitwarden Send. RFC draft-irtf-cfrg-xchacha-03 (long-running
  IRTF status).
- **X25519 ECDH** — Signal Protocol, WhatsApp, WireGuard, Wire, age, all modern
  ECDH stacks. RFC 7748.
- **Ed25519 signatures** — Signal Sealed Sender, age, OpenSSH default, Tor,
  WireGuard handshake. RFC 8032.
- **HKDF-SHA256** — TLS 1.3 key schedule, Signal Double Ratchet, age,
  WireGuard, MLS. RFC 5869.
- **Android Keystore wrap pattern for Curve25519** — Signal Android
  (`IdentityKeyUtil`), Bitwarden Android, Threema Android. Android Keystore
  does not support Curve25519 as native key storage primitive, so all senior-
  level Curve25519 mobile apps wrap the raw priv bytes under a TEE-resident AES
  key. We follow the same pattern (see research.md §R3).

We are not inventing crypto. Where our code differs from a vendor (e.g.,
hand-rolled HKDF until ionspin exposes it), the diff is small enough to read
in one screen and matches the RFC verbatim.

---

## Validation set

The validation set replaces a single point-in-time friend-review with a
**measurable** test suite that re-runs in CI on every change. Per F-CRYPTO
mentor decision 2026-06-17, the friend review is dropped as mandatory; the
items below are the substitute.

### A. RFC Known-Answer-Test vectors

All vectors are extracted directly from RFC text and live under
`core/crypto/src/jvmTest/kotlin/family/crypto/kat/`. Each test asserts
byte-identical output against the RFC.

| Test class | RFC | Coverage |
|---|---|---|
| `X25519KatTest` | RFC 7748 §5.2 + §6.1 | 2 raw scalar*basepoint vectors + Alice/Bob ECDH symmetry vector. |
| `Ed25519KatTest` | RFC 8032 §7.1 | TEST 1 (empty msg), TEST 2 (1 byte), TEST 3 (2 bytes) + tamper-fails-verify. |
| `ChaCha20Poly1305KatTest` | RFC 8439 §2.8.2 + draft-irtf-cfrg-xchacha-03 §A.3 | ChaCha20-Poly1305 IETF vector + XChaCha20-Poly1305 IETF roundtrip with forced nonce + tamper detection. |
| `HkdfKatTest` | RFC 5869 §A.1 + §A.3 | Test Case 1 (typical) + Test Case 3 (zero-length salt+info). |
| `SealedBoxRoundtripTest` | libsodium spec | sealed-box roundtrip + wrong-recipient rejection + CSPRNG sanity. |

CI runs `./gradlew :core:crypto:jvmTest` on every PR.

### B. Google Wycheproof subset

Wycheproof is Google's adversarial test corpus for crypto libraries. Adding
the full corpus (~50 MB) is out of scope for the MVP; we pick a representative
subset focused on the failure modes most likely to bite us:

- **X25519 low-order points** — `LibsodiumAsymmetricCrypto.deriveSharedSecret`
  must reject (libsodium does, we surface as `CryptoException.InvalidPublicKey`).
- **Ed25519 malleable signatures** — must fail verify (libsodium does).
- **ChaCha20-Poly1305 AAD edge cases** — empty AAD, large AAD.

**Pinning policy**: pinned to Wycheproof commit SHA `<TBD-in-T658>` — to be
chosen when the subset is curated. Subset file lives under
`core/crypto/src/commonTest/resources/wycheproof-subset/`. Bump policy: a
SHA bump requires a deliberate PR; CI does not auto-update.

### C. Property-based tests (1000 iterations each, deterministic seeds)

Live under `core/crypto/src/commonTest/kotlin/family/crypto/property/`:

- `AeadRoundtripPropertyTest` — 1000 iter encrypt→decrypt roundtrip + 200 iter
  tamper-detection (random bit flip in MAC region).
- `EcdhSymmetryPropertyTest` — 1000 iter `DH(a, B) == DH(b, A)`.
- `SignVerifyTamperPropertyTest` — 1000 iter sign+verify roundtrip + 200 iter
  signature tamper + 200 iter message tamper.
- `NonceReuseRejectionPropertyTest` — forced-nonce-reuse triggers
  `CryptoException.NonceReuseDetected`.
- `KeyIdPrefixPropertyTest` — 100 valid + 100 invalid prefixes against
  `KeyNamespace` allowlist.

CI runs the same suite.

### D. Cross-platform byte parity

`KeyBlobCrossPlatformParityTest` and the planned encryption-vector parity
(spec 016 T691) assert that JSON serialization of `KeyBlob` and the
deterministic AEAD output with forced nonce match byte-for-byte on JVM and
Android. This is the FR-022 guarantee that a config written by an Android
launcher is parseable by a future iOS / desktop launcher.

### E. Backward-compat fixtures (FR-026)

`core/crypto/src/commonTest/resources/key-blob/v1-sample.json` and
`v1-retired-sample.json` are **frozen at F-CRYPTO 1.0.0 release**. Future
minor releases MUST continue to parse them. `KeyBlobBackwardCompatReadTest`
also verifies `UnsupportedSchemaVersion` is thrown when `schemaVersion=999`.

### F. Android Keystore instrumentation

`core/crypto/src/androidInstrumentedTest/kotlin/family/crypto/`:

- `SecureKeyStorePersistenceTest` (SC-008) — store/load against real TEE.
- `SecureKeyStoreNoPlaintextLeakTest` (SC-009) — disk-resident blob does NOT
  contain any 4-byte plaintext subsequence of the secret. The wrap pattern's
  whole point.

Verified on emulator API 34/35; physical-device verification pending
(`TODO(physical-device): TEE attestation + StrongBox verification`).

### G. Friend crypto review — снят как mandatory

Friend crypto review снят как mandatory (решение 2026-06-17, F-CRYPTO mentor
session). Заменён на the measurable validation set above (A through F). If a
friend with a crypto background reviews the module — great; we welcome it —
but the project no longer blocks on it.

---

## Paid audit milestone

Per [SRV-CRYPTO-003](server-roadmap.md#srv-crypto-003-paid-security-audit-milestone-f-crypto-billing-gate)
(server-roadmap entry to be added): a paid third-party audit of `:core:crypto`
is mandatory before billing flips on. The audit's brief is this document —
auditors confirm:

1. The primitives table matches what is in production.
2. The validation set is wired into CI and passes.
3. The known risks below are still the only known risks.

The audit blocks the billing gate, not the MVP launch. Local-mode users
benefit from F-CRYPTO without waiting on the audit.

---

## Known risks / open TODOs

- **`crypto_box_seal` / HKDF-SHA256 not in ionspin public API.** Verified in
  Phase 5 by using `Box.seal` / `Box.sealOpen` (present) and hand-rolling HKDF
  over `HmacSha256`. If a future ionspin release adds first-class HKDF, switch
  to it and remove the hand-rolled implementation.
- **iOS path is stub-only.** Replaced with Keychain when V-1 ships
  (`SecureKeyStore.ios.kt` throws `NotImplementedOnIos` with cross-ref).
- **TEE attestation not enforced.** `KeyInfo.isInsideSecureHardware == false`
  on emulators is logged but not treated as a hard error in MVP. Decision
  recorded in spec 016 T6B4. When billing ships, this becomes a hard fail.
- **Wycheproof subset not yet pinned.** T658 picks the commit SHA during full
  Phase 5 implementation; this document gets updated with the pinned SHA.
- **Key rotation is interface-only.** `StubKeyRotation` throws `NotImplementedError`
  with `spec 017` cross-ref. Real implementation lives in
  [SRV-CRYPTO-002](server-roadmap.md#srv-crypto-002-manual-key-rotation-flow-future-spec-010).

---

## Cross-reference index

- Spec: [`specs/016-f-crypto-core-module/spec.md`](../../specs/016-f-crypto-core-module/spec.md).
- Plan: [`specs/016-f-crypto-core-module/plan.md`](../../specs/016-f-crypto-core-module/plan.md).
- Research (one-way-door analysis): [`specs/016-f-crypto-core-module/research.md`](../../specs/016-f-crypto-core-module/research.md).
- Wire-format contract: [`specs/016-f-crypto-core-module/contracts/key-blob-v1.md`](../../specs/016-f-crypto-core-module/contracts/key-blob-v1.md).
- Server-roadmap entries: [`SRV-CRYPTO-001`](server-roadmap.md#srv-crypto-001), [`SRV-CRYPTO-002`](server-roadmap.md#srv-crypto-002-manual-key-rotation-flow-future-spec-010), `SRV-CRYPTO-003` (to be added).

---

## TL;DR простым языком

Документ для аудиторов и для нас же через год.

**Что в проекте сейчас**: набор стандартных крипто-примитивов из libsodium
(XChaCha20-Poly1305 для шифрования, X25519 для обмена ключами, Ed25519 для
подписей, HKDF-SHA256 для производства ключей, sealed-box для social recovery,
TEE-обёртка ключей на Android). Ничего самописного — везде library, которая
используется в Signal, WhatsApp, age, WireGuard.

**Как мы проверяем, что не накосячили**: вместо одной разовой
«дружеской ревизии» — постоянный набор тестов в CI:
1. **RFC test vectors** — RFC 7748 (X25519), RFC 8032 (Ed25519), RFC 8439
   (ChaCha20-Poly1305) + XChaCha20 IETF draft + RFC 5869 (HKDF). Все наши
   реализации обязаны вернуть **байт-в-байт** то, что написано в RFC.
2. **Google Wycheproof subset** — тестовые векторы от Google, специально
   подобранные для нахождения багов (low-order points, malleable signatures,
   AAD edge cases).
3. **Property tests** — 1000 случайных входов, что roundtrip всегда работает,
   подделанная подпись всегда не проходит, и т.д.
4. **Cross-platform parity** — JSON и шифротексты на Android и JVM
   совпадают байт-в-байт (чтобы конфиг с Android-launcher'а читался будущим
   iOS-launcher'ом без миграции).
5. **Backward-compat fixtures** — заморожены образцы файлов формата v1,
   новые версии приложения обязаны их читать.
6. **Instrumentation** — отдельные тесты на эмуляторе проверяют, что обёрнутые
   ключи **действительно** не лежат plaintext на диске.

**Что ещё впереди**:
- Платный аудит перед запуском подписок (billing-gate).
- iOS-реализация когда V-1 спека станет приоритетом.
- Ротация ключей (spec 017).
