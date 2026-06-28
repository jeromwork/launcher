# Pre-Implementation Inventory: F-5 — Root Key Hierarchy + Owner Recovery

**Task**: T600 (Phase 0 — Revised per Review)  
**Date**: 2026-06-28  
**Purpose**: Reconciliation of `plan.md` / `tasks.md` with existing code reality in `:core:keys` (shipped previously under spec 018 / F-5b). Identifies KEEP, MIGRATE, and ADD files, and explicitly documents ABI mismatches and overlapping test fakes/contracts to prevent accidental duplication during implementation.

---

## (a) KEEP Files (Legacy F-5b / Foundation)
These files exist in `:core:keys` on `main` and provide domain types, error models, and envelope storage mechanics from spec 018. They must **not** be deleted or modified unless explicitly needed for non-breaking additions.

### `core/keys/src/commonMain/kotlin/family/keys/api/`
- `AsyncConfigPushQueue.kt` — Async queue contract for config pushing.
- `AuthIdentity.kt` — Local domain representation of authenticated identity (rule 2 ACL copy).
- `CipherError.kt` — Legacy F-5b cipher errors.
- `ConfigChangeNotifier.kt` — Notification contract for config mutations.
- `ConfigSaver.kt` — High-level config saver interface.
- `DeviceId.kt` — Device identification wrapper.
- `Envelope.kt` — Wire format for encrypted configuration payload.
- `EnvelopeBootstrap.kt` — Bootstrap logic for remote envelope fetching.
- `IdentityError.kt` — Identity resolution error types.
- `IdentityProof.kt` — Legacy proof of identity representation.
- `Module.kt` — KMP DI / module wiring declarations.
- `Outcome.kt` — Monadic result wrapper (`Success` / `Failure`).
- `PassphraseAttemptCounter.kt` — Legacy interface for persistent attempt counting.
- `PassphraseKdfParams.kt` — Legacy Argon2id KDF parameter holder from spec 018.
- `PassphrasePrompter.kt` — UI prompting contract for passphrase entry.
- `RecipientPubKey.kt` — Public key wrapper for multi-recipient envelopes.
- `RecoveryError.kt` — Recovery error models.
- `RemoteStorage.kt` — Remote storage interface.
- `SchemaVersionMemory.kt` — Tracks last-seen schema version for migration detection.
- `StorageError.kt` — Storage failure hierarchy.
- `VaultError.kt` — Vault error models.

### `core/keys/src/commonMain/kotlin/family/keys/api/internal/`
- `ByteArrayBase64Serializer.kt` — kotlinx.serialization helper for base64 byte arrays.
- `DeviceIdentity.kt` — Internal device identity logic.
- `EnvelopeStorage.kt` — Local/remote envelope persistence coordination.
- `PublicKeyDirectory.kt` — Directory of public keys.
- `RecipientResolver.kt` — Resolves recipients for envelope encryption.

### `core/keys/src/commonMain/kotlin/family/keys/impl/`
*(Note: Per project convention, KMP commonMain implementations reside in `family.keys.impl`, not `internal/`.)*
- `Argon2idPassphraseKdf.kt` — Passphrase derivation implementation.
- `DefaultEnvelopeBootstrap.kt` — Default implementation of envelope bootstrap.
- `EnvelopeConfigCipherImpl.kt` — Implementation of envelope cipher.
- `EnvelopeRemoteStorage.kt` — Remote storage implementation.
- `LocalFirstConfigSaver.kt` — Offline-first config saving implementation.
- `NoOpConfigChangeNotifier.kt` — No-op notification stub.
- `PublicKeyDirectoryRecipientResolver.kt` — Recipient resolver implementation.
- `RecoveryFlow.kt` — Legacy F-5b recovery flow coordinator.
- `RemoteStorageConfigSaver.kt` — Remote storage config saver.

### `core/keys/src/androidMain/kotlin/family/keys/android/`
- `AndroidDeviceIdentity.kt` — Android implementation of device identity.
- `WorkManagerAsyncConfigPushQueue.kt` — WorkManager-backed push queue.

---

## (b) MIGRATE Files (Modified by F-5)
These existing files require refactoring or adaptation during F-5 implementation while strictly maintaining backward compatibility (e.g., FR-018 byte-equal preservation). See section `(d) ABI MISMATCH` below for exact reconciliation rules.

- `core/keys/src/commonMain/kotlin/family/keys/api/internal/ConfigCipher2.kt` — Must be migrated in Phase 5 (T671) to derive key material via `KeyRegistry.derive(stableId, "config")` while preserving byte-equal decryption of spec 018 ciphertexts.
- `core/keys/src/commonMain/kotlin/family/keys/api/RootKey.kt` — Retained with existing shape per D1.
- `core/keys/src/commonMain/kotlin/family/keys/api/RootKeyError.kt` — Extended with new F-5 error cases per D2.
- `core/keys/src/commonMain/kotlin/family/keys/api/RootKeyManager.kt` — Extended with F-5 lifecycle methods per D3.
- `core/keys/src/commonMain/kotlin/family/keys/api/RecoveryKeyVault.kt` — Renamed to `RecoveryKeyBackup.kt` per D4.
- `core/keys/src/commonMain/kotlin/family/keys/api/RecoveryVaultBlob.kt` — Renamed to `RecoveryKeyBackupBlob.kt` per D4.
- `core/keys/src/commonMain/kotlin/family/keys/impl/RootKeyManagerImpl.kt` — Adapted/extended to support F-5 lifecycle methods.

