# Crypto — MLS core & KeyPackage (`:crypto-ffi` + `family.crypto.mls`)

**This file is the single source of truth for the MLS group-protocol layer** — the group crypto (TreeKEM/epochs), the KeyPackage format + directory, the encrypted keystore, and the FFI bridge. It is grounded in the **researched internal architecture of complete MLS projects** (openmls crates, Wire `core-crypto` module structure, matrix-sdk FFI, RFC 9750 + Signal prekey semantics) — **not** re-derived from our own past mentor sessions. Our product constraints (zero-knowledge server, family presets) are laid **on top** and marked as ours. If it and any other doc disagree on MLS, this file wins — except: primitives are [`crypto-primitives.md`](crypto-primitives.md), key hierarchy [`crypto-key-hierarchy.md`](crypto-key-hierarchy.md), the pairing/AS (who-may-add) [`crypto-pairing.md`](crypto-pairing.md), the KeyPackage *server deployment* [`messaging-delivery.md`](messaging-delivery.md), versioning [`wire-format.md`](wire-format.md), the umbrella [`crypto.md`](crypto.md). Change the model → update this file in the same commit.

<!-- AI-TLDR:BEGIN — READ THIS FIRST. If you can answer from this block, STOP. -->

## AI TL;DR

**THE BEACON (do NOT re-decide)**: the MLS core is **imported ready code (openmls, MIT) wrapped by a thin, clean-roomed layer whose STRUCTURE is copied from Wire `core-crypto` (GPL — architecture only, never the code)**, bridged to Kotlin via **UniFFI** (dumb bridge, no crypto logic). We do **not** write MLS, crypto, or bindings — we import them; we write four thin things (FFI verb surface, group wrapper, SQLCipher storage mapping, KeyPackage directory server). MLS gives **post-compromise security** via epoch re-key (the researched reason it beats Sender Keys). This is the **openmls's own Rust primitives**, NOT our Kotlin libsodium — two primitive stacks across the FFI border, exactly the Wire shape ([`crypto-primitives.md`](crypto-primitives.md)).

**The layered stack — what to IMPORT vs COPY-DESIGN vs WRITE** (grounded in openmls + Wire + Matrix):

| # | Layer | Verdict |
|---|---|---|
| 1 | Domain ports (`GroupPort`/`KeyPackagePort`/`CryptoPort`, Kotlin, no openmls types leak up) | **WRITE** thin; design-from Wire `CoreCrypto`/`Conversation` API shape |
| 2 | UniFFI-generated Kotlin bindings (dumb bridge) | **IMPORT** `uniffi`/`uniffi_macros` (MIT/Apache) + `cargo-ndk`; ref matrix-sdk-ffi (Apache) |
| 3 | Rust FFI crate `:crypto-ffi` (`#[uniffi::export]` ~10-20 verbs) | **WRITE** thin; ref matrix-sdk-ffi (Apache, readable) + Wire core-crypto-ffi (GPL, shape) |
| 4 | MLS wrapper: `Conversation = MlsGroup` + a `TransactionContext` for atomic mutations | **WRITE** thin; **copy-design Wire `core-crypto`** (GPL) — drop Proteus + e2e_identity, MLS-only |
| 5 | openmls protocol engine (`MlsGroup`, `KeyPackageBuilder`, proposals/commits) | **IMPORT `openmls` — MIT** |
| 6 | Crypto + Rand provider (`OpenMlsCrypto`+`OpenMlsRand`) | **IMPORT `openmls_rust_crypto` — MIT** (or `openmls_libcrux_crypto` for PQ) |
| 7 | `StorageProvider` → **SQLCipher** (encrypted at rest) | **IMPORT** trait scaffold `openmls_sqlite_storage` (MIT) + SQLCipher (BSD) + rusqlite (MIT); **WRITE** thin mapping; copy-design Wire `keystore`+`mls-provider` (GPL) |
| 8 | KeyPackage directory server (publish / atomic one-time claim / last-resort / rate limit) | **WRITE** ~100-300 lines; **copy-design RFC 9750 §5.1 + Signal prekey drain-defense** — no permissive turnkey server exists |

**openmls contract (the ready code)**: `OpenMlsProvider` composes `CryptoProvider` + `RandProvider` + **`StorageProvider`** (trait, `const VERSION`, persists the *whole* group state — tree/secrets/keys/proposals). `MlsGroup`: `new`/builder, `add_members`, `remove_members`, `self_update`, `commit_to_pending_proposals`, `merge_pending_commit`, `process_message`→`ProcessedMessage`/`StagedCommit`. `KeyPackageBuilder…build()` with a **`.last_resort()`** flag (RFC 9750 first-class). Cipher suite (MTI) = `MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519` (also ChaCha20 variant; PQ X25519+ML-KEM via libcrux).

