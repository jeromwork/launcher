# Checklist: meta-minimization — TASK-51 (libsodium consolidation)

**Spec**: [`specs/task-51-libsodium-consolidation/spec.md`](../spec.md)
**Date**: 2026-06-26
**Applied by**: AI agent (skill `checklist-meta-minimization`)
**Reference**: Article XI of `.specify/memory/constitution.md` + rule 4 of `CLAUDE.md` ("Minimum Viable Architecture, not Minimum Viable Product")

This spec is fundamentally a **consolidation / removal** refactor — most "new" surfaces actually replace older surfaces with fewer abstractions. The checklist still applies to the net-new package `cryptokit.pairing.*` and to the renamed `cryptokit.crypto.*`.

---

## New abstractions

- [x] CHK001 Every new interface/port has at least one concrete consumer **in this spec** (not "in spec 008 we'll need it").
  - `cryptokit.crypto.api.AeadCipher`, `AsymmetricCrypto`, `KeyDerivation`, `SecureKeyStore`, `RandomSource` — all consumed by `PairingCryptoCoordinator` (FR-008) + `Spec011SmokeDebugActivity` (User Story 1, scenario 3). Not net-new — renamed from `family.crypto.api.*` (spec 016).
  - `cryptokit.pairing.api.*` (15 wire-format types: `DeviceIdentity`, `EncryptedEnvelope`, `Recipient`, `DeviceIdentityRepository`, `EncryptedMediaStorage`, `RecipientResolver`, …) — all consumed by existing spec 011 pairing flow (FR-006). Each type already has at least one consumer in this codebase.

- [x] CHK002 If a new interface has only one implementation: justified by port-shape need (DI, fakes, platform asymmetry) — not by "extensibility".
  - `SecureKeyStore` is `expect/actual` — platform asymmetry (Android Keystore in `androidMain`, iOS Keychain in `iosMain` later per US3). Justified.
  - All crypto-API ports have **two** implementations (`libsodium` real + `fake` for tests, see Local Test Path) — fakes-for-tests need is the explicit justification (CLAUDE.md rule 6 mock-first).
  - `pairing.api` repositories (`DeviceIdentityRepository`, `EncryptedMediaStorage`, `RecipientResolver`) — Firestore-backed real adapter + in-memory fake (`FakeDeviceIdentityRepository`, etc.) per Local Test Path. DI + fake need is the justification.

- [x] CHK003 Mediator/orchestrator/manager class is justified by data transformation, not by pass-through.
  - `PairingCryptoCoordinator` — orchestrates **non-trivial** flow: generate per-device X25519 + Ed25519 keypairs → store in `SecureKeyStore` under per-device IDs → assemble `DeviceIdentity` → publish to repository. Transformation, not pass-through. Already existed pre-TASK-51.
  - No new mediators introduced by this spec.

- [x] CHK004 No custom DSL, registry, or plugin system unless simpler composition has been tried and documented as failing.
  - No DSL, no registry, no plugin system in this spec. The single Koin module `cryptokitModule` (FR-015) is plain DI composition.

## New modules / packages

- [x] CHK005 New gradle module satisfies at least one of Article V §3 criteria.
  - **No new gradle module created.** Spec stays inside existing `core/crypto/` module (per § Architectural decisions §1). New sub-packages within the same module, not new modules.

- [x] CHK006 If new module is added: plan answers "Why is a package not enough?" explicitly.
  - N/A — no new module added (see CHK005). Spec explicitly chooses packages-within-module: `cryptokit/crypto/` and `cryptokit/pairing/` co-located in `core/crypto/src/commonMain/`.

- [x] CHK007 No "utils" / "common" / "helpers" dumping ground module created.
  - No `utils` / `common` / `helpers` package introduced. `cryptokit.crypto.exception` is a focused exception hierarchy (single concern: CryptoException), not a dumping ground.

## New configuration

- [N/A] CHK008 New config field has a current FR consuming it.
  - No new user-facing or persisted configuration introduced. Spec touches code-only crypto stack.

- [N/A] CHK009 Config field defaults documented; backward-compat policy defined.
  - N/A — no new config fields.
  - Note: spec 011 wire-format (`schemaVersion: 1`) is **preserved unchanged** (FR-004, Assumptions); not a new config.

## CLAUDE.md rule 4 self-test

- [x] CHK010 **Test 1** applied: if abstraction were inlined, what would be lost?
  - `AeadCipher` / `AsymmetricCrypto` / `SecureKeyStore` ports: inlining would couple every call-site to ionspin/Android Keystore SDK types → violates CLAUDE.md rule 1 (domain isolation) and rule 2 (ACL). Loss is real, not "future optionality" — fakes for tests are needed today.
  - `HashFunction` port — **deliberately NOT introduced** (FR-014, Q6). Spec explicitly applied Test 1: inlining `MessageDigest.getInstance("SHA-256")` loses only theoretical optionality. Removed.
  - `Outcome<T, CryptoError>` sealed hierarchy — **deliberately removed** (FR-009, Q3). Test 1 applied: inlining throws-pattern with one universal catch loses nothing of current value. Removed.
  - `PrivateKey` / `SigningPrivateKey` opaque types — **deliberately removed** (§ Architectural decisions §2). Test 1 applied: orphan abstractions with no current consumers. Removed.
  - `cryptokit.pairing.api.*` types: not abstractions in the rule-4 sense — they're wire-format data classes carrying `schemaVersion`, required by CLAUDE.md rule 5. Inlining would violate rule 5.