---

## (c) ADD Files (New F-5 Implementations)
New files to be created following actual repository package conventions (`family.keys.api` for common ports/types, `family.keys.impl` for common implementations, `family.keys.android` for Android adapters).

### Domain Value Types & Ports (`core/keys/src/commonMain/kotlin/family/keys/api/`)
- `StableId.kt` — Type alias for `String` with UUID v4 provider-agnostic invariants (T602).
- `KdfParams.kt` — Value class for Argon2id parameters with init-block validation (T603).
- `DerivedKey.kt` — Opaque value class wrapping HKDF output (T605).
- `AvailabilityReason.kt` — Enum for capability detection reasons (T606).
- `AuthAvailabilityStatus.kt` — Sealed status class (`Available` / `Unavailable`) (T607).
- `BackupError.kt` — Sealed error hierarchy for backup operations (T609).
- `KeyRegistry.kt` — Port for key derivation (`derive`, `wipeAll`, `list`) (T612).
- `AuthAvailability.kt` — Port for checking auth provider availability (T615).

### Common Implementations & Codecs (`core/keys/src/commonMain/kotlin/family/keys/impl/`)
- `RecoveryBlobCodec.kt` — JSON encode/decode with strict schema version verification (T611). placed under `family.keys.impl` per project convention.

### Android Adapters (`core/keys/src/androidMain/kotlin/family/keys/android/`)
- `AndroidKeystoreRegistry.kt` — Implements `KeyRegistry` via `SecureKeystore` (T632).
- `Argon2RootKeyManager.kt` — Implements `RootKeyManager` via libsodium (T633).
- `DeviceKeyNamespaceProvider.kt` — Provides local device root key alias (T634).

### App-Layer Adapters & UI (`app/src/main/kotlin/com/launcher/`)
- `data/recovery/WorkerRecoveryKeyBackup.kt` — OkHttp HTTPS client against `workers/backup/` (T635).
- `data/recovery/DataStorePassphraseAttemptCounter.kt` — DataStore persistent attempt counter (T636).
- `data/recovery/DataStoreSchemaVersionMemory.kt` — Tracks schema version for migration (T637).
- `data/recovery/AuthAvailabilityAndroidImpl.kt` — Implements `AuthAvailability` via F-4 (T638).
- `data/identity/InitClaimClient.kt` — Client for calling `workers/identity/` endpoint (T668).
- `ui/recovery/RecoveryViewModel.kt` — ViewModel driving 3-screen recovery state machine (T644).
- `ui/recovery/RecoveryPassphraseSetupScreen.kt` — Setup screen Composable (T645).
- `ui/recovery/RecoveryPassphraseEntryScreen.kt` — Passphrase entry screen Composable (T646).
- `ui/recovery/RecoveryFallbackScreen.kt` — Fallback confirmation screen Composable (T647).
- `di/KeysModule.kt` — Dependency injection bindings (T648).

### Cloudflare Workers (`workers/`)
- `workers/backup/` — Complete Worker project for blob storage (T653-T661).
- `workers/identity/` — Complete Worker project for custom claim initialization (T662-T665).

### Documentation & Compliance
- `docs/recovery-flow.md` — Plain-Russian user guide (T673).
- `docs/dev/key-hierarchy.md` — Technical key derivation architecture diagram (T674).
- `app/src/main/res/xml/data_extraction_rules.xml` — Backup exclusion rules (T677).

---

## (d) ABI MISMATCH & RECONCILIATION DECISIONS

### D1: `RootKey` Shape
- **Existing Signature (`main`)**: `class RootKey(val bytes: ByteArray)` with public `bytes` property and explicit `.wipe()` method.
- **F-5 Plan Specification**: Opaque value class with private constructor.
- **Reconciliation Decision (D1)**: **KEEP EXISTING**. The existing shape with `.wipe()` provides superior cryptographic security by allowing callers to explicitly clear in-memory key buffers after deriving DEKs. F-5 plan and `data-model.md` will be updated to reflect this shape.

### D2: `RootKeyError` Cases
- **Existing Signature (`main`)**: `sealed class RootKeyError` with `KeystoreInvalidated`, `RecoveryRequired`, `StorageFailure`.
- **F-5 Plan Specification**: `WrongPassphrase`, `CorruptedBlob`, `NoKeystore`, `NoIdentity`.
- **Reconciliation Decision (D2)**: **EXTEND EXISTING**. Add `WrongPassphrase`, `CorruptedBlob`, and `NoIdentity` as additional subclasses inside existing `RootKeyError`. Existing cases (`KeystoreInvalidated`, `RecoveryRequired`, `StorageFailure`) remain fully valid and necessary for F-5 hardware/storage errors.

