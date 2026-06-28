# Pre-Implementation Inventory: F-5 — Root Key Hierarchy + Owner Recovery

**Task**: T600 (Phase 0)  
**Date**: 2026-06-28  
**Purpose**: Reconciliation of `plan.md` / `tasks.md` with existing code reality in `:core:keys` (shipped previously under spec 018 / F-5b). Per `gemini-handoff.md`, legacy KEEP files must remain untouched, MIGRATE files will be adapted with byte-equal backward compatibility where required, and ADD files will introduce F-5 specific ports, types, and adapters.

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
- `RecoveryKeyVault.kt` — Legacy vault interface for Firestore recovery blobs.
- `RecoveryVaultBlob.kt` — Legacy vault blob structure.
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
These existing files require refactoring or adaptation during F-5 implementation while strictly maintaining backward compatibility (e.g., FR-018 byte-equal preservation).

- `core/keys/src/commonMain/kotlin/family/keys/api/internal/ConfigCipher2.kt` — Must be migrated in Phase 5 (T671) to derive key material via `KeyRegistry.derive(stableId, "config")` while preserving byte-equal decryption of spec 018 ciphertexts.
- `core/keys/src/commonMain/kotlin/family/keys/api/RootKey.kt` — Already exists as an opaque wrapper around `ByteArray`; will be kept/aligned with F-5 `wipe()` requirements.
- `core/keys/src/commonMain/kotlin/family/keys/api/RootKeyError.kt` — Already exists; will be updated/aligned with F-5 error cases (`WrongPassphrase`, `CorruptedBlob`, `NoKeystore`, `NoIdentity`).
- `core/keys/src/commonMain/kotlin/family/keys/api/RootKeyManager.kt` — Already exists; will be migrated/aligned with F-5 port contract (`current: Flow<RootKey?>`, `create()`, `recover()`, `forget()`).
- `core/keys/src/commonMain/kotlin/family/keys/impl/RootKeyManagerImpl.kt` — Existing implementation to be adapted or superseded by Android adapter implementation per package conventions (`family.keys.android.Argon2RootKeyManager`).

---

## (c) ADD Files (New F-5 Implementations)
New files to be created following actual repository package conventions (`family.keys.api` for common ports/types, `family.keys.api.internal` for common implementations, `family.keys.android` for Android adapters).

### Domain Value Types & Ports (`core/keys/src/commonMain/kotlin/family/keys/api/`)
- `StableId.kt` — Type alias for `String` with UUID v4 provider-agnostic invariants (T602).
- `KdfParams.kt` — Value class for Argon2id parameters with init-block validation (T603).
- `DerivedKey.kt` — Opaque value class wrapping HKDF output (T605).
- `AvailabilityReason.kt` — Enum for capability detection reasons (T606).
- `AuthAvailabilityStatus.kt` — Sealed status class (`Available` / `Unavailable`) (T607).
- `BackupError.kt` — Sealed error hierarchy for backup operations (T609).
- `RecoveryKeyBackupBlob.kt` — Wire format data class with `schemaVersion=1` (T610).
- `KeyRegistry.kt` — Port for key derivation (`derive`, `wipeAll`, `list`) (T612).
- `RecoveryKeyBackup.kt` — Port for backup storage (`uploadBlob`, `fetchBlob`, `deleteBlob`) (T614).
- `AuthAvailability.kt` — Port for checking auth provider availability (T615).

### Common Internal Codecs (`core/keys/src/commonMain/kotlin/family/keys/api/internal/`)
- `RecoveryBlobCodec.kt` — JSON encode/decode with strict schema version verification (T611).

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
