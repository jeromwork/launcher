# Checklist: domain-isolation — TASK-51 libsodium consolidation

Applied to: `specs/task-51-libsodium-consolidation/spec.md`
Date: 2026-06-26
Source skill: `.claude/skills/checklist-domain-isolation/SKILL.md`
References: CLAUDE.md rules 1 (Domain isolated from infrastructure) + 2 (Anti-Corruption Layer), ADR-001 (Platform Parity Gate), ADR-005 §3 Gate 1.

---

## External surfaces / ports introduced or touched by this spec

- **Crypto primitives port family** (`cryptokit.crypto.api.*`): `AeadCipher`, `AsymmetricCrypto`, `KeyDerivation`, `SecureKeyStore`, `RandomSource` — already exist in `:core:crypto` (spec 016). TASK-51 renames namespace `family.*` → `cryptokit.*` and consolidates as single source of truth.
- **Pairing wire-format API** (`cryptokit.pairing.api.*`): 15 spec 011 wire-format types (`DeviceIdentity`, `DeviceId`, `EncryptedEnvelope`, `Recipient`, `DeviceIdentityRepository`, `EncryptedMediaStorage`, `RecipientResolver`, ...) migrate from `com.launcher.api.crypto` into a new package in the same `:core:crypto` module.
- **libsodium adapter** (`cryptokit.crypto.libsodium.*`): ionspin-based implementation in `commonMain`.
- **Android Keystore adapter**: `SecureKeyStore` `expect/actual` (Android-only) — already exists; lazysodium-coupled `AndroidKeystoreSecureKeystore` (300+ lines) is deleted (FR-010).
- **Removed adapters**: `lazysodium-android` (com.goterl), `net.java.dev.jna:jna`, `Libsodium*.kt`, `LibsodiumProvider`, `AndroidKeystoreSecureKeystore`.
- **Inline SHA-256**: `MessageDigest.getInstance("SHA-256")` used inline in `Spec011SmokeDebugActivity` (debug-only) — no new `HashFunction` port (FR-014).

---

## Vendor SDKs

- [x] CHK001 No vendor SDK type (lazysodium `SodiumAndroid`, `com.goterl.*`, JNA `JNA.register`, ionspin internal libsodium types, Coil, Firebase, etc.) appears in any signature visible to the domain layer. FR-002, FR-006, FR-007 explicitly forbid `com.goterl.*`, `lazysodium`, `JNA.register`, `SodiumAndroid` imports in production code; SC-003/SC-004 verify via grep. ionspin types are wrapped inside `cryptokit.crypto.libsodium.*` adapter — domain depends only on `cryptokit.crypto.api.*`.
- [x] CHK002 Each external SDK has exactly one wrapper module: ionspin libsodium-kmp is wrapped by `cryptokit.crypto.libsodium.*` in `:core:crypto`; domain references **only** the `cryptokit.crypto.api.*` port interfaces. FR-006 names the port-only contract; FR-008 rewrites `PairingCryptoCoordinator` against the ports.
- [x] CHK003 "Vendor disappears tomorrow" test: per FR-006, if ionspin disappeared, change is limited to `cryptokit.crypto.libsodium.*` adapter package (the wrapper module). Spec § Assumptions explicitly notes ionspin coverage and MLS protocol parking-lot as separate; consumers stay on `cryptokit.crypto.api.*`.

## Transport types

- [x] CHK004 No HTTP-client / retrofit-annotation / raw-JSON-container types appear in domain signatures. Spec touches no transport — only on-device crypto + Keystore. Firestore publication of `DeviceIdentity` (pairing) goes through repository ports; the wire format itself is a domain-owned data class.
- [x] CHK005 Domain-owned wire format: `DeviceIdentity`, `EncryptedEnvelope`, `Recipient`, etc. are domain data classes in `cryptokit.pairing.api.*` (per FR-006). They carry `schemaVersion: 1` (Assumption + FR-004). Serializers live in adapters; not generated DTOs posing as domain models.

## Platform types

- [x] CHK006 No `android.*`/`androidx.*`/`Intent`/`Uri`/`Context`/`Bundle`/`LifecycleOwner` appears in `commonMain`. USv3 + Acceptance Scenario 2 explicitly verifies no Android-specific imports in commonMain crypto stack. The only Android-coupled piece is `SecureKeyStore` actual in `androidMain`.
- [x] CHK007 Platform-derived data is projected: Android Keystore is reached via `SecureKeyStore` `expect/actual` exposing `ByteArray` and `KeyId: String` only — no `KeyStore`/`KeyGenParameterSpec` leaks into common. `MessageDigest` is confined to a debug Activity (`Spec011SmokeDebugActivity`), not in domain values.

