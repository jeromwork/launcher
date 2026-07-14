# PR Draft: TASK-112 — KeyVault Port Boundary

**Branch**: `task-112-keyvault-port` → `main`
**Backlog**: [task-112 → Verification](../../backlog/tasks/task-112%20-%20Decision-Cross-platform-IdentityVault.md)
**Session 6 Decision block** (revised 2026-07-14) — `KeyVault` port + `RecoveryStrategy` port (pluggable — passphrase MVP, BIP39/2FA future additive); Purpose enum minimal (CONFIG + RECOVERY_BLOB); libsodium-kmp software crypto layer; Android Keystore ONLY for `root_key` at rest.

## Summary

Introduces the `KeyVault` port as the single misuse-resistant cryptographic entry point for the domain layer (rule 1, rule 2). Feature modules now route all symmetric AEAD / MAC / Ed25519 signatures through one interface instead of raw `KeyRegistry.derive(...).bytes` access.

* Full port + newtypes + `Purpose` enum with explicit stableId + sealed `VaultException` (7 variants).
* `PassphraseRecovery` adapter — Argon2id V1 (64 MiB, 3 iter) + Bitwarden salt pattern + known-plaintext validation blob.
* `KeyVaultCore` shared logic + `FakeKeyVault` (JVM) + `AndroidKeyVault` (SecureKeyStore + libsodium-JNI) — one code path, byte-equal wire format across platforms.
* Gradle fitness rule `verifyKeysNoVendorImports` — bans `com.google.*` / `android.*` / feature-module imports in `:core:keys` commonMain.
* `KeyVault` DI wired in `LauncherApplication` as additive to spec-018 flow — feature modules can start depending on it today.

## Files changed

Added:
- [KeyVault.kt](core/keys/src/commonMain/kotlin/cryptokit/keys/api/vault/KeyVault.kt) — 9-method port
- [Purpose.kt](core/keys/src/commonMain/kotlin/cryptokit/keys/api/vault/Purpose.kt), [Ciphertext.kt](core/keys/src/commonMain/kotlin/cryptokit/keys/api/vault/Ciphertext.kt), [Aad.kt](core/keys/src/commonMain/kotlin/cryptokit/keys/api/vault/Aad.kt), [MacTag.kt](core/keys/src/commonMain/kotlin/cryptokit/keys/api/vault/MacTag.kt), [Signature.kt](core/keys/src/commonMain/kotlin/cryptokit/keys/api/vault/Signature.kt), [PublicIdentity.kt](core/keys/src/commonMain/kotlin/cryptokit/keys/api/vault/PublicIdentity.kt) — newtypes
- [VaultException.kt](core/keys/src/commonMain/kotlin/cryptokit/keys/api/vault/VaultException.kt) — sealed hierarchy (7 variants)
- [RecoveryStrategy.kt](core/keys/src/commonMain/kotlin/cryptokit/keys/api/vault/RecoveryStrategy.kt), [IdentityHint.kt](core/keys/src/commonMain/kotlin/cryptokit/keys/api/vault/IdentityHint.kt), [PassphraseRecovery.kt](core/keys/src/commonMain/kotlin/cryptokit/keys/api/vault/PassphraseRecovery.kt)
- [FakeKeyVaultLogic.kt](core/keys/src/commonMain/kotlin/cryptokit/keys/impl/vault/FakeKeyVaultLogic.kt) — `KeyVaultCore` shared logic
- [RootKey.kt](core/keys/src/commonMain/kotlin/cryptokit/keys/impl/vault/RootKey.kt), [BlobHeader.kt](core/keys/src/commonMain/kotlin/cryptokit/keys/impl/vault/BlobHeader.kt), [Argon2Params.kt](core/keys/src/commonMain/kotlin/cryptokit/keys/impl/vault/Argon2Params.kt), [ValidationBlobStore.kt](core/keys/src/commonMain/kotlin/cryptokit/keys/impl/vault/ValidationBlobStore.kt) — internal helpers
- [AndroidKeyVault.kt](core/keys/src/androidMain/kotlin/cryptokit/keys/impl/vault/AndroidKeyVault.kt), [AndroidValidationBlobStore.kt](core/keys/src/androidMain/kotlin/cryptokit/keys/impl/vault/AndroidValidationBlobStore.kt) — Android adapter
- [FakeKeyVault.kt](core/keys/src/commonTest/kotlin/cryptokit/keys/vault/FakeKeyVault.kt), [TestRecoveryStrategy.kt](core/keys/src/commonTest/kotlin/cryptokit/keys/vault/TestRecoveryStrategy.kt) — mock-first adapters
- [KeyVaultContractTest.kt](core/keys/src/commonTest/kotlin/cryptokit/keys/vault/KeyVaultContractTest.kt) (15 tests), [BlobHeaderTest.kt](core/keys/src/commonTest/kotlin/cryptokit/keys/vault/BlobHeaderTest.kt) (6), [PurposeEnforcementTest.kt](core/keys/src/commonTest/kotlin/cryptokit/keys/vault/PurposeEnforcementTest.kt) (3), [AadCanonicalTest.kt](core/keys/src/commonTest/kotlin/cryptokit/keys/vault/AadCanonicalTest.kt) (4), [RecoveryStrategyTest.kt](core/keys/src/commonTest/kotlin/cryptokit/keys/vault/RecoveryStrategyTest.kt) (6), [CrossPlatformVectorTest.kt](core/keys/src/commonTest/kotlin/cryptokit/keys/vault/CrossPlatformVectorTest.kt) (3), [VaultExceptionExhaustivenessTest.kt](core/keys/src/commonTest/kotlin/cryptokit/keys/vault/VaultExceptionExhaustivenessTest.kt) (1)
- [AndroidKeyVaultIntegrationTest.kt](core/keys/src/androidInstrumentedTest/kotlin/cryptokit/keys/impl/vault/AndroidKeyVaultIntegrationTest.kt), [CrossPlatformVectorAndroidTest.kt](core/keys/src/androidInstrumentedTest/kotlin/cryptokit/keys/impl/vault/CrossPlatformVectorAndroidTest.kt) — compile-only; run on emulator gate
- [vectors/v1.json](core/keys/src/commonTest/resources/vectors/v1.json) — cross-platform test vector fixture (freeze-expected-bytes deferred to phase-2 emulator run)
- [KeyVaultModule.kt](app/src/main/java/com/launcher/app/di/KeyVaultModule.kt) — Koin wiring for `AndroidKeyVault`
- [PR-DRAFT.md](specs/task-112-keyvault-port/PR-DRAFT.md) — this file

