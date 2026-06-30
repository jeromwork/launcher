# Checklist: meta-minimization (plan-level re-run)

**Scope:** plan.md + data-model.md + research.md for TASK-6 (F-5 Root Key Hierarchy + Owner Recovery).
**Date:** 2026-06-28
**Previous run:** spec-level (see `meta-minimization.md`). This re-run audits whether the **plan** stage introduced speculative abstractions, modules, or types not present in the spec stage.

---

## Results

| ID    | Status | Notes |
|-------|--------|-------|
| CHK001 | [x] | Every port has at least one in-spec consumer: `KeyRegistry` → `ConfigCipher2` (spec 018, already shipped) + RootKey wrap; `RootKeyManager` → setup/recovery flows; `RecoveryKeyBackup` → `RootKeyManager.create/.recover`; `AuthAvailability` → wizard gate (FR-005, FR-013). No port deferred to "future spec 008". |
| CHK002 | [x] | Single-impl ports each justified by port-shape need: `RecoveryKeyBackup` (own-server swap, server-roadmap SRV-RECOVERY-001 = documented exit ramp, swap = single-file); `KeyRegistry`/`RootKeyManager` (KMP commonMain + Android adapter — platform asymmetry, libsodium boundary per rule 2 ACL); `AuthAvailability` (multi-impl required — domain-level enum forbids vendor names, future Email/Phone adapters anticipated by F-4). No "extensibility for its own sake". |
| CHK003 | [x] | `RootKeyManagerImpl` is data-transformation (Argon2id → wrap → store; unwrap → derive). `RecoveryBlobCodec` is JSON serialization. `KeyRegistryImpl` performs HKDF. No pass-through orchestrators. `RecoveryViewModel` maps errors → UI state (legit MVVM). |
| CHK004 | [x] | No DSL, no plugin system, no purpose-registry. `KeyRegistry.derive(stableId, purpose)` uses plain `String` for `purpose` (Q-G plain-string approach, ~3-7 values, additive without arch change). Simpler composition explicitly chosen over registry. |
| CHK005 | [x] | New gradle module `core/keys/` satisfies Article V §3: (a) ownership boundary (key hierarchy is sole concern), (b) build isolation (KMP commonMain ports + Android adapters), (c) stable API (port shapes locked from contracts/), (d) testability (fakes in commonTest, fitness function via Konsist on this module's boundary). Worker artifact `workers/backup/` is a separate language/runtime artifact (TypeScript on Cloudflare), not a gradle module — separation is forced by language. |
| CHK006 | [x] | Plan §Project Structure answers: `core/keys/` is KMP module not a package because (a) Konsist fitness function scoped to module boundary (forbidden-token grep on `core/keys/src/commonMain/`), (b) build isolation prevents accidental libsodium/Firebase imports leaking from `app/` into ports, (c) iOS target declared inactive at module level (consistent with F-CRYPTO precedent — package-level can't express target asymmetry). |
| CHK007 | [x] | No "utils/common/helpers" module. All adapters named by what they do: `AndroidKeystoreRegistry`, `Argon2RootKeyManager`, `WorkerRecoveryKeyBackup`, `DataStorePassphraseAttemptCounter`. |
| CHK008 | [x] | No new user-facing config fields. `RecoveryKeyBackupBlob` fields each have a current consumer: `schemaVersion` (CHK008 wire-format), `stableId` (Worker routing + self-describing), `salt`/`nonce`/`kdfParams`/`ciphertext` (Argon2id+AEAD requirement), `createdAt` (audit/debug per spec — could be argued speculative; see CHK010 below). |
| CHK009 | [x] | `RecoveryKeyBackupBlob` carries `schemaVersion=1` from first commit (rule 5); backward-compat test enforced (`RecoveryKeyBackupBlobBackwardCompatTest`); migration policy = additive fields + UnsupportedSchema refuse on newer-version reads. Documented in contracts/recovery-key-backup-v1.md. |
| CHK010 | [!] | **Test 1 applied per abstraction**. RootKey/DerivedKey opaque wrappers: if inlined as raw `ByteArray`, lose constant-time equals + `toString()` leak prevention + serialization ban — these are **invariants**, not optionality. Keep. `KdfParams` value class: if inlined as 3 ints + algorithm string in the blob, lose constructor invariant validation (algorithm ∈ {Argon2id}, ≥1024 KiB memory minimum); these are also invariants. Keep. **Minor concern (advisory, not fail):** `RecoveryKeyBackupBlob.createdAt` field — if inlined / removed, only audit/debug optionality lost. Defensible (small field, audit value real), but borderline per Test 1. Recommend keeping with note in contracts/. |
| CHK011 | [x] | Test 2: if Cloudflare R2 doubled in price / was deprecated → swap = `HttpRecoveryBackupStorage` adapter, one-file change, no wire-format change (research R1 exit ramp documents this exactly). If libsodium deprecated → swap inside `core/crypto` (TASK-51 already established this seam, not F-5's concern). If Firebase Auth deprecated → `AuthProvider` port already isolates F-5 (F-4 territory). All seams justified ≤ 1-day swap costs documented. |
| CHK012 | [x] | Plan REMOVED two abstractions vs round-1 draft: `NoOpRecoveryKeyBackup` adapter (research R6) + `RecoveryKeyBackupSelector` (plan §Constitution Check Gate 8). Verified absent in current plan.md (no references in §Project Structure, DI module shows "single WorkerRecoveryKeyBackup, no NoOp adapter, no Selector"). research.md R6 explicitly records the rejection rationale + exit ramp. No dangling references to either in plan.md / data-model.md. **Rule 4 in action — confirmed.** |
| CHK013 | [x] | No "deprecated, remove later" code introduced by plan. Migration from spec 018 `ConfigCipher2` is byte-equal preserved (FR-018) — not a deprecation, a transparent integration. No removal-deferred items. |

**Total:** 12/13 [x], 1/13 [!] (advisory only, not a fail).

---

## Findings summary

**Strengths:**
- Plan explicitly removes two abstractions per round-2 owner pushback (`NoOpRecoveryKeyBackup`, `RecoveryKeyBackupSelector`) — rule 4 (MVA) applied retroactively. research.md R6 documents the why-rejected + regret conditions + exit ramp for re-introduction.
- Every single-implementation port carries a concrete port-shape justification (platform asymmetry / KMP boundary / fitness-function scope / documented swap path).
- `KeyRegistry.derive(stableId, purpose)` uses plain `String` purpose values (Q-G plain-string) — registry/DSL escalation explicitly avoided.
- New `core/keys/` module justified by Konsist fitness-function scope (forbidden-token grep at module boundary cannot be expressed at package level).

**Advisory (not a fail):**
- CHK010 — `RecoveryKeyBackupBlob.createdAt` field has only audit/debug value. Defensible (small field, real debug value via R2 console + curl) but is the one item closest to "speculative" in the plan. Recommend a single-line note in `contracts/recovery-key-backup-v1.md` documenting "field exists for operational debugging — if dropped in v2, no client behavior change."

**No fails.** Plan-level architecture passes meta-minimization re-run.
