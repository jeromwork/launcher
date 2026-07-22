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

**Vault & recovery — architected below from industry standard, NOT built (0 code)**: the key-vault boundary (§Key vault — operation-on-vault, Apple SecureEnclave / AWS KMS / WebAuthn consensus) and recovery anti-brute-force (§Recovery anti-brute-force — Signal SVR / Apple HSM / OPAQUE / Argon2 / SSS landscape) are now complete in this file. Residual **product** choice (memorable PIN vs high-entropy recovery code) → `mentor`. Owners (history): TASK-112 (vault — its decision matches the researched standard), TASK-59 (recovery), TASK-21 (2FA escrow), TASK-39 (social).

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

## Key vault — the operation-on-vault boundary (architected from industry standard; NOT built)

**Grounded in the researched industry consensus** (Apple CryptoKit SecureEnclave, AWS KMS/HSM, WebAuthn/passkeys, Android Keystore) — synthesised, not re-derived. The vault is the boundary through which the app **operates on keys without the raw private key crossing into app memory when hardware allows**.

**The standard contract (one-directional consensus)**: **operation-on-vault** — the vault exposes *operations* (`sign` / `agree` / `wrap` / `unwrap` inside), never *"get the private key"*. Keys are **non-exportable by default**; the only bytes that may leave are (a) public keys, (b) signatures / shared-secrets, (c) **wrapped DEKs via a narrow, explicit, audited export hatch** (the envelope pattern — AWS KMS `GenerateDataKey`). This is exactly [TASK-112]'s decided shape (operation-on-vault + narrow `exportDerivedKey` hatch + newtype-per-object) — **the research confirms it is the industry standard**, so it stays.

**The unavoidable constraint (do NOT design around it)**: **no mainstream secure element supports Curve25519 — P-256 only** (Android StrongBox/TEE, Apple Secure Enclave). Since our messaging crypto standardises on Ed25519/X25519 ([`crypto-primitives.md`](crypto-primitives.md)), true "raw key never in memory" (U7) is **unattainable in hardware for the identity key today**. The industry fallback (Signal / Bitwarden / Threema): generate an **AES-GCM key inside the secure element and use it to seal the raw Ed25519/X25519 private key at rest** (`SecureKeyStore`, [`crypto-primitives.md`](crypto-primitives.md) P2); the raw curve key is unsealed into memory only for the operation. Hardware-native (operation-on-vault) for P-256; **wrapped-raw** for our curves.

**Port shape (the boundary)**:
- `KeyVaultPort` exposes operations, typed handles, **no `exportPrivate()`**. Methods: `generateSigningKey(): SigningKeyHandle`, `sign(handle, msg)`, `generateAgreementKey(): AgreementKeyHandle`, `agree(handle, peerPub): SharedSecret`, `wrap(handle, dek)`, `unwrap(handle, wrapped)`, and a *separate, audited* `exportWrappedDataKey()` limited to derived DEKs only.
- **Typed keys (the CryptoKit lesson, ported)**: `SigningKeyHandle` ≠ `AgreementKeyHandle` as distinct types, so "sign with an agreement key" is unrepresentable — carries Apple's compile-time discipline to Kotlin/Rust even where Android doesn't enforce it (K-invariant; [`crypto-primitives.md`](crypto-primitives.md) P4).
- **Surface the capability level, do NOT hide it**: each adapter reports `STRONGBOX` / `TRUSTED_ENVIRONMENT` / `SOFTWARE_WRAPPED` (mirrors Android `KeyInfo.getSecurityLevel()`). The domain learns *how strongly* a key is held, because it changes the theft-resistance guarantee.

**Build-vs-buy**: 🟢 Android Keystore + Apple CryptoKit (platform-native backends); Google **Tink** (Apache-2.0 — the key-management/envelope vocabulary, portable); **libsodium** (ISC — the raw Ed25519/X25519 primitives the wrapped-raw path needs). 🟡 the `KeyVaultPort` + per-platform adapters (expect/actual or Rust-FFI) — thin glue. Cross-platform: Android now → iOS (Keychain/SecureEnclave) → future Rust `vault-rs` swap behind the port, zero domain change.

## Recovery anti-brute-force — the landscape + our MVP choice (architected; NOT built)

**The load-bearing truth (researched, do NOT wish it away)**: bounding guesses of a **low-entropy** secret (a PIN) against a **server-database dump** requires **either trusted hardware (enclave/HSM) OR split trust (threshold-OPRF / secret-sharing)**. A single untrusted server (our Cloudflare Worker) or a client-only KDF **cannot** provide it — they only make guessing *slower*, not *bounded*.

**The landscape (industry standard, each with its honest limit)**:

| Approach | Bounds guesses vs a server dump? | Needs trusted HW? | Fit for our no-HSM MVP |
|---|---|---|---|
| **SVR / HSM** (Signal SVR3 SGX/Nitro/SEV; Apple iCloud 10-tries-then-destroy; WhatsApp Backup Vault) | ✅ counter + self-destruct in hardware | ✅ | ❌ we have no HSM yet |
| **OPAQUE single server** (RFC 9807) | ❌ a dumped/corrupt server can offline-attack (RFC's own admission) | ❌ | ⚠️ doesn't meet the threat for a PIN |
| **Threshold-OPRF / distributed OPAQUE** | ✅ if OPRF key split across independent operators | ❌ | ⚠️ needs a 2nd independent trust domain |
| **Argon2id client-only** | ❌ just slow; unbounded offline attempts | ❌ | ✅ trivial, but honest ONLY for high-entropy secrets |
| **Shamir Secret Sharing / social** | ✅ no single guessable point | ❌ | ✅ server-free (recovers a *strong* secret) |

**Our MVP choice (no own HSM, zero-knowledge, Worker stopgap)** — the industry-honest path: **client-side Argon2id over a HIGH-ENTROPY recovery secret** (a generated recovery code / diceware phrase — the WhatsApp "64-digit key" move), **optionally Shamir-split** across the user's own devices / chosen custodians. This **sidesteps the low-entropy problem entirely** (nothing weak to brute-force), is fully zero-knowledge, server-free for the crypto, and permissively licensed. Argon2id is already our `PasswordHash` primitive.

**Invariant (do NOT violate)**: **never ship single-Worker PIN recovery and claim dump-resistance.** A single untrusted server's *online* rate-limit evaporates the instant its state is dumped — the exact threat we defend. If the product later demands a *memorable PIN*, it needs split-trust or trusted hardware; say so explicitly.

**Exit ramp (versioned `VaultRecoveryPort`, additive)**: MVP = Argon2id-client (+ optional SSS) → next = **threshold-OPRF across two operators** (`opaque-ke`, Rust MIT/Apache, NCC-audited) once a 2nd trust domain exists → final = **SVR-style enclave / HSM** (design-copy Signal SVR3, AGPL code = reference only; or rent cloud HSMs à la Apple/WhatsApp) once we run our own Rust server. The client only ever "blinds the secret → unwraps the envelope", so swapping adapters is additive, not a rewrite (rule 8).

**Residual PRODUCT choice → `mentor` (research can't answer this — it's segment policy)**: does the product **require a memorable PIN** (family convenience → but then needs split-trust/HW to be dump-resistant), or **accept a high-entropy recovery code** (Argon2-client suffices, MVP-clean)? Likely a **preset field** (family default = recovery code; clinic may mandate PIN + HSM at Phase-3+). This supersedes TASK-59's incomplete "SVR vs OPAQUE vs HMAC" framing (HMAC is not a real option; single-server OPAQUE does not bound a PIN — the research resolves it).

**Ready libs (permissive)**: `opaque-ke` (Rust, **MIT/Apache**, NCC-audited 2021 for WhatsApp); Argon2id via **libsodium** (ISC); Shamir SSS (permissive crates, unpatented). Signal SVR2/SVR3 = **AGPL + enclave-bound → design-copy only**.

## Industry grounding

- **NIST SP 800-57 Pt 1 Rev 5** — key management (generation, derivation, distribution, rotation, revocation, escrow, recovery, key states) is a discipline distinct from algorithm selection. This zone is 800-57 territory; primitives are 800-131A.
- **AWS KMS envelope encryption** — DEK-encrypted-by-KEK is canonically key management, not a primitive.
- **Google Tink** — keysets, rotation, envelope all live in the key-management layer atop primitives.
- **Signal `account-keys` crate** — PIN / Secure Value Recovery (anti-brute-force) is its own crate, separate from primitives and protocol; precedent for splitting the vault out later if it grows (not today).

Sources: https://nvlpubs.nist.gov/nistpubs/specialpublications/nist.sp.800-57pt1r5.pdf ; https://docs.aws.amazon.com/kms/latest/cryptographic-details/client-side-encryption.html ; https://developers.google.com/tink/key-management-overview ; https://github.com/signalapp/libsignal .

**Vault + recovery sources** (industry standard the two sections above are built on): Android Keystore https://developer.android.com/privacy-and-security/keystore ; Apple SecureEnclave https://developer.apple.com/documentation/cryptokit/secureenclave ; AWS KMS envelope https://docs.aws.amazon.com/kms/latest/cryptographic-details/generating-data-keys.html ; WebAuthn/passkeys https://www.webauthn.me/passkeys ; Tink (Apache-2.0) https://github.com/tink-crypto/tink ; Signal SVR3 https://eprint.iacr.org/2024/887.pdf ; Apple iCloud Keychain escrow https://support.apple.com/guide/security/escrow-security-for-icloud-keychain-sec3e341e75d/web ; WhatsApp E2E backup https://www.whatsapp.com/security/WhatsApp_Security_Encrypted_Backups_Whitepaper.pdf ; OPAQUE RFC 9807 https://www.rfc-editor.org/rfc/rfc9807.html + opaque-ke (MIT/Apache) https://github.com/facebook/opaque-ke ; Argon2/libsodium (ISC) https://github.com/jedisct1/libsodium .

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
