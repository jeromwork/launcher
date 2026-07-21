# HANDOFF: Crypto architecture SoT consolidation (Track A)

> **Purpose of this file**: complete, self-contained implementation instructions for an AI session.
> All research is DONE, all owner decisions are TAKEN, all contradictions are RESOLVED.
> The implementer must NOT re-derive, re-research, or re-decide anything in here.
> If something in this file conflicts with another doc — this file reflects the owner's latest
> decisions (2026-07-21) and wins for the scope of this work; fix the other doc via the dedup steps below.
>
> Created 2026-07-21 by an Opus session after: 4-agent repo audit + industry research
> (web) + ground-truth code audit + owner decision session. Owner: g.jeromwork@gmail.com (Russian speaker).
> Russian TL;DR for the owner is at the bottom.

---

## 0. Process preamble — do this BEFORE touching any file

1. **Backlog**: TASK-143 is currently `In Progress` (branch exists, only the task-file itself modified, no code commits). Per CLAUDE.md, only one task may be In Progress.
   - Ask the owner ONE question: "Ставлю TASK-143 на Paused и создаю новый task для crypto-консолидации?" Expected answer: yes (this was pre-agreed).
   - Set TASK-143 → `Paused` with note: «Пауза ради crypto SoT-консолидации. Частичная работа: только правки task-файла (в рабочем дереве). Вернуться к развилке 3 вариантов + замер QR (AC#1)». Edit frontmatter directly (do NOT use `backlog task edit -s Draft` — it breaks IDs; Paused via CLI is fine, Draft is not).
   - Create a new backlog task via `backlog task create 'Crypto architecture SoT consolidation (Track A) + crypto skill + extraction-policy' -s Draft --priority high -l 'crypto,architecture,docs' -m m-1` then set it In Progress. Fill description mentor-style per `.claude/skills/backlog-task-format/SKILL.md`. AC suggestions are in §8 of this file.
2. **Branch**: create a feature branch named `task-<N>-crypto-sot-consolidation` from `main` (where `<N>` = new task id). Do not work on `main` or on the task-143 branch.
3. **Do NOT commit `backlog/tasks/task-143*` changes that were already in the working tree** unless they are your own Paused-status edit. Check `git status` first.
4. This work is **docs + skills only**. ZERO production code changes. Code-boundary fixes discovered during the audit become NEW backlog tasks (listed in §7), not edits in this pass.
5. Checklists are chat-only (ADR-011 §5). Do not create files under `specs/**/checklists/`.
6. When done: run skill `pre-pr-backlog-sync` before `gh pr create`.

---

## 1. Why (context in three sentences)

The owner repeatedly had to re-think architectural decisions with each AI session. The one domain where this STOPPED was ECS: a single consolidated file `docs/architecture/ecs.md` + a thin router skill `.claude/skills/ecs/SKILL.md` made every subsequent task run without re-deriving anything. This handoff replicates that exact pattern for the crypto domain, which is currently a 1302-line file (`docs/architecture/crypto.md`) mixing two stacks of different maturity, tutorials, roadmaps, and stale paths — with no routing skill.

## 2. The etalon recipe (extracted from ecs.md + ecs skill — replicate exactly)

Every new/rewritten architecture file MUST have:

1. **Precedence declaration in sentence one**: "this file wins over any other doc on topic X", with named carve-outs (for crypto files: `wire-format.md` wins on versioning; `server.md` wins on endpoint baseline).
2. **AI-TLDR block** (`<!-- AI-TLDR:BEGIN/END -->`, ~50–80 lines) that front-loads THE ADOPTED APPROACH in bold as a "beacon" ("point agents here; do NOT re-decide"), then: stack/type table, decided invariants (numbered, e.g. C1–C6), rejected list (one line + pointer), open questions each with owning TASK-N, and closes with a **routing table** ("routine question → stop here; topic X → §N; topic Y → other-file.md").
3. **Numbered decided invariants** fenced with "Do NOT re-derive; changing any requires a decision-supersedes task".
4. **Rejected (do not re-litigate)** section with the disqualifying REASON per alternative.
5. **Exit ramps** for one-way doors; open questions name their owning TASK.
6. **Industry grounding** with sources (this handoff §4 supplies them — copy citations from there).
7. **Pure architecture only**: no novice tutorials, no owner explanations, no roadmaps, no implementation sequences, no checklists. Schematic type shapes / mermaid / ASCII diagrams are OK; step-by-step prose walkthroughs are NOT.
8. The companion **skill is a thin router, never a second copy**: wide concrete-noun trigger net + "read AI-TLDR first, do not re-derive" + reading map with section pointers + hard sync rule asserted on both sides (file §"How to change this document" and skill).

## 3. Ground truth of the code (audited 2026-07-21 — trust this, not crypto.md's body)

Modules (`settings.gradle.kts`): `:app, :core, :core:crypto, :core:keys, :core:push, :core:cloud, :core:wire, :crypto-ffi`.

| Module | Namespace | Contains | Deps (fitness-enforced) |
|---|---|---|---|
| `:core:crypto` | `family.crypto` **and** `family.pairing` | primitive ports `AeadCipher, KeyDerivation, PasswordHash, SecureKeyStore (expect/actual), AsymmetricCrypto, RandomSource, KeyBlobStore, KeyEscrow, KeyRotation` + libsodium impls (`family.crypto.libsodium.*`) + stubs + fakes; pairing ports `DeviceIdentityRepository, RecipientResolver, EncryptedMediaStorage` | none (`verifyCryptoIsolation`) |
| `:core:keys` | `family.keys` | `RootKeyManager, KeyRegistry` (+ `RootKeyManagerImpl`, `AndroidKeystoreRegistry`), envelope: `ConfigCipher2` (interface, `family.keys.api.internal`) + `EnvelopeConfigCipherImpl` + `Envelope` value; recovery: `RecoveryKeyBackup, RecoveryKeyBackupBlob, RecoveryError, KdfParams, PassphrasePrompter, PassphraseAttemptCounter, RecoveryFlow, Argon2idPassphraseKdf` | `:core:crypto` only (`verifyKeysIsolation`) |
| `:core:wire` | `family.wire` | `WireVersion, WireVersionHeader` (3-field header + `accessFor()` gate), `WireFormatErrors` | none — leaf (`verifyWireIsolation`) |
| `:crypto-ffi` | `family.launcher.cryptoffi` | Rust crate `crypto_ffi`, UniFFI 0.28 proc-macro (no .udl). `src/lib.rs` = 26 lines: `hello()` + `panics()` only | none |

**Resolved contradictions (facts, not opinions):**
- Native lib is **`libcrypto_ffi.so`**. `libopenmls_ffi.so` does NOT exist. All `openmls` mentions in crypto.md's body (lines ~125, 620, 639, 641) are stale — openmls appears ONLY in docs/backlog, zero code.
- **MLS is 0 % implemented.** No `CryptoPort/GroupPort/KeyPackagePort/KeyPackage/GroupState/TreeKEM` in any `.kt`. Planned in TASK-123/124/125. The FFI module is a toolchain smoke-test (TASK-122 done).
- **`KeyVault`/`IdentityVault` do not exist in code** — TASK-112 is an open Decision. Real hierarchy ports: `RootKeyManager`, `KeyRegistry`, `SecureKeyStore`.
- Post-TASK-141 versioning: crypto SDK types carry NO version/serialization (`Envelope`, `RecoveryKeyBackupBlob` are plain classes). Version headers live in `:core:wire` + adapter DTOs (`com.launcher.adapters.crypto.KeyBlob`, `RecoveryBlobJsonCodec` in `:app`, Firestore adapters).
- Namespace is **`family.*`** (renamed from `cryptokit.*`, guarded by `NoLegacyFamilyNamespaceTest`). Docs still saying `cryptokit` are stale.
- **Known boundary violation #1**: `family.pairing.*` squats inside `:core:crypto` and holds `@Serializable` types (`PublicKey`, `SigningPublicKey`, `DeviceId`) + `ByteArrayBase64Serializer`, violating the TASK-141 "no serialization in crypto modules" invariant. Owner decision: pairing is a SEPARATE zone; current location = recorded debt → new task (§7.T1). Do NOT move code now.
- **Known boundary violation #2**: crypto adapters have two homes — `family.keys` adapters in `:app` (`com.launcher.app.data.envelope|recovery`), `family.crypto`/`family.pairing` adapters in `:core` (`com.launcher.adapters.crypto.*`). Owner decision: target rule = single home, migration is a NEW task (§7.T2). Architecture files state the TARGET rule and mark current split as known debt. Which single home (`:app` vs dedicated adapter module) is decided inside that task, not here.
- Envelope (`ConfigCipher2`) depends on primitives only (AEAD + `crypto_box_seal`), receives recipients/aad from orchestrators above (`DefaultEnvelopeBootstrap`, `RemoteStorage`). It does NOT reach into `RootKeyManager` internally.

## 4. Industry-validated boundary map (research done — copy into the files)

All seven zones validated against: Google Tink, RFC 9420 + RFC 9750 (MLS architecture), Signal/libsignal, NIST SP 800-57 Part 1, AWS KMS envelope encryption, Apple CryptoKit, libsodium scope, Wire core-crypto (production openmls+UniFFI — our exact stack). Sources with URLs in §4.1.

| Zone | Owns | Must NOT own | Talks to |
|---|---|---|---|
| **Primitives** (`family.crypto.*`) | AEAD/ECDH/sig/KDF/CSPRNG APIs; KAT/Wycheproof/property validation; typed keys (signing ≠ agreement, CryptoKit lesson) | version fields, serialization, key purposes/lifecycle/rotation policy, storage paths, ANY policy | called by all zones; **keystore is a sibling port (`SecureKeyStore`), never merged into algorithm API** (CryptoKit `SecureEnclave` precedent) |
| **Key hierarchy** (`family.keys.*`) | root key, HKDF purpose derivation, envelope encryption of config (AWS KMS: envelope = key-management, not primitive), recovery vault + anti-brute-force, rotation/escrow stubs, NIST 800-57 key states | feature wire formats (only its own blob shapes via adapter DTOs), group protocol, transport | down: primitives. up: purpose keys to features. Server counter via Worker port (rule 13 Tier 2) |
| **Pairing / membership = our Authentication Service (RFC 9750)** (`family.pairing.*`, future `:core:pairing`) | identity↔key binding, authorization/revoke policy (RFC 9750 §3.5: access control is application-layer BY DESIGN); the Noise_XX QR handshake — planned via `snow` Rust crate through FFI (§4.5 fact 4), NOT hand-rolled | ratchet/group internals, primitives re-implementation (incl. own ECDH — rejected) | down: Kotlin side → libsodium primitives; handshake → Rust `snow` via `:crypto-ffi`. out: hands verified bindings to MLS core, which trusts them and never re-derives |
| **MLS core** (future, 0 code) | RFC 9420 mechanics: TreeKEM, epochs, commit/welcome, KeyPackage *format* | membership policy, KeyPackage pool ops, delivery trust, identity verification | **its OWN Rust-side primitives** via openmls provider trait (`OpenMlsCrypto`; backend RustCrypto/libcrux — choice belongs to TASK-124, NOT decided) — it does NOT call our Kotlin libsodium primitives; state via storage port (openmls `StorageProvider` pattern); pairing zone as its AS |
| **KeyPackage lifecycle = Delivery Service directory role (RFC 9750 §5.1)** (future, 0 code) | client: pool sizing/replenishment, last-resort designation, post-Welcome key deletion; server: claim/one-time-use + drain defense (Worker endpoint under rules 12/13) | KeyPackage internal structure (MLS core's), identity semantics | MLS core; Worker DS endpoint (opaque blobs) |
| **FFI** (`:crypto-ffi`) | UniFFI/cargo-ndk build, bindings, panic/error mapping. Bridges are DUMB (libsignal/Wire pattern) | any crypto logic, any policy | wraps Rust; Kotlin consumes generated bindings only |
| **Wire** (`:core:wire`) | version header + reader gate | — (leaf) | consumed by adapter DTO layer |

## 4.5 DECIDED FACTS REGISTRY (verified 2026-07-21 against Decision blocks + crypto.md component choices — COPY these, never re-derive)

**Decision-status ground truth** (per rule 11: `Draft` + filled `### Decision (English)` block = DECIDED, mutable until implementation starts; the block is the contract):
- **DECIDED** (Draft + Decision block filled): TASK-101, TASK-102, TASK-103, TASK-104 (owner accepted 2026-07-03), TASK-105, TASK-106, TASK-108, TASK-110, TASK-112. Also Done: TASK-100, TASK-57, TASK-58 (superseded→104).
- **NOT decided**: TASK-111, TASK-114 (Draft, no Decision block).
- **In Discussion** (block drafted, not final): TASK-115, TASK-117.
- Implication for the umbrella zone map: Track B zones are `designed, not built` — their SoT is the **Decision block of the owning task**, NOT crypto.md scenario prose. The implementer MUST read the Decision blocks of TASK-102 and TASK-104 before writing those zone rows.

**Protocol & library facts** (sources: TASK-58 closure note, TASK-104 Decision block, crypto.md §«Какие компоненты выбрали» lines 594–773):
1. Group E2E = **MLS (RFC 9420)**, chosen over Signal Sender Keys for post-compromise security. TASK-58 closure 2026-07-07.
2. MLS library = **openmls, pinned `=0.8.1`** (MIT; SRLabs 2024 audit, 7/8 findings fixed). Chosen in TASK-104 mentor session 2026-07-02; recorded in crypto.md frontmatter. Rejected with reasons: libsignal (AGPL), matrix-rust-sdk (AGPL+Synapse), Kalium/CoreCrypto (GPL), mls-rs (no third-party audit — runner-up), mls-kotlin (hobby). Exit ramp: swap to mls-rs ≈ 1–2 weeks adapter rewrite (same RFC 9420 wire format; on-disk state is library-specific).
3. **MLS runs on openmls's OWN Rust-side primitives** (`OpenMlsCrypto` provider; backend choice = TASK-124 scope). It does NOT call our Kotlin libsodium. Two primitive stacks across the FFI border = deliberate (Wire core-crypto precedent).
4. **Pairing handshake = Noise_XX via `snow` (Rust crate, via UniFFI)** — "собственный ECDH handshake" explicitly REJECTED (crypto.md line 801). Therefore the pairing zone ALSO spans both worlds: identity↔key binding + revoke policy = Kotlin side (built: `DeviceIdentityRepository` etc.); the Noise handshake itself = Rust side (planned, TASK-67). crypto-pairing.md must state this two-world split explicitly.
5. Kotlin binding = **UniFFI**; ACTUAL mechanism (built, TASK-122) = **proc-macro mode 0.28, NO .udl file**. TASK-124's and crypto.md's ".udl" mentions are stale — instruct: proc-macro, not UDL.
6. **Planned MLS adapter home** = `core/crypto/src/androidMain/.../adapters/openmls/` (crypto.md frontmatter + TASK-124 agree; namespace now `family.*` — TASK-124's `cryptokit.*` paths predate the TASK-141 rename). crypto.md BODY's `app/adapters/openmls/` + `native/openmls-ffi/` + `libopenmls_ffi.so` are ALL stale. Planned fitness rule (TASK-124): `commonMain` must not import `openmls*`/`uniffi*` — only androidMain/iosMain may.
7. MLS state storage = **SQLCipher** (TASK-125), via openmls `StorageProvider` trait; TASK-124 does in-memory first (deliberate split "works" vs "survives reboot"). ⚠ crypto.md:649 says SQLCipher key derived via **PBKDF2**, while recovery vault uses **Argon2id** — potential inconsistency; do NOT resolve it in this consolidation, flag it as an open note inside the umbrella zone map row for MLS storage (owner: resolve at TASK-125 spec time).
8. **KeyPackage defense** (TASK-104 Decision, ACCEPTED): pool cap + claim dedup + last-resort + CF edge rate limit; NO active velocity policing. Preset fields (family defaults): `poolCap=100`, `claimDedupTTLSeconds=600`, `lastResortRotationDays=7`, `refillThreshold=20`. Invariant: cap enforced server-side. KeyPackagePort stays FAKE (client-local) until TASK-104 server side lands.
9. **Revoke** (TASK-102 Decision block exists — read it): primary-user device = sole MLS Commit signer; admins revoke via profile edit + server edit lock (TTL 300 s); device reconciles profile vs roster → issues Remove. Explicitly does NOT apply to the future family messenger group (TASK-42, parking m-4) — that's a SEPARATE group with its own future policy.
10. **Two different MLS groups** must never be conflated: device-management group (TASK-102, MVP path) vs family messenger group (TASK-42, parking). Envelope encryption remains the config-sharing mechanism; TASK-42 migration triggers only on outgrow (scale/security incident).
11. **History backup MVP** = Signal-style none (TASK-100 Done): new device sees only current Profile snapshot; exit ramp HIST-BACKUP-001 (Phase-3+).

Canonical leakage smells to write into the files (industry-warned): (a) version/wire inside crypto primitive — already fixed by TASK-141, unanimously confirmed by age/JWE/Tink/libsodium; (b) key lifecycle leaking into primitives — symptom: primitives module knowing purpose names or rotation schedules; (c) protocol trust leaking into delivery — RFC 9750: "even a malicious DS cannot add itself to groups"; (d) membership policy leaking into group protocol; (e) session/state storage baked into protocol code (libsignal externalizes ALL state behind 4 store interfaces); (f) hardware keystore fused with algorithm API.

### 4.1 Sources (cite these in the files' industry-grounding sections)

- Tink key mgmt / rotation / envelope-as-key-mgmt: https://developers.google.com/tink/key-management-overview , https://developers.google.com/tink/client-side-encryption ; KMS as separate artifacts: https://github.com/tink-crypto/tink-java-gcpkms
- MLS architecture (AS/DS, KeyPackage §5.1, access control §3.5): https://www.rfc-editor.org/rfc/rfc9750.html ; protocol: RFC 9420
- libsignal layering + store interfaces: https://github.com/signalapp/libsignal
- Signal spec decomposition (X3DH/PQXDH vs Double Ratchet vs Sesame): https://signal.org/docs/
- openmls provider traits: https://book.openmls.tech/traits/traits.html
- Wire core-crypto (production openmls + UniFFI Kotlin/Swift): https://github.com/wireapp/core-crypto/blob/main/docs/ARCHITECTURE.md
- NIST SP 800-57 Pt 1 Rev 5: https://nvlpubs.nist.gov/nistpubs/specialpublications/nist.sp.800-57pt1r5.pdf
- AWS envelope encryption: https://docs.aws.amazon.com/kms/latest/cryptographic-details/client-side-encryption.html
- CryptoKit SecureEnclave separation: https://developer.apple.com/documentation/cryptokit/secureenclave
- libsodium scope (stops at primitives): https://doc.libsodium.org/

## 5. Owner decisions taken 2026-07-21 (FINAL — do not re-ask)

1. **Scope**: Track A first (consolidate the BUILT libsodium stack). Track B (MLS/KeyPackage/server-crypto) files are NOT written now — the umbrella carries a "map of the undecided" pointing at Decision tasks instead.
2. **Granularity**: umbrella + ONE skill `crypto` now; concept files only for Track A; further splitting later when work reaches those concepts (MVA / rule of three).
3. **Extraction policy**: separate file `docs/architecture/extraction-policy.md` (not an INDEX.md section).
4. **Pairing**: separate architecture zone (our AS). Current location in `:core:crypto` = recorded debt + migration task. Serialization in pairing becomes legal once it leaves the crypto SDK modules.
5. **Adapters**: target rule = single home; migration is a new task; files state target + current reality as debt.

## 6. The work, file by file

### 6.1 New: `docs/architecture/crypto-primitives.md`

Content sources: crypto.md lines 1031–1055 (primitives table, libsodium adapters, industrial baseline) + 1059–1111 (validation set A–F) + the primitives rows of §3/§4 above.
Structure per §2 recipe. Zone charter: owns algorithms + validation; keystore = sibling port; must-not-own list from §4 row 1. Status: BUILT (tasks 2/51/56 Done). Include the fitness functions that already guard it (`verifyCryptoIsolation`, `NoLegacyFamilyNamespaceTest`).
**Mandatory scope statement (two primitive stacks)**: this file covers the KOTLIN-side libsodium primitives serving key hierarchy / envelope / recovery / pairing. It does NOT serve MLS: openmls brings its own Rust-side crypto backend behind its `OpenMlsCrypto` provider trait (backend choice = TASK-124 scope), below the FFI bridge. Two primitive stacks coexisting across an FFI border is the industry-standard shape (Wire core-crypto precedent). Write this explicitly so no reader assumes MLS sits on libsodium (or tries to "unify" the two stacks — that would mean writing a custom OpenMlsCrypto backend, high-risk crypto work with no benefit; list under Rejected).

### 6.2 New: `docs/architecture/crypto-key-hierarchy.md`

Content sources: `docs/dev/key-hierarchy.md` (derivation chain, promote its architecture here) + crypto.md glossary bits 827–831 + rotation/escrow stub note (~line 1235) + §3 ground truth (real port names!) + §4 row 2.
Zone charter: root→HKDF purposes, envelope (`ConfigCipher2`/`EnvelopeConfigCipherImpl` — state that envelope is key-management per AWS KMS precedent, depends on primitives only), recovery vault (`RecoveryKeyBackup*`, `PassphraseAttemptCounter`, Argon2id via primitives), rotation/escrow = interface stubs (`KeyEscrow`, `KeyRotation` live in `:core:crypto` today — note this placement as acceptable: they are port declarations, policy would live here). Status: BUILT (tasks 4/6/66 Done). Open: TASK-112 (IdentityVault boundary — NOT YET DECIDED, do not present `KeyVault` as existing), TASK-59 (anti-brute-force mechanism choice), TASK-21/39 (recovery flows).

### 6.3 New: `docs/architecture/crypto-pairing.md`

Content sources: crypto.md scenario 2 (lines 377–448, keep the mermaid, DROP the step-by-step prose) + revoke policy (734–764) + scenario 4 mermaid (508–592, drop prose) + §4 row 3.
Zone charter: this zone IS our RFC 9750 Authentication Service. Owns handshake (Noise_XX over QR — note `snow` crate is the planned impl, NOT yet in code), identity↔key binding (`DeviceIdentityRepository` etc. — real, built), revoke/reconciliation policy. Must-not-own from §4.
**Known debt block (mandatory)**: `family.pairing.*` currently lives inside `:core:crypto` with `@Serializable` types — violates the crypto-SDK no-serialization invariant; target = own module `:core:pairing`; migration = TASK from §7.T1. Until migrated, the invariant "no serialization in crypto modules" formally reads "no serialization in `family.crypto.*` / `family.keys.*` packages".
**Two-world statement (mandatory)**: identity↔key binding + revoke policy = Kotlin side (built); Noise_XX handshake = `snow` Rust crate via `:crypto-ffi` (planned, TASK-67; own-ECDH rejected — crypto.md:801). Related decisions (most DECIDED via Decision blocks, see §4.5): TASK-102 (device-group ownership/revoke — DECIDED, read its Decision block), TASK-106 (signup gate — DECIDED), TASK-116 (iconic challenge — Discussion), TASK-143 (QR deep-link versioning — In Progress→Paused, do not resolve it here).

### 6.4 Rewrite: `docs/architecture/crypto.md` → umbrella (~150 lines)

KEEP: frontmatter component inventory (lines 1–89, update stale paths per §3: `libcrypto_ffi.so`, no `app/adapters/openmls`); AI-TLDR (93–175) rewritten per §2 with a **routing table**: primitives → crypto-primitives.md; key hierarchy/envelope/recovery → crypto-key-hierarchy.md; pairing/handshake/revoke → crypto-pairing.md; versioning → wire-format.md; endpoints → server.md; extraction → extraction-policy.md; MLS/KeyPackage → "map of the undecided" below. Component map mermaid (250–296) with legend repointed. Decision index TABLE only (889–919). Rejected alternatives (795–821). Terminology mapping (856–871). Cross-refs, history.
ADD: **Zone map table** — one row per zone (primitives / key hierarchy / pairing-AS / MLS core / KeyPackage-DS / FFI / wire) with columns: zone → file (or "no file yet") → status (`built` / `designed, not built`) → owning Decision task if status is not-built. This is the ecs.md "Open (deferred, with owners)" pattern: ONE line per zone, task ID as pointer. Do NOT add any standalone "undecided/open tasks" dump section — the backlog is the task tracker (rule 11), the umbrella is architecture only. For not-built zones state one sentence: "the contract for this zone = the `### Decision (English)` block of the owning task (most ARE decided — §4.5); crypto.md scenario prose is secondary and may be stale." Before writing MLS/KeyPackage/revoke rows, READ the Decision blocks of TASK-102 and TASK-104 and use their wording.
DELETE from crypto.md (content moves or dies):
- 188–247 novice MLS tutorial → DELETE (teaching prose; the MLS file will get its own TLDR when Track B happens).
- 298–592 scenarios: keep only the mermaids that moved to concept files; delete step-by-step prose and "зачем" tables.
- 594–773 component choices: primitives/hierarchy/pairing parts move to their files; openmls/UniFFI/SQLCipher/KeyPackage/TreeKEM/revoke choice records stay COMPACT in the umbrella's decision index (they are decided choices for Track B, keep one line each + task pointer).
- 823–854 novice glossary → DELETE (duplicates TLDR).
- 873–887 "Топ-7 способов взорвать" → DELETE.
- 921–1008 Implementation sequence → DELETE, replace with one line: «порядок реализации → `backlog sequence list --plain`» (rule 11).
- 1031–1111 → moved (6.1).
- 1113–1226 Post-MVP roadmap + A4 pre-release checklist → MOVE to new `docs/dev/crypto-prerelease.md` (operational, not architecture).
- 1239–1266 FFI exit ramps → keep a COMPACT FFI zone summary in umbrella (it's small); full crypto-ffi.md file is deferred until Track B (owner decision: split on demand). Fix `libopenmls_ffi.so` → `libcrypto_ffi.so` everywhere.

### 6.5 New: `docs/architecture/extraction-policy.md`

Consolidates the extraction discipline currently scattered across 6 places. Content sources: `docs/product/glossary.md` §7a (lines ~225–274: candidates `core/wizard`, `core/localization`, `core/ui-senior`, rule of three, fitness function `core/*` never imports `app/*`); `specs/016-f-crypto-core-module/spec.md` US-5 + FR-004 (crypto extraction: `git filter-repo` → private repo, Apache 2.0, Maven artifact at 2nd consumer); `specs/task-51-libsodium-consolidation/spec.md` line ~119 (`cryptokit-kmp` — NOTE namespace is now `family.*`, update); TASK-141 motivation (versioning kept OUT of crypto to preserve the extraction barrier); `docs/dev/project-backlog.md` ~617 + `docs/dev/server-roadmap.md` ~894 (push module extraction TODO-ARCH-018 / SRV-PUSH-EXTRACTION); inline TODOs `core/crypto/build.gradle.kts:16` (`extract-when-2nd-consumer`).
Decisions to state: trigger = second REAL consumer (rule of three, never speculative); extraction unit = whole crypto core + FFI bridges as ONE versioned repo (libsignal/Wire precedent), NOT per-module scatter; vendor/KMS adapters stay OUT of the extracted core (Tink precedent); **ECS is explicitly NOT an extraction candidate** (task-136 contract: "a second consumer would be speculative, rule 4"); versioning module `:core:wire` is the barrier that keeps crypto extractable (TASK-141).
Cross-link: crypto.md umbrella and wire-format.md each get one pointer line to this file (wire-format.md edit must follow its own §12 same-commit rule — this is a pointer addition, not a rule change).

### 6.6 New skill: `.claude/skills/crypto/SKILL.md`

Copy the SHAPE of `.claude/skills/ecs/SKILL.md` (32 lines — read it first). Frontmatter description trigger net: crypto, ключи, key hierarchy, root key, HKDF, envelope, AEAD, libsodium, Argon2id, recovery, vault, passphrase, escrow, rotation, pairing, handshake, Noise, MLS, openmls, TreeKEM, KeyPackage, GroupPort, FFI, UniFFI, crypto-ffi, SecureKeyStore, RootKeyManager, KeyRegistry, ConfigCipher2, Ed25519, Curve25519, "как работает наша крипта".
Body: thin router. "Single source of truth = docs/architecture/crypto.md umbrella; read its AI-TLDR first; do not re-derive or re-decide." Reading map: primitives question → crypto-primitives.md; key/envelope/recovery → crypto-key-hierarchy.md; pairing/revoke → crypto-pairing.md; MLS/KeyPackage → umbrella zone map (status column names the owning Decision task) — **for zones marked `designed, not built` the skill must instruct: STOP and surface the owning Decision task, do not improvise an answer**. Guardrails (each with file§ pointer): version/serialization never in crypto SDK packages (wire-format.md + TASK-141); keystore = sibling port; membership policy never in MLS core; bridges are dumb. Hard sync rule both sides.

### 6.7 Dedup pass (single source of truth enforcement)

1. `docs/dev/agent-context.md` — crypto sections §2 (107–181), §3 (185–257), §4 (261–317), §5 (319–397), §6 crypto parts (397–483): replace each with 2–4 line pointer blocks ("см. docs/architecture/crypto*.md; здесь только опасные места, которых нет в SoT" — keep genuinely operational gotchas if any, move architecture claims out). Sections §1 (ECS) and §7 (server) are OUT OF SCOPE for this pass — leave, or add one-line "may duplicate ecs.md/server.md — pending separate dedup" warning at top of those sections.
2. `docs/dev/key-hierarchy.md` — keep ONLY: port→implementation mapping table + "How to add a new purpose" how-to. Replace the derivation-chain architecture (lines ~6–42) with a pointer to crypto-key-hierarchy.md.
3. `docs/architecture/INDEX.md` — register the new files (crypto-primitives, crypto-key-hierarchy, crypto-pairing, extraction-policy) in its registry with scope lines; update crypto.md's entry to "umbrella/router".
4. `CLAUDE.md` — no rule changes needed. Rule 1 crypto-exception text references `:core:crypto`/`:core:keys` — still correct. Do NOT touch.
5. Historical files (ADR-008/012/013, specs/016, task-127 specs, decisions/) — DO NOT EDIT (historical per rule 11).

### 6.8 New: `docs/dev/crypto-prerelease.md`

Receives crypto.md lines 1113–1226 (post-MVP A1–A5 incl. A4 audit checklist) mostly verbatim, with a header "Operational pre-release material, moved out of architecture per SoT consolidation. Architecture → docs/architecture/crypto.md."

## 7. Follow-up backlog tasks to CREATE (Draft, do not start them)

- **T1**: «Вынести family.pairing.* из :core:crypto в :core:pairing» — priority medium, label crypto,refactor. Rationale: pairing = separate AS zone (industry), clears TASK-141 no-serialization invariant for the crypto SDK. AC: package moved, fitness rules updated (`verifyCryptoIsolation` allowlist), no `@Serializable` left in `:core:crypto`, `NoLegacy*` tests green.
- **T2**: «Единый дом для крипто-адаптеров» — priority low, label crypto,refactor. Decide `:app` vs dedicated adapter module INSIDE the task; migrate `com.launcher.adapters.crypto.*` accordingly.
- **T3** (optional, if owner agrees): «Dedup agent-context.md §1 ECS + §7 server» — the non-crypto restates found in the audit.
- Note in the new consolidation task's description: TASK-105/102/104/106/108 Decision sessions ALREADY HAPPENED (blocks filled — §4.5). The next real lever after this consolidation merges is the implementation chain **TASK-123 → TASK-124 → TASK-125** (F-CRYPTO ports → openmls → SQLCipher), plus closing the two genuinely undecided: TASK-111, TASK-114.

## 8. Suggested AC for the consolidation task

1. `[hand]` crypto.md ужат до умбреллы (~150 строк): AI-TLDR + routing + карта зон (со статусом built / designed-not-built per zone); никакой отдельной «свалки нерешённых задач»; туториалы/roadmap/impl-sequence удалены или переехали.
2. `[hand]` Созданы crypto-primitives.md, crypto-key-hierarchy.md, crypto-pairing.md — каждый по рецепту §2 (precedence, AI-TLDR, инварианты, Rejected, industry grounding с URL).
3. `[hand]` Создан extraction-policy.md, ссылки из crypto.md и wire-format.md; ECS явно исключён.
4. `[hand]` Создан skill `crypto` (thin router по образцу ecs), триггеры покрывают все крипто-термины; для нерешённых тем skill велит STOP → Decision task.
5. `[hand]` Дедуп: agent-context.md крипто-секции → ссылки; key-hierarchy.md → dev-справка со ссылкой; INDEX.md обновлён.
6. `[hand]` Ни одного упоминания `libopenmls_ffi.so`, `app/adapters/openmls`, `cryptokit.*` в живых (не исторических) доках.
7. `[hand]` Заведены follow-up tasks T1, T2 (Draft).
8. `[hand]` Zero production-code changes in the PR diff (docs + skills + backlog only).

## 9. Verification before PR

- `grep -rn "libopenmls" docs/ .claude/` → only historical/spec files, none in `docs/architecture/` or skills.
- `grep -rn "cryptokit\." docs/architecture/ .claude/skills/crypto/` → zero.
- Every `docs/architecture/crypto*.md` has `AI-TLDR:BEGIN` and a routing/reading map.
- Skill `crypto` contains NO architecture content (spot-check: no invariant explained inline, only pointers).
- `git diff --stat` contains no `.kt`, `.rs`, `.gradle.kts` changes.
- Run `pre-pr-backlog-sync`, then `gh pr create` with `Backlog: task-<N> → <status>` line.

---

## Русское резюме для владельца (TL;DR)

**Что это**: полная инструкция для AI-сессии, которая будет делать крипто-консолидацию. Всё уже исследовано и решено — исполнителю не нужно ничего продумывать заново.

**Что будет сделано**: `crypto.md` (1302 строки, устаревший, смешивает всё) превращается в короткий файл-роутер + три чистых архитектурных файла по построенной части крипты (примитивы / иерархия ключей / pairing) + файл про вынос модулей в семейство приложений + skill `crypto` (как у ECS — чтобы агент всегда упирался в правильный файл и не перепридумывал). Дубли в `agent-context.md` и `key-hierarchy.md` заменяются ссылками.

**Что решено тобой**: pairing — отдельная зона (переезд кода — отдельной задачей позже); адаптерам — единый дом (тоже отдельной задачей); сначала Track A (построенное); в умбрелле — карта зон со статусами (никакой свалки нерешённых задач — задачи живут в backlog); extraction-policy — отдельным файлом.

**Что проверено**: все границы зон подтверждены промышленными стандартами (Tink, Signal, RFC 9750, NIST, libsodium, Wire) и реальным кодом репозитория. Найденные противоречия (имя нативной библиотеки, место pairing-пакета, два дома адаптеров) разрешены и записаны.

**Чего здесь НЕТ**: изменений кода — только документы и skills.

**Важная поправка аудита 2026-07-21 (вторая волна)**: почти все крипто-Decision'ы УЖЕ приняты (Decision-блоки заполнены в TASK-101…110, 112; реально не решены только TASK-111 и TASK-114). MLS работает на **своих** Rust-примитивах openmls (не на нашем libsodium); pairing-handshake — через Rust-библиотеку `snow` (тоже через FFI). Все эти факты внесены в §4.5 handoff'а — исполнитель их копирует, не выводит заново. Следующий рычаг после консолидации — цепочка реализации TASK-123 → 124 → 125.