**OUR product constraints laid on top (marked as ours, NOT universal)**:
- **Zero-knowledge KeyPackage server (rule 13)** — the directory stores each KeyPackage as an **opaque blob keyed by an opaque ID**; the server never parses MLS internals (Tier-1 minimal-directory, the one elevation RFC-MLS forces). Deployment: [`messaging-delivery.md`](messaging-delivery.md).
- **SQLCipher key from OUR key hierarchy** — hand SQLCipher a raw 32-byte key derived in our audited key layer (Argon2id/HKDF from the root key, [`crypto-key-hierarchy.md`](crypto-key-hierarchy.md)), bypassing SQLCipher's own PBKDF2 — DB-key derivation stays inside our layer.
- **Family preset fields** (our policy, differ per segment — rule 11): `poolCap` (100), `claimDedupTTLSeconds` (600), `lastResortRotationDays` (7), `refillThreshold` (20). Clinic/B2B override at Phase-3+. The *mechanism* (pool + one-time claim + last-resort + coarse rate limit) is researched (RFC 9750 + Signal); these *numbers* are ours.
- **Who-may-add/remove** is application policy in [`crypto-pairing.md`](crypto-pairing.md) (RFC 9750 §3.5), NOT the MLS core.

**Invariants** (ML1–ML6, see §Invariants). **Status**: designed, not built. FFI foundation (hello/panics smoke) done (TASK-122); the group wrapper + storage + KeyPackage server are 0 code.

**Routing**: MLS group / KeyPackage / FFI / keystore → stay here. Primitives (libsodium) → [`crypto-primitives.md`](crypto-primitives.md). Root key / SQLCipher-key derivation → [`crypto-key-hierarchy.md`](crypto-key-hierarchy.md). Who-may-add / revoke → [`crypto-pairing.md`](crypto-pairing.md). KeyPackage server deployment / endpoints → [`messaging-delivery.md`](messaging-delivery.md). Versioning → [`wire-format.md`](wire-format.md).

<!-- AI-TLDR:END -->

## Invariants (decided — do NOT re-derive; changing one is a `decision-supersedes` task)

- **ML1 — import the crypto core, write only glue.** openmls (MIT) + its provider/storage/crypto crates + UniFFI are imported; we write the FFI verb surface, the group wrapper, the SQLCipher mapping, and the KeyPackage server. If you find yourself reimplementing TreeKEM, commits, or ciphersuites — stop, openmls has it.
- **ML2 — the FFI bridge is dumb** (libsignal/Wire): no crypto logic, no policy in the binding; it marshals types only. Panics contract guarded by skill [`crypto-ffi-panic-check`](../../.claude/skills/crypto-ffi-panic-check/SKILL.md).
- **ML3 — the StorageProvider persists the whole group state, encrypted at rest via SQLCipher**, keyed by a raw key from our key hierarchy (not SQLCipher PBKDF2). Structure copied from Wire `keystore`+`mls-provider` (GPL — clean-room).
- **ML4 — the KeyPackage directory is a zero-knowledge server (rule 13)**: opaque blob + opaque ID, one-time claim enforced server-side (atomic pop), last-resort fallback when the pool is empty, coarse rate limit against drain (Signal pattern). It never parses MLS. Deployment in [`messaging-delivery.md`](messaging-delivery.md).
- **ML5 — MLS runs on openmls's own Rust primitives, NOT our libsodium** (two stacks across the FFI border — the industry shape, Wire core-crypto). Do not write a custom `OpenMlsCrypto` on libsodium to "unify" ([`crypto-primitives.md`](crypto-primitives.md) §Rejected).
- **ML6 — authorization (who may add/remove) is application policy, not the MLS core** (RFC 9750 §3.5) — owned by [`crypto-pairing.md`](crypto-pairing.md) (TASK-102). MLS executes the crypto add/remove; it does not decide who may.

## KeyPackage lifecycle — the researched architecture (RFC 9750 + Signal)

**The library** (openmls) builds/validates KeyPackages and flags last-resort; **it does not distribute them**. The directory is a server we write, following the normative RFC + Signal's drain-defense (no permissive turnkey server exists — Wire backend / Phoenix are AGPL, copy-design only):

1. **Publish** — client uploads a pool of one-time KeyPackages + one last-resort (`POST /v1/keypackage/publish`), enforcing our `poolCap`; overflow dropped (`stored/dropped`), never 429 on publish.
2. **Claim** — `POST /v1/keypackage/claim` atomically pops **one** one-time KeyPackage (DB transaction / conditional delete); **one-time-use enforced server-side** (RFC 9750 §5.1). Idempotent within `claimDedupTTLSeconds` keyed by `(requester_id, target_id)`.
3. **Last-resort fallback** — when the one-time pool is empty, return the reusable last-resort (`is_last_resort=true`) rather than failing (RFC 9750). Weakened FS on that single handshake, bounded by `lastResortRotationDays`.
4. **Coarse rate limit** — per-claimer/target window against drain (Signal X3DH: "rate limits on fetching prekey bundles"). Lives at the edge (rules 12/13 baseline), not KeyPackage-specific code.
5. **Client-driven refill** — device publishes a new batch when local count < `refillThreshold` (no server-push scheduling).

