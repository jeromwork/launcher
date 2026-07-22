# Key Hierarchy — developer reference (F-5 task-6)

> **Architecture (the derivation chain, invariants, envelope, recovery) lives in
> [`../architecture/crypto-key-hierarchy.md`](../architecture/crypto-key-hierarchy.md)** — the single source of truth.
> This file is developer-facing only: the port→implementation mapping and the "how to add a purpose" how-to.
> For the owner-facing explanation see [`../recovery-flow.md`](../recovery-flow.md).

## Players (ports)

| Port | Module | Implementations |
|---|---|---|
| `RootKeyManager` | `:core:keys` commonMain | `RootKeyManagerImpl` (real), `FakeRootKeyManager` (tests) |
| `KeyRegistry` | `:core:keys` commonMain | `AndroidKeystoreRegistry` (androidMain), `FakeKeyRegistry` (tests) |
| `DeviceKeyNamespaceProvider` | `:core:keys` commonMain | `AndroidDeviceKeyNamespaceProvider` (androidMain) — used in US-4 local-only mode |
| `RecoveryKeyBackup` | `:core:keys` commonMain | `WorkerRecoveryKeyBackup` (`:app` HttpURLConnection client against workers/backup), `FakeRecoveryKeyBackup` (tests) |
| `AuthAvailability` | `:core:keys` commonMain | `AuthAvailabilityAndroidImpl` (`:app`, bridges F-4 `GmsAvailabilityPort`) |
| `PassphrasePrompter` | `:core:keys` commonMain | `RecoveryViewModel` (Compose UI bridge), `FakePassphrasePrompter` (tests) |
| `PassphraseAttemptCounter` | `:core:keys` commonMain | `DataStorePassphraseAttemptCounter` (`:app`), `InMemoryAttemptCounter` (tests) |
| `SchemaVersionMemory` | `:core:keys` commonMain | `DataStoreSchemaVersionMemory` (`:app`) |
| `SecureKeyStore` | `:core:crypto` (expect/actual) | Android Keystore TEE wrap (`SecureKeyStore.android.kt`) |
| `KeyDerivation` | `:core:crypto` commonMain | `LibsodiumKeyDerivation` (HKDF-SHA256 over jose-style HMAC) |
| `PasswordHash` | `:core:crypto` commonMain | `LibsodiumArgon2idPasswordHash` |
| `AeadCipher` | `:core:crypto` commonMain | `LibsodiumAeadCipher` (XChaCha20-Poly1305) |

## Wire formats

`RecoveryKeyBackupBlob` (v1) — JSON document persisted by `workers/backup/`
on R2 under `backup/{stableId}/v1.json`. See
[`contracts/recovery-key-backup-v1.md`](../../specs/task-6-root-key-hierarchy-recovery/contracts/recovery-key-backup-v1.md).

`AAD_PREFIX = "f5-recovery-vault-v1"` — D4 scope-excluded wire constant.
Renaming would invalidate every produced ciphertext within v1; deferred
until a v2 schema bump where the rename can ride along.

## Exit ramps

| Concern | Today | Exit ramp |
|---|---|---|
| Root-key rotation | Not supported (single root per identity for the lifetime of an install) | TASK-41 in Phase 5 backlog. Adds `rotate(): Outcome<Unit, _>` to `RootKeyManager`, requires bump to a fresh `KeyRegistry.namespace(version)` slot, walks user data through re-encrypt. |
| Forward secrecy on recovery blob | None (single blob lives forever until Fallback wipe) | TASK-41 same epic. |
| KDF algorithm migration (Argon2id → next gen) | `RecoveryKeyBackupBlob.kdfParams.algorithm` already varied; RecoveryFlow refuses unknown algorithm via the R16 guard. | New algorithm string in `KdfParams.ALGORITHM_*` constants, RecoveryFlow branch on read, re-wrap on successful unwrap (background re-upload). |
| Backup endpoint relocation (Cloudflare → own server) | `BuildConfig.RECOVERY_BACKUP_WORKER_URL` is the single configurable. `WorkerRecoveryKeyBackup` adapter swap. | Documented in `server-roadmap.md` SRV-RECOVERY-001. |
| Identity-token claim layout | Today: `claims.uid` proxies `stableId` until `workers/identity/ POST /init-claim` (Track B) deploys. | `claims.stableId` once Track B is live. App-side `InitClaimClient.kt` runs the binding call after first Sign-In. |
| Switch from BundledSource RecoveryBackup to alternative ConfigSource | N/A — backup is identity-bound, not shareable. Not subject to rule 9. | N/A. |

## How to add a new `purpose`

1. Pick a stable string (`"contacts"`, `"media"`, ...). Same value MUST be
   used across every device of every install — it goes through HKDF `info`.
2. Add the string constant to a `KeyPurpose` companion object somewhere in
   the consuming module (NOT in `:core:keys` — domain stays generic).
3. Call `keyRegistry.derive(authIdentity.stableId, purpose)`.
4. Add an integration test that proves `derive(...)` is idempotent across
   process restarts AND that two different purposes don't collide.

When the registry grows past 5 purposes, follow the inline TODO in
`KeyRegistry.kt` and introduce a `sealed class Purpose` so the compiler
forces exhaustiveness on all consumers.
