# Checklist: meta-minimization

Applied to `specs/task-6-root-key-hierarchy-recovery/spec.md` on 2026-06-28.

Anti-bloat audit per CLAUDE.md rule 4 (Minimum Viable Architecture) and constitution Article XI.

| ID | Item | Verdict | Evidence / Reasoning |
|----|------|---------|----------------------|
| CHK001 | Every new interface has ≥1 concrete consumer in this spec | [x] PASS | `KeyRegistry` consumed by `ConfigCipher2` migration (FR-018) + `RootKeyManager` (HKDF source). `RootKeyManager` consumed by all 3 recovery screens + ConfigCipher2. `RecoveryKeyBackup` consumed by setup/entry/fallback (FR-014/015/016, FR-019). `AuthAvailability` consumed by selector (FR-012/013) + setup gating (US-1 AS-5, US-4 AS-1). |
| CHK002 | Single-impl interface justified by port shape | [x] PASS | `KeyRegistry`/`RootKeyManager` — DI for fakes (FR-022) + Android Keystore platform asymmetry. `RecoveryKeyBackup` has **2 impls** (Google Drive + NoOp) — not single. `AuthAvailability` — platform-asymmetry (Android-specific GMS check) + fake for US-4 test. All ports justified by fake-adapter mandate (rule 6) + cross-platform `commonMain` isolation. |
| CHK003 | Mediator/orchestrator justified by transformation | [x] PASS | `RecoveryKeyBackupSelector` transforms capability detection → adapter choice (FR-012); not pass-through. `RootKeyManager` transforms passphrase+identity → root key (KDF), not pass-through. |
| CHK004 | No custom DSL/registry/plugin system | [x] PASS | Purposes as plain strings (Q-G resolution); explicit rejection of `Purpose sealed class + Registry` until count > 5. Inline TODO documents the deferral. |
| CHK005 | New gradle module satisfies Article V §3 | [x] PASS | `core/keys/` — ownership boundary (crypto domain), build isolation (`commonMain` for KMP), stable API (port surface), material testability gain (fakes in `commonTest`). Distinct from `core/crypto` (libsodium primitives). |
| CHK006 | Why package not enough? | [x] PASS | KMP `commonMain` requires module-level source set; package within `:app` couples to Android. Module boundary enforces rule 1 (no androidMain types leak into domain). |
| CHK007 | No utils/common/helpers dumping ground | [x] PASS | Module is `core/keys/`, scope-bounded to key hierarchy. No `common`/`utils` package created. |
| CHK008 | Config field has current FR consuming it | [x] PASS | All `RecoveryKeyBackupBlob` fields (FR-006) consumed: `schemaVersion` (FR-023 backward-compat), `stableId` (FR-019 isolation), `salt`+`kdfParams` (FR-009 KDF), `ciphertext`+`nonce` (decrypt), `createdAt` (debug/audit). No speculative fields. |
| CHK009 | Defaults documented, backward-compat defined, migration path | [x] PASS | `schemaVersion=1` (FR-006); backward-compat test `RecoveryKeyBackupBlobBackwardCompatTest` (FR-023). FR-018 migration path for ConfigCipher2 byte-equal. |
| CHK010 | Test 1 — inline & lose what? | [x] PASS | Documented per-port: inline `RootKeyManager` → loses fake-adapter testing of recovery flow without real Keystore + cross-platform reuse. Inline `KeyRegistry` → loses HKDF abstraction (would force ConfigCipher2 to know root-key material directly, violating rule 1). Inline `AuthAvailability` → loses local-mode gating without GMS coupling (US-4 unverifiable). Inline `RecoveryKeyBackup` → loses NoOp fallback for non-GMS path (would force `if (hasGMS)` branching in domain). All seams retain. |
| CHK011 | Test 2 — swap cost ≤ 1 day? | [x] PASS | If Google Drive App Data deprecated: only `GoogleDriveAppDataRecoveryKeyBackup` adapter changes; domain + UI + Selector logic stays. > 1 day (Drive integration is real work) → seam justified. If libsodium Argon2 deprecated: only `Argon2RootKeyManager` impl; `RecoveryKeyBackupBlob.kdfParams` versioning preserves backward-compat. Seams pass test 2. |
| CHK012 | Dangling references audited | [x] PASS | Spec acknowledges legacy `RecoveryKeyVault` → `RecoveryKeyBackup` rename (Notes section); references update for backlog `task-6` flagged. No `docs/**` audit shown but spec explicitly defers naming decision to plan phase. |
| CHK013 | Deprecation → concrete removal task | [x] PASS | `legacy 020-f5-* branch` artifacts — decision deferred to plan, not "eventually". Exit ramps each name a concrete TASK (TASK-21, TASK-39, TASK-41) with phase. |

---

## Anti-bloat focus areas (per request)

- **3 Compose screens count** (Setup/Entry/Fallback) — minimal: each screen has distinct UX state (FR-014/015/016). Cannot merge: Setup is create-flow, Entry is recovery-flow, Fallback is destructive-confirm. No bloat.
- **`NoOpRecoveryKeyBackup` necessity** — justified: alternative is `if (hasGMS)` branching across domain/UI (rule 1 violation). Null-object pattern keeps `RecoveryKeyBackup` invariant non-null in DI graph. US-4 acceptance requires it. PASS.
- **`AuthAvailability` port necessity** — justified: domain-level gating decision in US-1 AS-5 + US-4 AS-1. Without it, F-5 would peek into `AuthAdapterSelector` (F-4 infra). Port keeps capability check in domain language (`Available`/`Unavailable(reason)`). PASS.
- **`KeyRegistry` + `RootKeyManager` separation** — two ports, distinct concerns: RootKeyManager owns identity+passphrase→root (lifecycle), KeyRegistry owns root→purpose (HKDF derivation). Both have ≥1 consumer; collapsing would mix lifecycle and derivation. PASS.

## Verdict

13/13 PASS. No bloat detected. Premature-abstraction probes (single-impl ports, mediator pass-through, registry/DSL) all pass with documented justifications. The spec explicitly defers `Purpose registry` (Q-G), `passphrase change` (Q-H), `local→cloud upgrade` (Q-F), `server-side rate-limit` (Q-I) — all to concrete future tasks, not speculative scaffolding.