- [x] CHK011 **Test 2** applied: if dependency on the other side doubled in price / was deprecated / violated privacy — how long to swap?
  - libsodium-via-ionspin → another libsodium binding (e.g. cashapp's): swap localised to `cryptokit.crypto.libsodium.*` package, ~1–2 days. Seam justified — exactly the kind of vendor we already swapped (lazysodium → ionspin = this spec). Empirical evidence the seam is needed.
  - Android Keystore → alternate keystore: localised to `androidMain` `SecureKeyStore` actual, ~1 day. Seam justified by platform asymmetry alone (iOS Keychain in future iosMain).
  - `MessageDigest` for fingerprint: 1-line swap, no seam needed → port correctly **not** introduced.

## Removal validation

- [x] CHK012 If spec removes existing abstractions/modules: dangling references in `docs/**`, `specs/**` audited.
  - Spec removes substantial code: 22 files in `com.launcher.api.crypto/`, 5 `Libsodium*.kt`, `LibsodiumProvider`, `AndroidKeystoreSecureKeystore`, 8 fakes in `com.launcher.fake.crypto.*` (§ Clarifications resolution log + Key Entities § "Old stack ghost").
  - Spec also renames `family.crypto.*` → `cryptokit.*` (FR-016) — historic `family.*` references in specs/docs are pre-existing artifacts.
  - **Partial audit**: FR-007 introduces Konsist fitness tests enforcing zero matches for `com.goterl.*`, `net.java.dev.jna.*`, `JNA.register`, `com.launcher.api.crypto.*`, `family.crypto.*` — this is the automated dangling-reference guard for **production code**. SC-003, SC-004, SC-012 give grep verification.
  - **Open item**: spec does not explicitly audit `docs/**` / `specs/**` for stale references to `com.launcher.api.crypto.*` or `family.crypto.*`. Recommended follow-up during `speckit-tasks` — add task "audit docs/specs for renamed namespaces" or accept that historic specs (002, 007, 011, 016) will retain old names as historical artifacts (per `CLAUDE.md` spec-naming convention: old specs not renamed). **Non-blocking** — production code is what Konsist enforces.

- [x] CHK013 If spec marks code "deprecated, will remove later" — concrete removal task exists.
  - Spec does **not** introduce any "deprecated, remove later" markers. Removal is **immediate** — Q1 deep migration: 22 files + 5 files + 8 fakes deleted in this spec, not deferred.
  - Inline-TODO `TODO(post-task-6): replace nuke-and-re-pair with derive-from-root` (FR-005) — points to a concrete future task (TASK-6 Root Key Hierarchy, parking-lot), not "eventually". Satisfies the rule.

---

## Verdict

**PASS** — 13/13 CHK [x] (CHK008, CHK009 marked N/A as no new configuration introduced).

The spec is well-aligned with the meta-minimization principle. It is fundamentally a **negative-delta** refactor: it removes 5 ports (`HashFunction`, `Outcome` sealed hierarchy, `PrivateKey`/`SigningPrivateKey` opaque types, `AndroidKeystoreSecureKeystore`, parallel `CryptoModule`), 22+5+8 files of legacy crypto code, and one `packaging.jniLibs.pickFirsts` build hack. The net-new surface (`cryptokit.pairing.api.*`) consists of **wire-format data types** with `schemaVersion` per CLAUDE.md rule 5, not speculative abstractions — and each type has an existing consumer in spec 011 pairing flow. CLAUDE.md rule 4 tests are visibly applied in the clarifications table (Q3, Q4, Q6, Q7 all answered with "remove / inline / don't introduce").

## Open items

1. **CHK012 follow-up (non-blocking)**: during `speckit-tasks`, decide whether to add a task auditing `docs/**` and old specs (`001..020`) for stale references to `com.launcher.api.crypto.*` or `family.crypto.*`. Two acceptable resolutions:
   - **Option A**: audit and update non-historic docs (e.g. `docs/dev/`, `docs/product/decisions/`).
   - **Option B**: accept that pre-2026-06-23-renamed-convention specs and ADRs are historical artifacts; document the rename in a single ADR note pointing readers to the new namespace. (Aligns with `CLAUDE.md` spec-naming convention guidance.)

2. **Test fakes destination** is no longer open — § Clarifications resolution log resolved it: old fakes deleted, new ones in `core/crypto/src/commonTest/kotlin/cryptokit/{crypto,pairing}/fake/`. The `[NEEDS CLARIFICATION]` marker still appearing in § Local Test Path (line 212) is **stale** and should be cleaned up in the next spec touch.