Modified:
- [AsymmetricCrypto.kt](core/crypto/src/commonMain/kotlin/cryptokit/crypto/api/AsymmetricCrypto.kt) + [LibsodiumAsymmetricCrypto.kt](core/crypto/src/commonMain/kotlin/cryptokit/crypto/libsodium/LibsodiumAsymmetricCrypto.kt) + [FakeAsymmetricCrypto.kt](core/crypto/src/commonTest/kotlin/cryptokit/crypto/fake/FakeAsymmetricCrypto.kt) — added `ed25519KeyPairFromSeed(seed)` port + libsodium impl using `crypto_sign_seed_keypair`. Without this, every `unlock()` would mint a fresh Ed25519 identity.
- [core/keys/build.gradle.kts](core/keys/build.gradle.kts) — new fitness gradle task `verifyKeysNoVendorImports` wired into `:core:keys:check`
- [RootKey.kt](core/keys/src/commonMain/kotlin/cryptokit/keys/api/RootKey.kt), [KeyRegistry.kt](core/keys/src/commonMain/kotlin/cryptokit/keys/api/KeyRegistry.kt) — `@Deprecated` warning steering new code to `KeyVault`. Legacy TASK-6 flow keeps compiling.
- [LauncherApplication.kt](app/src/main/java/com/launcher/app/LauncherApplication.kt) — imports + registers `keyVaultModule`

## Verification

### Automated (all green)
- `./gradlew :core:keys:jvmTest` — 38 new tests + 200 existing spec-018 tests: BUILD SUCCESSFUL
- `./gradlew :core:keys:verifyKeysNoVendorImports :core:keys:verifyKeysIsolation` — clean
- `./gradlew :core:keys:compileDebugKotlinAndroid :core:keys:compileDebugAndroidTestKotlinAndroid` — clean
- `./gradlew :app:compileMockBackendDebugKotlin` — clean (KeyVaultModule DI compiles)

