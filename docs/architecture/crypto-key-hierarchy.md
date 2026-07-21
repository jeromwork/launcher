# Crypto key hierarchy (`:core:keys` / `family.keys`)

**This file is the single source of truth for the key-management layer** — root key, purpose derivation, envelope encryption of config, and the recovery vault. If it and any other doc disagree, this file wins — except: wire-format versioning is owned by [`wire-format.md`](wire-format.md), primitives by [`crypto-primitives.md`](crypto-primitives.md), and the umbrella by [`crypto.md`](crypto.md). Change the model → update this file in the same commit.

<!-- AI-TLDR:BEGIN — READ THIS FIRST. If you can answer from this block, STOP. -->

## AI TL;DR

**THE BEACON (do NOT re-decide)**: one **RootKey (32 bytes)** per identity, from which **HKDF-SHA256 derives one key per purpose** (`config`, `contacts`, `media`, …). This layer knows **what each key is *for*** — the thing the primitive layer is forbidden to know. It *uses* primitives ([`crypto-primitives.md`](crypto-primitives.md)); primitives never call up into it. Envelope encryption of the user's config and the passphrase recovery vault live here — both are **key management** (AWS KMS / NIST SP 800-57 framing), not primitives. Everything is in `:core:keys` (`family.keys`), which depends only on `:core:crypto`.

**Zone charter**

| Owns | Must NOT own |
|---|---|
| RootKey generation/recovery, HKDF purpose derivation, envelope encryption of config, recovery vault + anti-brute-force, rotation/escrow *stubs*, NIST 800-57 key states | crypto algorithms (down in primitives), feature wire formats (only its own blob shapes, via adapter DTOs), group protocol, transport |

**Derivation chain**

```
AuthIdentity.stableId (UUID v4)               ← namespace key throughout
        │
        ▼
   RootKey (32 bytes)   ── (a) generated locally on first Sign-In
        │                  (b) recovered: passphrase → Argon2id(blob.salt, blob.kdfParams)
        │                        → AEAD-unwrap(blob.ciphertext) → RootKey
        │  stored wrapped via SecureKeyStore (TEE / StrongBox)
        │
        │  HKDF-SHA256  (ikm=RootKey, salt=stableId, info=purpose, len=32)
        ├──► DerivedKey("config")     — config encryption surface
        ├──► DerivedKey("contacts")   — future surface
        └──► DerivedKey("media")      — future surface
```

**Ports** (built): `RootKeyManager`, `KeyRegistry` (HKDF derive), `DeviceKeyNamespaceProvider`, `RecoveryKeyBackup`, `PassphrasePrompter`, `PassphraseAttemptCounter`, `SchemaVersionMemory`, `AuthAvailability` — all in `:core:keys` commonMain. Envelope: `ConfigCipher2` (interface, `family.keys.api.internal`) + `EnvelopeConfigCipherImpl` + `Envelope` value. Primitive ports it consumes (`KeyDerivation`, `PasswordHash`=Argon2id, `AeadCipher`, `SecureKeyStore`) live in `:core:crypto`.

**Invariants** (K1–K5, do NOT re-derive — see §Invariants): K1 one RootKey per identity per install. K2 purpose is an HKDF `info` string, stable across all devices. K3 envelope depends on primitives only, never reaches into `RootKeyManager` internally. K4 recovery vault is identity-bound — never shareable (rule 9 does not apply). K5 no version/serialization in the value types (rule 1 crypto exception); wire shapes live in adapter DTOs.

**Open (owning task named)**: `KeyVault`/`IdentityVault` port boundary — **NOT built, NOT finally decided**, TASK-112 (do NOT present `KeyVault` as existing code). Anti-brute-force counter mechanism (SVR vs OPAQUE vs HMAC) — TASK-59. Recovery flows — TASK-21 (2FA escrow), TASK-39 (social).

**Rejected**: root-key rotation now (TASK-41, Phase 5); forward secrecy on the recovery blob now (same). See §Rejected/Exit ramps.

**Status**: BUILT (TASK-4 envelope, TASK-6 root key + recovery, TASK-66 bucket registry Done).

**Routing**: key/envelope/recovery question → stay here. Algorithm details → [`crypto-primitives.md`](crypto-primitives.md). Versioning → [`wire-format.md`](wire-format.md). Pairing → [`crypto-pairing.md`](crypto-pairing.md).

<!-- AI-TLDR:END -->

## Invariants (decided — do NOT re-derive; changing one is a `decision-supersedes` task)

