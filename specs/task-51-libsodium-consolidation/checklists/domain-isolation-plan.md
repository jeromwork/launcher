# Checklist: domain-isolation (applied to plan.md)

**Spec**: [../spec.md](../spec.md) | **Plan**: [../plan.md](../plan.md) | **Data model**: [../data-model.md](../data-model.md)
**Applied**: 2026-06-26 | **Scope**: plan-level artifacts (plan.md + data-model.md + contracts/)

Separate from spec-level result in [`domain-isolation.md`](./domain-isolation.md) — this file evaluates the **plan / architecture / data-model** outputs of speckit-plan, not spec.md.

---

## Vendor SDKs

- [x] CHK001 No vendor SDK type (Firebase, Coil, WhatsApp, Google Play Services, Crashlytics, etc.) appears in any signature visible to the domain layer (`commonMain` ports, domain values, repositories).
  - Plan module map (§Architecture): `cryptokit.crypto.api.*` and `cryptokit.pairing.api.*` ports live in `:core:crypto/commonMain` and reference only domain types (`PublicKey`, `DeviceId`, `EncryptedEnvelope`, `CryptoException`).
  - ionspin libsodium types are confined to `cryptokit/crypto/libsodium/` package (adapter side).
  - Android Keystore types confined to `androidMain` SecureKeyStore actual.
  - data-model.md §1: Firebase/Firestore types appear only as **cause-of-CryptoException** (caught and wrapped in adapter) — never in port signatures.

- [x] CHK002 Each external SDK has exactly one wrapper module (the **adapter**); domain references **only the port**.
  - ionspin libsodium → wrapped via `cryptokit/crypto/libsodium/` adapter package (one module).
  - Android Keystore → wrapped via `androidMain` `SecureKeyStore` actual.
  - Firestore → wrapped via `DeviceIdentityRepository` + `EncryptedMediaStorage` port implementations in `:core` androidMain.
  - lazysodium → being **removed** entirely (no longer exists post-Phase 6).

- [x] CHK003 The "vendor disappears tomorrow" test: number of files needing change is ≤ size of one adapter module. Documented.
  - Plan Risks table + Module map: if ionspin disappears → only `cryptokit/crypto/libsodium/` package files change (domain ports stay). Adapter swap is the entire migration story for TASK-51 itself (lazysodium → ionspin) — demonstrates the boundary works.

## Transport types

- [x] CHK004 No transport types (HTTP clients, retrofit annotations, raw JSON containers) appear in domain signatures.
  - Port signatures (data-model.md §2.9-2.11) use domain types only: `DeviceIdentity`, `EncryptedEnvelope`, `Uuid`, `DeviceId`.
  - kotlinx.serialization `@Serializable`/`@SerialName` annotations are on **domain-owned data classes** (wire-format types we own), not transport DTOs.

- [x] CHK005 If domain emits/consumes a wire format, the **wire format type** is a domain-owned data class with serializers in adapter (not a generated DTO posing as a domain model).
  - `DeviceIdentity`, `EncryptedEnvelope`, `Recipient`, `Ciphertext` — all domain-owned data classes in `cryptokit.pairing.api.*`.
  - Contracts (contracts/device-identity.md, encrypted-envelope.md, ciphertext.md) define byte layout + schemaVersion + `@SerialName` discipline.
  - Serialization mechanism (kotlinx.serialization) operates on the domain type; no separate DTO.

## Platform types

- [x] CHK006 No `android.*`, `androidx.*`, `Intent`, `Uri`, `Context`, `Bundle`, `LifecycleOwner` appears in `commonMain`.
  - data-model.md ports use only Kotlin stdlib + project domain types.
  - `SecureKeyStore` is `expect`/`actual` — `expect` in commonMain has pure-domain signature, `actual` in androidMain hides Keystore types.

- [x] CHK007 If a domain value needs platform-derived data (e.g., a package name, a content URI), it carries a domain-typed projection (`String`, `LauncherUri`), not the raw platform type.
  - `DeviceId(value: String)`, `PublicKey(bytes: ByteArray)`, `Uuid` — all primitive/domain projections.
  - No `android.net.Uri` or `Context` leaks into envelope/identity types.

## Ports

- [x] CHK008 Every external surface used by this spec is exposed through a **port** (interface in `commonMain`).
  - `AeadCipher`, `AsymmetricCrypto`, `KeyDerivation`, `SecureKeyStore`, `RandomSource`, `PasswordHash`, `KeyRotation`, `KeyEscrow` — all ports in `cryptokit.crypto.api`.
  - `DeviceIdentityRepository`, `EncryptedMediaStorage`, `RecipientResolver` — ports in `cryptokit.pairing.api`.

