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
        │  HKDF-SHA256  (ikm=RootKey, salt=stableId, info=purpose, len=32)   ── KeyRegistry.derive mechanism (built)
        ├──► DerivedKey(MLS_SIGNATURE)   — openmls signature key via KeyVault.exportDerivedKey (future — TASK-124)
        └──► DerivedKey(NOISE_STATIC)    — snow Noise handshake key via KeyVault.exportDerivedKey (future — TASK-67)

   NOTE: config is NOT purpose-derived — it uses a random-CEK hybrid envelope
   (ConfigCipher2, random CEK + crypto_box_seal per recipient); recovery uses an
   Argon2-from-passphrase wrap key (RecoveryFlow), not RootKey HKDF. So the derive
   mechanism has NO boundary-2 consumer today — MLS_SIGNATURE/NOISE_STATIC are its
   first (future) consumers via the export hatch. (Corrected 2026-07-22 — earlier
   "DerivedKey(config/contacts/media)" was a phantom with no call site.)
```

**Ports** (built): `RootKeyManager`, `KeyRegistry` (HKDF derive), `DeviceKeyNamespaceProvider`, `RecoveryKeyBackup`, `PassphrasePrompter`, `PassphraseAttemptCounter`, `SchemaVersionMemory`, `AuthAvailability` — all in `:core:keys` commonMain. Envelope: `ConfigCipher2` (interface, `family.keys.api.internal`) + `EnvelopeConfigCipherImpl` + `Envelope` value. Primitive ports it consumes (`KeyDerivation`, `PasswordHash`=Argon2id, `AeadCipher`, `SecureKeyStore`) live in `:core:crypto`.

**Invariants** (K1–K5, do NOT re-derive — see §Invariants): K1 one RootKey per identity per install. K2 purpose is an HKDF `info` string, stable across all devices. K3 envelope depends on primitives only, never reaches into `RootKeyManager` internally. K4 recovery vault is identity-bound — never shareable (rule 9 does not apply). K5 no version/serialization in the value types (rule 1 crypto exception); wire shapes live in adapter DTOs.

**Vault & recovery — architected below from industry standard, NOT built (0 code)**: the key-vault boundaries (§Key vault — **three boundaries** split by operation-kind per AWS KMS `KeyUsage` / Tink / OpenMLS consensus; only boundary 2 `KeyVault` is a new port, boundaries 1+3 = already-built `AsymmetricCrypto` + `SecureKeyStore`) and recovery anti-brute-force (§Recovery anti-brute-force — Signal SVR / Apple HSM / OPAQUE / Argon2 / SSS landscape) are complete in this file. Residual **product** choice (memorable PIN vs high-entropy recovery code) → `mentor`. Owners (history): TASK-112 (`KeyVault` = data-key ops; deep-research 2026-07-22 confirmed the three-boundary split, retired the earlier god-port), TASK-59 (recovery), TASK-21 (2FA escrow), TASK-39 (social).

**Rejected**: root-key rotation now (TASK-41, Phase 5); forward secrecy on the recovery blob now (same). See §Rejected/Exit ramps.

**Status**: BUILT (TASK-4 envelope, TASK-6 root key + recovery, TASK-66 bucket registry Done).

**Routing**: key/envelope/recovery question → stay here. Algorithm details → [`crypto-primitives.md`](crypto-primitives.md). Versioning → [`wire-format.md`](wire-format.md). Pairing → [`crypto-pairing.md`](crypto-pairing.md).

<!-- AI-TLDR:END -->

## Invariants (decided — do NOT re-derive; changing one is a `decision-supersedes` task)

- **K1 — one RootKey per identity for the install lifetime.** No rotation today (single root); rotation is TASK-41, additive.
- **K2 — purpose = stable HKDF `info` label, modelled as a closed enum + governed escape hatch.** The label value must be identical across every device of every install, or derived keys diverge. The `KeyVault` port surfaces purposes as `enum Purpose { MLS_SIGNATURE, NOISE_STATIC }` (compile-time exhaustive; ONLY the real export consumers — see §Key vault); the enum name maps to a stable `info` byte-string internally. Industry model (MLS `HKDF-Expand-Label`: fixed in-spec labels + IANA registry): add a `Purpose.External(labelBytes)` / new variant additively when a new consumer appears — never an enum→free-string rewrite. Adding a purpose = new enum variant + stable label + collision/idempotence test. (Stale `"config"`/`"contacts"`/`"media"` examples removed 2026-07-22 — config is a random-CEK envelope, not purpose-derived; contacts/media are Profile buckets, not vault purposes.)
- **K3 — envelope encryption depends on primitives only.** `ConfigCipher2`/`EnvelopeConfigCipherImpl` takes plaintext + recipient pubkeys + AAD and produces an `Envelope` using AEAD + `crypto_box_seal` per recipient. It does **not** read `RootKeyManager`/`KeyRegistry` internally — those are orchestrated above it (`DefaultEnvelopeBootstrap`, `RemoteStorage`). Envelope is key-management (DEK-under-KEK, AWS KMS pattern), sitting on primitives.
- **K4 — recovery vault is identity-bound, never shareable.** Passphrase + `RecoveryKeyBackupBlob` are secrets bound to one identity; rule 9 (shareability) explicitly does not apply.
- **K5 — value types carry no version, no serialization.** `Envelope`, `RecoveryKeyBackupBlob` are plain classes (rule 1 crypto exception, TASK-141). Their wire shapes live in adapter DTOs (`RecoveryBlobJsonCodec` in `:app`, Firestore adapters) governed by [`wire-format.md`](wire-format.md).

## Rotation / escrow (stubs today)

`KeyRotation` and `KeyEscrow` are **declared ports with stub implementations** (`StubKeyRotation` throws `NotImplementedError`). Placement of the port declarations in `:core:crypto` is acceptable — they are primitive-adjacent declarations; the *policy* (when to rotate, escrow semantics) would live in this layer when built. Real rotation = TASK-41 (Phase 5); server-side escrow tracked as SRV-CRYPTO-002 in `../dev/server-roadmap.md`. KDF-algorithm migration (Argon2id → next) already supported: `KdfParams.algorithm` is varied and `RecoveryFlow` refuses unknown algorithms (R16 guard), re-wrapping on successful unwrap.

## Key vault — the operation boundaries (architected from industry standard; the `KeyVault` port NOT built)

**Grounded in verified industry consensus** (AWS KMS `KeyUsage`, Apple CryptoKit SecureEnclave, Google Tink primitive interfaces, OpenMLS `OpenMlsProvider` trait split, Azure Key Vault `key_ops`, age) — synthesised, not re-derived. See §Industry grounding for the source-backed claims (deep-research 2026-07-22, adversarially verified).

**The load-bearing finding: "vault" is NOT one port — the industry splits it into three boundaries by operation-kind, and forbids fusing them.** AWS KMS makes this structural: a key carries an immutable `KeyUsage` (`SIGN_VERIFY` / `ENCRYPT_DECRYPT` / `KEY_AGREEMENT` / `GENERATE_VERIFY_MAC`), wrong-usage requests are rejected (`InvalidKeyUsageException`), and an asymmetric key **cannot** wrap a data key. Tink splits `KeysetHandle` into distinct per-primitive interfaces (`Aead`, `Mac`, `PublicKeySign`/`Verify`). Apple types `SecureEnclave.P256.Signing` ≠ `KeyAgreement` ≠ `SymmetricKey`. OpenMLS separates `OpenMlsCrypto` (operations) from `StorageProvider` (key-at-rest). Even Android's unified KeyMint HAL still splits by JCA class (`Signature`/`Cipher`/`Mac`) at the API layer — it is not a true one-port counterexample.

### The three boundaries (and which we already own)

| # | Boundary | Operations | Shape | Our port | Status |
|---|---|---|---|---|---|
| **1** | **Identity-key operations** (asymmetric) | `sign`, key-`agree` | operation-on-vault + typed handles, **no private export** | `AsymmetricCrypto` ([`crypto-primitives.md`](crypto-primitives.md)) | **built** |
| **2** | **Data-key operations** (symmetric) | AEAD `seal`/`open`, `mac`, narrow `exportDerivedKey` | operation-on-vault + **narrow, audited** export hatch | **`KeyVault`** (TASK-112) | **NOT built** |
| **3** | **Key-at-rest / capability** | wrap/unwrap raw key material; report security level | placement, not algorithm — a **sibling** port | `SecureKeyStore` ([`crypto-primitives.md`](crypto-primitives.md) P2) | **built** |

**`KeyVault` (this zone's only new port) = boundary 2 only** — symmetric data-key operations over HKDF-derived purpose keys. **MVP shape (this is the current truth — read it here, not in the task):**

```kotlin
package family.keys.api