- **K1 — one RootKey per identity for the install lifetime.** No rotation today (single root); rotation is TASK-41, additive.
- **K2 — purpose = stable HKDF `info` string.** The same purpose value must be used across every device of every install, or derived keys diverge. Adding a purpose = new stable string + collision/idempotence test (see how-to in the dev reference).
- **K3 — envelope encryption depends on primitives only.** `ConfigCipher2`/`EnvelopeConfigCipherImpl` takes plaintext + recipient pubkeys + AAD and produces an `Envelope` using AEAD + `crypto_box_seal` per recipient. It does **not** read `RootKeyManager`/`KeyRegistry` internally — those are orchestrated above it (`DefaultEnvelopeBootstrap`, `RemoteStorage`). Envelope is key-management (DEK-under-KEK, AWS KMS pattern), sitting on primitives.
- **K4 — recovery vault is identity-bound, never shareable.** Passphrase + `RecoveryKeyBackupBlob` are secrets bound to one identity; rule 9 (shareability) explicitly does not apply.
- **K5 — value types carry no version, no serialization.** `Envelope`, `RecoveryKeyBackupBlob` are plain classes (rule 1 crypto exception, TASK-141). Their wire shapes live in adapter DTOs (`RecoveryBlobJsonCodec` in `:app`, Firestore adapters) governed by [`wire-format.md`](wire-format.md).

## Rotation / escrow (stubs today)

`KeyRotation` and `KeyEscrow` are **declared ports with stub implementations** (`StubKeyRotation` throws `NotImplementedError`). Placement of the port declarations in `:core:crypto` is acceptable — they are primitive-adjacent declarations; the *policy* (when to rotate, escrow semantics) would live in this layer when built. Real rotation = TASK-41 (Phase 5); server-side escrow tracked as SRV-CRYPTO-002 in `../dev/server-roadmap.md`. KDF-algorithm migration (Argon2id → next) already supported: `KdfParams.algorithm` is varied and `RecoveryFlow` refuses unknown algorithms (R16 guard), re-wrapping on successful unwrap.

## Industry grounding

- **NIST SP 800-57 Pt 1 Rev 5** — key management (generation, derivation, distribution, rotation, revocation, escrow, recovery, key states) is a discipline distinct from algorithm selection. This zone is 800-57 territory; primitives are 800-131A.
- **AWS KMS envelope encryption** — DEK-encrypted-by-KEK is canonically key management, not a primitive.
- **Google Tink** — keysets, rotation, envelope all live in the key-management layer atop primitives.
- **Signal `account-keys` crate** — PIN / Secure Value Recovery (anti-brute-force) is its own crate, separate from primitives and protocol; precedent for splitting the vault out later if it grows (not today).

Sources: https://nvlpubs.nist.gov/nistpubs/specialpublications/nist.sp.800-57pt1r5.pdf ; https://docs.aws.amazon.com/kms/latest/cryptographic-details/client-side-encryption.html ; https://developers.google.com/tink/key-management-overview ; https://github.com/signalapp/libsignal .

## Exit ramps

| Concern | Today | Exit ramp |
|---|---|---|
| Root-key rotation | Not supported (one root per install) | TASK-41 (Phase 5): `rotate()` on `RootKeyManager`, fresh `KeyRegistry.namespace(version)`, walk user data through re-encrypt. |
| Forward secrecy on recovery blob | None (single blob until Fallback wipe) | TASK-41 (same epic). |
| KDF migration (Argon2id → next) | `KdfParams.algorithm` varied; unknown refused (R16) | New `KdfParams.ALGORITHM_*` constant, `RecoveryFlow` branch on read, re-wrap on unwrap. |
| Backup endpoint relocation (Cloudflare → own server) | `BuildConfig.RECOVERY_BACKUP_WORKER_URL` single configurable; `WorkerRecoveryKeyBackup` adapter swap | `server-roadmap.md` SRV-RECOVERY-001. |

## Related

- Umbrella + zone map: [`crypto.md`](crypto.md). Primitives: [`crypto-primitives.md`](crypto-primitives.md). Pairing: [`crypto-pairing.md`](crypto-pairing.md). Versioning: [`wire-format.md`](wire-format.md).
- Developer port→impl mapping + "how to add a purpose": [`../dev/key-hierarchy.md`](../dev/key-hierarchy.md).
- Owner-facing recovery walkthrough: [`../recovery-flow.md`](../recovery-flow.md).