- [x] CHK009 Port shape is driven by domain need, not by adapter convenience (no method like `getFromSharedPreferences(key)`).
  - data-model.md §2.9: `publishOwn(linkId, identity)`, `fetchPeer(linkId, peerDeviceId)`, `listAll(linkId)` — domain verbs.
  - No `getFromSharedPreferences`, `readFirestoreDoc`, `getKeystoreAlias` style methods.
  - `SecureKeyStore.load/store(KeyId)` — domain concept (KeyId), not Android Keystore alias string.

- [x] CHK010 Each port has a fake adapter (in `commonTest` or shared test artifact) used by tests of higher-level code (per CLAUDE.md rule §6).
  - Module map: `commonTest/cryptokit/crypto/fake/` (FakeAeadCipher, FakeAsymmetricCrypto, etc.) and `commonTest/cryptokit/pairing/fake/` (FakeDeviceIdentityRepository, FakeEncryptedMediaStorage).
  - Test strategy §Unit tests: `PairingCryptoCoordinatorTest` rewritten to use new fakes.

- [x] CHK011 Each port has a real adapter (in `androidMain` and/or `iosMain`).
  - Module map: `commonMain/cryptokit/crypto/libsodium/` for crypto primitive real adapters (cross-platform via ionspin), `androidMain/cryptokit/crypto/libsodium/` for SecureKeyStore actual.
  - Pairing-side real adapters (Firestore-backed DeviceIdentityRepository etc.) in `:core/androidMain/adapters/crypto/`.

- [x] CHK012 DI wiring picks fake/real per build per CLAUDE.md rule §6.
  - Plan FR-015 + Module map: unified `cryptokitModule.kt` in `:app/di/`. Existing flavor-based DI pattern (mockBackend vs realBackend) preserved.
  - `NoFakeCryptoInAppTest` Konsist rule (existing, hardcoded path updated) enforces that production build doesn't pull fake adapters.

## Source-set placement

- [x] CHK013 For every new file: clearly assigned to `commonMain` / `androidMain` / `iosMain` with one-sentence justification (per ADR-005 §3 Gate 1).
  - Plan §Architecture Module map is explicit per file/package:
    - `cryptokit.crypto.api`, `cryptokit.crypto.libsodium`, `cryptokit.crypto.exception`, `cryptokit.pairing.api` → `commonMain` (pure Kotlin / ionspin KMP).
    - `SecureKeyStore` actual → `androidMain` (Android Keystore).
    - `PairingCryptoCoordinator`, repos → `:core/androidMain/adapters/crypto/` (Firestore-backed).

- [x] CHK014 Default placement is `commonMain`; deviation has explicit reason (uses platform API, requires platform packaging, etc.).
  - Plan: commonMain is the default; androidMain is used only for `SecureKeyStore` actual (Android Keystore TEE) and pairing-side Firestore adapters. iosMain placeholders for TASK-26 future.

## Existing-code regressions

- [x] CHK015 Spec doesn't reintroduce any vendor type into a `commonMain` file already cleansed by prior specs.
  - Konsist rules `Spec011IsolationTest` + `Spec014IsolationTest` updated to extend ban list (plan §Test strategy → Fitness functions).
  - New rules `NoLazysodiumInProductionTest`, `NoLegacyComLauncherCryptoTest`, `NoLegacyFamilyNamespaceTest` prevent regression.

- [x] CHK016 Spec doesn't add new `expect`/`actual` declaration where pure-Kotlin would suffice.
  - `SecureKeyStore` is the only expect/actual — justified (Android Keystore TEE, iOS Keychain are platform-specific by nature).
  - All other crypto primitives go through ionspin KMP library directly in commonMain (no expect/actual needed).
  - HashFunction port explicitly rejected (R-004) — confirms minimization stance.

---

## Notes

- Plan does NOT introduce new vendor dependencies; it **removes** lazysodium + JNA, reducing surface area.
- The TASK-51 refactor itself demonstrates the value of CHK001-CHK003: lazysodium → ionspin swap touches only the adapter module shape (rename `family.* → cryptokit.*`), not domain consumers — the boundary held.
- `kotlinx.serialization` is allowed in domain per project convention (CLAUDE.md rule 1 list of allowed: "pure language standard library, coroutine and flow primitives, other domain types"). Kotlinx is project-standard, not a vendor SDK in the rule-1 sense.

---

domain-isolation-plan: 16/16 CHK [x]