interface KeyVault {   // boundary 2, MVP = the narrow export hatch ONLY
    // Narrow, audited export hatch — for external Rust libs (openmls signature key,
    // snow Noise static) that manage raw material themselves. Purpose-whitelisted.
    @Throws(VaultException::class) fun exportDerivedKey(purpose: Purpose, context: ByteArray, length: Int): DerivedKeyBytes
}

enum class Purpose { MLS_SIGNATURE, NOISE_STATIC }   // K2 — ONLY the real export consumers; External(labelBytes) added additively when a new consumer appears
sealed class VaultException(message: String, cause: Throwable? = null) : Exception(message, cause)   // categories: hardware, user-action, data-integrity, programming-error
class DerivedKeyBytes(val bytes: ByteArray, val purpose: Purpose) : AutoCloseable { override fun close() { bytes.fill(0) } }
```

Method is **sync** (every reference lib is sync; FFI-friendly). This is the OpenMLS `export_secret` / AWS KMS `GenerateDataKey` narrow-hatch shape.

**Deferred additively (NOT built — no consumer today, rule 4 MVA)**: `aeadSeal(purpose, plaintext, aad) → …`, `aeadOpen(…)`, `mac(purpose, message) → …` and their value types were the original Decision shape, but **no boundary-2 AEAD/MAC consumer exists in code** — config is a random-CEK hybrid envelope (`ConfigCipher2`, not purpose-derived) and recovery is Argon2-from-passphrase (`RecoveryFlow`, not RootKey-HKDF). These operations are added when a real consumer appears (Tink `Aead` shape), not ahead of time. A `Ciphertext` type is deliberately NOT introduced (avoids colliding with the built `family.crypto.api.values.Ciphertext`).

**Build timing**: the port contract (interface + fake) may land early, but the production adapter is written **with the first consumer** — TASK-67 (`NOISE_STATIC`) or TASK-124 (`MLS_SIGNATURE`), both Draft — never ahead of one. Decision owner/history: [TASK-112](../../backlog/tasks/task-112%20-%20Decision-Cross-platform-IdentityVault.md) (the *why* + alternatives + exit ramps live in its Decision block; the *what/how* is here).

> ⚠️ SUPERSEDED (2026-07-22): an earlier version of this section described a single `KeyVaultPort` with `generateSigningKey/sign/agree/wrap/unwrap` + typed handles + `capability level`. That shape **conflated boundaries 1 and 3 into one port and duplicated already-built ports** — its `sign`/`agree` are `AsymmetricCrypto` (boundary 1), its `wrap`/`unwrap` + capability level are `SecureKeyStore` (boundary 3, P2). The deep-research 2026-07-22 confirmed the three-boundary split (§Industry grounding); the god-port is retired — its operations already exist as `AsymmetricCrypto` + `SecureKeyStore` ([`crypto-primitives.md`](crypto-primitives.md)).

**Capability / security level belongs to boundary 3, NOT to `KeyVault`.** `STRONGBOX` / `TRUSTED_ENVIRONMENT` / `SOFTWARE` (`KeyInfo.getSecurityLevel()`) is a property of *where a stored key resides* and is **attestable only for asymmetric keys** (Android attestation requires RSA/EC/ML_DSA; AES/HMAC throw `InvalidAlgorithmParameterException`). HKDF-derived symmetric purpose-keys are always software-floor and never attested — a security-level field on `KeyVault` would be meaningless. It lives on `SecureKeyStore`.

**Version lives in cleartext framing ABOVE the primitive, never inside it** (K5, [`wire-format.md`](wire-format.md)). Research unanimous: age (`age-encryption.org/v1` header line), JWE RFC 7516 (Protected Header as AAD), Bitwarden EncString (`encType` cleartext prefix), MLS RFC 9420 (`WireFormat`/`protocol_version` authenticated-not-encrypted) all keep the version discriminator readable *before* decryption. This constrains the **deferred** boundary-2 AEAD types (when built) and the existing `Envelope` adapter DTOs alike: any ciphertext version is a cleartext prefix parsed by the adapter DTO, never a field the crypto primitive reads — consistent with the rule-1 crypto exception (TASK-141). (The MVP export hatch returns raw key bytes, carries no wire format, so this does not apply to it.)

**Purpose labels = closed enum for real consumers + governed escape hatch** (MLS `HKDF-Expand-Label` model: fixed in-spec labels + IANA exporter-label registry). Our `Purpose` enum lists ONLY the export consumers (2 variants: `MLS_SIGNATURE`, `NOISE_STATIC`); add a `Purpose.External(labelBytes)` / new variant additively when a new consumer appears, never an enum→free-string rewrite.

**The unavoidable constraint (boundary 1/3, do NOT design around it)**: **no mainstream secure element supports Curve25519 — P-256 only** (Android StrongBox/TEE, Apple Secure Enclave). Since our crypto standardises on Ed25519/X25519 ([`crypto-primitives.md`](crypto-primitives.md)), true "raw key never in memory" is unattainable in hardware for the identity key today. Industry fallback (Signal / Bitwarden / Threema): generate an AES-GCM key **inside** the secure element and use it to seal the raw Ed25519/X25519 private key at rest (`SecureKeyStore`, P2); the raw curve key is unsealed into memory only for the operation.

**Build-vs-buy**: 🟢 boundaries 1+3 already built on Android Keystore + libsodium; iOS = Keychain/SecureEnclave adapters (V-1). Google **Tink** (Apache-2.0) — the key-management/envelope *vocabulary*. **OpenMLS `OpenMlsProvider = OpenMlsCrypto + OpenMlsRand + StorageProvider`** (Apache-2.0/MIT) — take this trait split verbatim across the Rust-FFI border for the MLS path. 🟡 the `KeyVault` port (boundary 2) + per-platform adapters — thin glue over the built primitive ports; future Rust swap behind the port, zero domain change.

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

**Three-boundary split (deep-research 2026-07-22, adversarially verified — the source-backed basis for §Key vault above)**:
- **AWS KMS** — per-key immutable `KeyUsage` enum (`SIGN_VERIFY` / `ENCRYPT_DECRYPT` / `KEY_AGREEMENT` / `GENERATE_VERIFY_MAC`); wrong-usage → `InvalidKeyUsageException`; asymmetric key cannot wrap a data key. `GenerateDataKey` = the narrow export hatch scoped to symmetric data keys. Split by operation-kind, enforced at request time. (verified 6–8×)
- **Google Tink** — `KeysetHandle` yields distinct per-primitive interfaces (`Aead` / `Mac` / `PublicKeySign`/`Verify` / `HybridEncrypt`); raw-byte export gated behind an explicit `InsecureSecretKeyAccess` token. (verified ~10×)
- **Apple CryptoKit** — `SecureEnclave.P256.Signing` ≠ `KeyAgreement` ≠ `SymmetricKey` as distinct types; SE is P256-only, no symmetric AEAD keys.
- **Android** — attestation & `getSecurityLevel()` (STRONGBOX/TEE/SOFTWARE) are **asymmetric-only** (AES/HMAC throw `InvalidAlgorithmParameterException`); "one uniform KeyMint port" claim was **refuted** — JCA still splits `Signature`/`Cipher`/`Mac`. Capability level is a per-key key-at-rest property, not an operation property.
- **OpenMLS** (Apache-2.0/MIT) — `OpenMlsProvider = OpenMlsCrypto` (operations) `+ OpenMlsRand + StorageProvider` (key-at-rest, replaced the old `OpenMlsKeyStore`). Operations and storage are separate boundaries. `export_secret` = narrow typed-byte export hatch. Take this trait split verbatim across the Rust-FFI border.
- **Version placement** — age / JWE (RFC 7516) / Bitwarden EncString / MLS (RFC 9420) unanimously keep the version discriminator in cleartext framing above the primitive (authenticated-not-encrypted). Confirms K5 + TASK-141.

Sources: https://nvlpubs.nist.gov/nistpubs/specialpublications/nist.sp.800-57pt1r5.pdf ; https://docs.aws.amazon.com/kms/latest/APIReference/API_GenerateDataKey.html ; https://developers.google.com/tink/key-management-overview ; https://github.com/tink-crypto/tink ; https://book.openmls.tech/traits/traits.html ; https://developer.apple.com/documentation/cryptokit/secureenclave ; https://source.android.com/docs/security/features/keystore/attestation ; https://www.rfc-editor.org/rfc/rfc7516 ; https://github.com/signalapp/libsignal .

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