Ports (domain, Kotlin, no MLS types leak): `KeyPackagePool` (opaque `KeyPackageId`), `LastResortKeyManager` (opaque `LastResortKey`); result types are sealed classes, HTTP codes never leak into domain.

## Industry grounding (the researched prior art this file is built on)

- **openmls** (MIT) — the imported engine: provider pattern, `StorageProvider` trait, `MlsGroup`, `KeyPackageBuilder.last_resort()`, shipped `openmls_rust_crypto` / `openmls_sqlite_storage` / `openmls_libcrux_crypto` (PQ). https://book.openmls.tech/ · https://docs.rs/openmls · https://blog.openmls.tech/posts/2024-09-04-v0_6-release/
- **Wire `core-crypto`** (GPL — architecture copied clean-room) — the module structure: `core-crypto` (multiplexer) / `keystore` (SQLCipher) / `mls-provider` (StorageProvider seam) / `core-crypto-ffi` (UniFFI); the `CoreCrypto`/`Conversation`/`TransactionContext` shape. https://wireapp.github.io/core-crypto/core_crypto/
- **matrix-sdk FFI** (Apache-2.0 — readable + importable reference for the FFI mechanics) — UniFFI + cargo-ndk, `cdylib`, AAR packaging. https://matrix-org.github.io/matrix-rust-sdk/matrix_sdk_crypto_ffi/index.html
- **UniFFI** (Mozilla, MIT/Apache) proc-macro mode (no .udl); **cargo-ndk** builds the Android `.so`. https://github.com/mozilla/uniffi-rs
- **SQLCipher** (BSD) via rusqlite/rusqlcipher (MIT) — AES-256 DB encryption. https://docs.rs/crate/rusqlcipher/latest
- **RFC 9750** §5.1 — KeyPackage one-time-use + last-resort (the normative directory rules). https://www.rfc-editor.org/rfc/rfc9750.html
- **Signal X3DH/PQXDH** — prekey drain-defense (rate limits on fetch) = the KeyPackage claim rate-limit. https://signal.org/docs/specifications/x3dh/
- **PCS reason (MLS > Sender Keys)**: MLS epoch re-key self-heals after compromise; Sender Keys do not. Production evidence: Wire (openmls), Discord DAVE (2026), RCS. RFC 9420.

## Rejected (do not re-litigate)

- ❌ **Vendoring Wire `core-crypto` / Kalium (GPL) or matrix-rust-sdk-crypto's MLS-as-a-product** — GPL breaks the commercial model; and matrix-sdk-crypto is Olm/Megolm (not MLS). Import openmls (MIT); copy Wire's *structure* only.
- ❌ **Reimplementing MLS / TreeKEM / ciphersuites** — openmls is the audited, production engine (ML1).
- ❌ **`mls-kotlin`** (JVM-only hobby) / **MLSpp** (C++ FFI worse than Rust for KMP) / **own MLS wire format** (breaks RFC 9420 interop / MIMI).
- ❌ **A custom `OpenMlsCrypto` on libsodium to unify the two primitive stacks** — high-risk, no benefit; keep openmls's Rust primitives (ML5).
- ❌ **Crypto logic in the FFI bridge** — dumb bridge only (ML2).
- ❌ **KeyPackage server that parses MLS / uses `userUid` as a key** — opaque blobs + opaque IDs (ML4, rule 13).

## Exit ramps

- **openmls → mls-rs** (Apache-2.0/MIT, AWS Labs) — RFC 9420 wire-compatible, so interop unaffected; on-disk state is library-specific → ~1-2 weeks adapter rewrite behind `GroupPort`. Trigger: a blocking openmls defect or a licensing/maintenance change.
- **UniFFI → manual JNI** — ~2-3 weeks; the `:crypto-ffi` boundary contract is unchanged for callers.
- **SQLCipher → other encrypted store** — behind the `StorageProvider` impl; the openmls trait is unchanged.
- **KeyPackage server Cloudflare → own Rust** — ~100 lines TS → ~100 lines Rust (axum + Postgres `keypackage_pool` table); client refill scheduler unchanged (rule 8, [`messaging-delivery.md`](messaging-delivery.md)).

## Related

- Umbrella + zone map: [`crypto.md`](crypto.md). Primitives (libsodium, the OTHER stack): [`crypto-primitives.md`](crypto-primitives.md). Key hierarchy (SQLCipher key source): [`crypto-key-hierarchy.md`](crypto-key-hierarchy.md). Pairing/AS (who-may-add, revoke): [`crypto-pairing.md`](crypto-pairing.md). KeyPackage server deployment: [`messaging-delivery.md`](messaging-delivery.md). Versioning: [`wire-format.md`](wire-format.md). FFI panic contract: skill [`crypto-ffi-panic-check`](../../.claude/skills/crypto-ffi-panic-check/SKILL.md).
- FFI foundation task: TASK-122 (done). Implementation chain: TASK-123 (ports) → TASK-124 (openmls) → TASK-125 (SQLCipher).