### D3: `RootKeyManager` API
- **Existing Signature (`main`)**: `suspend fun getOrCreate(identity): Outcome<RootKey, RootKeyError>` and `suspend fun wipe(identity): Outcome<Unit, RootKeyError>`.
- **F-5 Plan Specification**: `current: Flow<RootKey?>`, `create()`, `recover()`, `forget()`.
- **Reconciliation Decision (D3)**: **EXTEND EXISTING**. Keep `getOrCreate` and `wipe` intact to maintain backward compatibility for spec 018 consumers. Add `current: Flow<RootKey?>`, `create()`, `recover()`, and `forget()` alongside them in the `RootKeyManager` interface and adapt `RootKeyManagerImpl` accordingly.

### D4: `RecoveryKeyVault` → `RecoveryKeyBackup` Rename
- **Existing Signatures (`main`)**: `RecoveryKeyVault` port, `RecoveryVaultBlob` wire format, `fetchVault` / `storeVault` / `deleteVault` methods.
- **F-5 Plan Specification**: `RecoveryKeyBackup` port, `RecoveryKeyBackupBlob` wire format, `uploadBlob` / `fetchBlob` / `deleteBlob` methods.
- **Reconciliation Decision (D4)**: **RENAME NOW**. Per owner backlog decision from 2026-06-23, rename `Vault` to `Backup` across the codebase (~24 files) via `git mv` and sed-style import updates. Done in commit `7be001c`.

#### D4 Scope Exclusions (intentional — do NOT rename these)

The following three items retain legacy `vault` spelling by deliberate design decision. Any future rename must go through a separate migration task, not be done during Phase 1:

1. **`firestore.rules` — deployment surface, sync with live rules.**
   The Firestore security rules file is a deployment artefact — renaming it requires a coordinated live-rules push. Renamed collections/paths would break existing data. Scope exclusion: `firestore.rules` is **frozen at its current vault-named paths** until a dedicated migration window is scheduled with the owner.
   Reference: `FirestoreRecoveryKeyBackup.kt:149` comment references `isValidRecoveryVaultBlob` function name from `firestore.rules` — correct, do not rename.

2. **`AAD_PREFIX = "f5-recovery-vault-v1"` — FR-018 byte-equal wire compatibility.**
   Located in `RecoveryFlow.kt:175`. This string is embedded verbatim as Additional Authenticated Data (AAD) in every encrypted ciphertext written under spec 018. Renaming it would break decryption of all existing on-device envelopes (byte-equal AAD mismatch). Scope exclusion: **this constant must never change**. If a new AAD scheme is needed it must be introduced as a new version (`f5-recovery-backup-v2`) with explicit migration logic.

3. **`VaultError` sealed class — migration deferred to T609.**
   `VaultError.kt` was intentionally NOT renamed during D4. It will be superseded by the new `BackupError` sealed hierarchy introduced in T609 (§(c) ADD files). The migration path at T609 is:
   - Add `BackupError` as the new public error type for `RecoveryKeyBackup` operations.
   - Update `RecoveryKeyBackup.fetchBlob/uploadBlob/deleteBlob` signatures from `VaultError` → `BackupError`.
   - Update call-sites (`FirestoreRecoveryKeyBackup`, `RecoveryFlow`, fakes, contract tests).
   - Delete `VaultError.kt` only after all call-sites are migrated.
   Until T609: the intermediate naming inconsistency (`RecoveryKeyBackup` returns `VaultError`) is accepted.

---

## (e) Existing Test Overlaps (Preventing Duplicates)
The following test fakes, contracts, and fitness tests already exist in `core/keys/src/` on `main` and overlap with planned Phase 1 tasks (T616–T627, T631). Gemini must **extend or reuse** these rather than creating duplicate files:

1. **Fakes (`commonTest/fakes/`)**:
   - `FakeRecoveryKeyVault.kt` (will become `FakeRecoveryKeyBackup.kt` under D4) — fulfills **T618**. Do not recreate.
   - `FakePassphrasePrompter.kt`, `FakeIdentityProof.kt`, `FakeDeviceIdentity.kt`, `FakeEnvelopeStorage.kt` already exist.

2. **Contract & Wire Format Tests (`commonTest/`)**:
   - `contracts/RootKeyManagerContractTest.kt` — tests `RootKeyManager`. Must be extended for new F-5 methods rather than duplicated.
   - `contracts/RecoveryKeyVaultContractTest.kt` (will become `RecoveryKeyBackupContractTest.kt` under D4) — overlaps with **T622** roundtrip tests.
   - `RecoveryVaultBackwardCompatTest.kt` — overlaps with **T623** backward compatibility tests.
   - `WireFormatJsonTest.kt` — tests JSON encoding/decoding.

3. **Fitness Tests (`jvmTest/fitness/`)**:
   - `ImportRestrictionsFitnessTest.kt` — enforces layer boundaries and forbidden package imports. Overlaps with **T631** Konsist fitness rule. Must be audited/extended rather than creating a second fitness test file.