## Ports

- [x] CHK008 Every external surface is exposed via a port: libsodium primitives → `cryptokit.crypto.api.AeadCipher` / `AsymmetricCrypto` / `KeyDerivation` / `RandomSource`; Keystore → `SecureKeyStore`; pairing wire-format I/O → `DeviceIdentityRepository`, `EncryptedMediaStorage`, `RecipientResolver` in `cryptokit.pairing.api.*`.
- [x] CHK009 Port shapes are domain-driven, not adapter-driven. Methods like `generateX25519KeyPair`, `generateEd25519KeyPair`, `store(keyId, bytes)`, `load(keyId): ByteArray?` (FR-008, FR-010) are domain verbs — none expose `KeyGenParameterSpec`, `SecretKeyEntry`, or other Android-Keystore-specific shape.
- [x] CHK010 Fake adapters exist per port (spec § Local Test Path): `FakeAeadCipher` (XOR stub), `FakeAsymmetricCrypto` (seeded), `FakeRandomSource`, `FakeSecureKeyStore` (in-memory HashMap), plus pairing fakes (`FakeDeviceIdentityRepository`, `FakeEncryptedMediaStorage`). § Clarifications resolution log confirms new fakes go to `core/crypto/src/commonTest/.../cryptokit/crypto/fake/` and `cryptokit/pairing/fake/`.
- [x] CHK011 Real adapters present: libsodium adapter in `commonMain` (`cryptokit.crypto.libsodium.*`), Android Keystore `actual` in `androidMain`. iOS path is structurally ready (USv3) — iOS Keychain `actual` to be added when TASK-26 lands (not in scope here).
- [x] CHK012 DI wiring picks fake/real per build: FR-015 consolidates bindings into one `cryptokitModule` Koin module; existing build flavors (`mockBackend` vs prod) already swap fakes/real per spec 016 pattern.

## Source-set placement

- [x] CHK013 Source-set placement justified per file class. FR-016 enumerates placements explicitly: API (`commonMain/.../cryptokit/crypto/api/`), libsodium impl (commonMain), JVM tests (`jvmTest/.../cryptokit/crypto/kat/`), exception hierarchy (commonMain), stubs (commonMain/commonTest), Android Keystore actual (androidMain). USv3 Acceptance Scenario 1 makes this an explicit verification gate.
- [x] CHK014 Default placement is `commonMain`; deviations are reasoned. Only `androidMain` deviation is `SecureKeyStore.actual` (uses `android.security.keystore` API) and the debug `Spec011SmokeDebugActivity` (uses `java.security.MessageDigest` in an Android Activity — debug-only). All other crypto + pairing files target `commonMain`.

## Existing-code regressions

- [x] CHK015 Spec does not reintroduce any vendor type into a `commonMain` file previously cleansed. On the contrary, FR-007 adds Konsist fitness tests that forbid re-introduction of `com.goterl.*`, `net.java.dev.jna.*`, `JNA.register`, `com.launcher.api.crypto.*`, `family.crypto.*`. SC-007 verifies the fitness tests are green.
- [x] CHK016 No new `expect`/`actual` introduced where pure Kotlin would suffice. Only existing `SecureKeyStore` expect/actual is reused (FR-010). Crypto primitives stay pure-Kotlin via ionspin's commonMain API. The decision to use inline `MessageDigest` (FR-014) instead of a new `HashFunction` port explicitly avoids premature abstraction (CLAUDE.md rule 4).

---

## Verdict

**PASS** — 16/16 CHK [x].

All gates green:
- Domain isolation strictly maintained: vendor types confined to one adapter package; ports drive shapes; commonMain free of platform APIs.
- ACL boundary explicit: ionspin (single SDK) wrapped in `cryptokit.crypto.libsodium.*`; Android Keystore wrapped behind `SecureKeyStore` expect/actual.
- Wire format types (`DeviceIdentity`, `EncryptedEnvelope`, …) are domain-owned with explicit `schemaVersion`, not transport DTOs.
- Fakes + real adapters present per port; DI consolidates to one module without violating isolation.
- Fitness tests (FR-007 / SC-007) make regressions detectable mechanically per CLAUDE.md rule 7.

The spec actually **strengthens** domain isolation relative to the pre-TASK-51 baseline: removes the lazysodium/JNA leak (UnsatisfiedLinkError vector), deletes orphan port types (`PrivateKey`, `SigningPrivateKey`, `HashFunction`), and collapses two parallel APIs (`com.launcher.api.crypto.*` + `family.crypto.*`) into one (`cryptokit.*`).

## Open items

None. No items flagged for follow-up — the spec is internally consistent against this checklist.

---

domain-isolation: 16/16 CHK [x]