### Emulator gate (pending, run per `android-emulator` skill)
- `./gradlew :core:keys:connectedAndroidTest` on `pixel_5_api_34` — runs `AndroidKeyVaultIntegrationTest` + `CrossPlatformVectorAndroidTest` (SC-004 byte-equal parity vs JVM run).

### Manual smoke
- Install `mockBackendDebug` on emulator → verify existing spec-018 recovery flow (Setup → Entry → Fallback) still passes UI regression (SC-005). No UI change expected — TASK-112 delivered as additive infrastructure.

## Design decisions (non-obvious)

1. **`RecoveryStrategy` = `abstract class`, not `sealed`** — Kotlin/MPP treats `commonTest` as a separate module for sealed-subclass purposes, which would block `TestRecoveryStrategy` extension. Isolation of `deriveRoot` / `verifyUnlock` now enforced by convention + Rule 1 (feature modules construct strategies, don't invoke methods directly).
2. **`Purpose.stableId` explicit** (`0x0001 CONFIG`, `0x0002 RECOVERY_BLOB`) — ordinal-based ids rejected as fragile against enum reorder. Blob header carries these ids for cross-purpose safety (SC-008 `WrongPurpose`).
3. **MAC via HKDF-Extract** (`HMAC-SHA256(macKey, message)` — `KeyDerivation.derive(ikm=message, salt=macKey, info="mac-v1", 32)`) — no dedicated keyed-MAC port in `:core:crypto` yet. Upgrade to real BLAKE2b keyed hash tracked in server-roadmap `SRV-CRYPTO-MAC-UPGRADE`.
4. **`AndroidKeyVault` extends `KeyVaultCore`** — visibility bump on `KeyVaultCore` to `open class` (was `internal open class`). Feature modules MUST NOT construct or subclass `KeyVaultCore` directly (KDoc warns). DI provides `KeyVault` interface only.
5. **`AndroidKeyVault` reuses existing `SecureKeyStore`** from `:core:crypto` (Android Keystore wrap pattern) instead of writing a new `EncryptedSharedPreferences` layer as plan T014 suggested — same crypto properties, existing test coverage on OEM matrix.

## Scope adjustments from plan (documented in [tasks.md](specs/task-112-keyvault-port/tasks.md))

**T018-T020 no-op**: Original plan assumed `ConfigCipher2` used direct `keyRegistry.derive("config").bytes + aead.seal`. Reality: `EnvelopeConfigCipherImpl` uses hybrid encryption (fresh CEK + X25519 sealed-box) — no root-derived key involved. External modules (`:app`, `:core:cloud`, `:core:push`) show **zero** uses of `DerivedKey.bytes` — domain isolation already achieved via existing api/impl split.

**T022 deferred**: No `LogoutManager` / `signOut` handler exists in `:app` yet; `keyVault.wipe()` wire-up gated on downstream feature work that introduces logout.

**T023-T026 deferred (Phase 4 downgrade)**: Making `RootKey` / `KeyRegistry` `internal` would break the entire spec-018 recovery UI (`FirstLaunchActivity`, `RecoveryViewModel`, `RootKeyManagerImpl` — `Outcome<RootKey, ...>` returns). Replacing spec-018 with `KeyVault.unlock(PassphraseRecovery)` requires a different crypto design (spec-018: random root wrapped by passphrase; TASK-112: root derived FROM passphrase) — epic scope, deserves its own follow-up TASK. Interim: `@Deprecated` warnings steer new code.

## Backlog: task-112 → Verification

Pending AC:
- `[auto:deferred-local-emulator]` `./gradlew :core:keys:connectedAndroidTest` — `AndroidKeyVaultIntegrationTest` + `CrossPlatformVectorAndroidTest`
- `[auto:deferred-physical-device]` OEM matrix smoke — Xiaomi 11T, Samsung, Pixel (`KeyStoreContext` compat) — physical devices unavailable
- `[hand]` `SC-005` regression — TASK-6 UI flow byte-identical after `keyVaultModule` DI addition (verify via mockBackend smoke)

Follow-up tasks proposed:
- `TASK-112a` — replace spec-018 recovery flow with `KeyVault.unlock(PassphraseRecovery)` — unblocks full SC-010 downgrade.
- `TASK-112b` — freeze `vectors/v1.json` expected bytes after Android + JVM parity run.
