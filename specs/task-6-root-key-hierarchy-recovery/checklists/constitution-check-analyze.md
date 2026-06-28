# Constitution Check — speckit.analyze re-run

**Plan**: `specs/task-6-root-key-hierarchy-recovery/plan.md`
**Date**: 2026-06-28 (analyze phase, after rounds 2+3 owner direction)
**Constitution**: `.specify/memory/constitution.md` v1.10 (Article XIV §7 added 2026-06-28)
**Procedure**: `.claude/skills/procedure-constitution-check/SKILL.md`

---

## Verdicts per gate

| # | Gate | Verdict | Reasoning |
|---|------|---------|-----------|
| 1 | Architecture | **PASS** | KMP module `core/keys/` with ports in `commonMain`, Android adapters in `androidMain`. Worker split (`workers/backup/` + `workers/identity/`) mirrors future Go microservice boundaries (per memory `project_workers_microservice_mapping.md`); each Worker = one future service, not bundled features. No vendor SDK types in `commonMain` API. Adapter swap (Worker → own-server) does not touch `commonMain`. Boundaries explicit and minimal. |
| 2 | Core / System Integration | **PASS** | Reuses F-CRYPTO `SecureKeystore` (TEE) and F-4 `AuthProvider`. HTTPS to Worker via standard OkHttp — not a system integration. No new broadcasts / services / receivers / content providers. Lazy-init `RootKeyManager` adds no startup work (Article IX §4). |
| 3 | Configuration | **N/A** | F-5 is foundation infrastructure, not user-facing profile/configuration. Wire-format `RecoveryKeyBackupBlob` carries `schemaVersion=1` from first commit (CLAUDE.md rule 5). Contract documented in `contracts/recovery-key-backup-v1.md`. |
| 4 | Required Context Review | **PASS** | All relevant docs linked in plan §Required Context Review: constitution Articles I-XIX incl. Article XIV §7, CLAUDE.md rules 1-16, `docs/dev/server-roadmap.md` SRV-RECOVERY-001 (updated 2026-06-28), specs 016/017/018/019, decisions (deferred-cloud, f4-identity), ADR-011, and ≥5 relevant memory files. No governed doc omitted without justification. |
| 5 | Accessibility | **PASS** | Three Compose screens (Setup / Entry / Fallback) use Material 3 with `autofillHints`, tap-target ≥ 56dp (senior-safe override per Article VIII), TalkBack labels on inputs + spinners, no plaintext passphrase echo. Full validation deferred to `checklist-accessibility` + `checklist-elderly-friendly` at tasks phase (acceptable per Article XVI). |
| 6 | Battery / Performance | **PASS** | Argon2id (heavy work) runs only at setup/recovery — not on launch or per-push. HKDF sub-ms. Single HTTPS POST per setup (3 retry with back-off). No background workers / polling / boot receivers. Lazy-init avoids cold-start hit. SC-010 ≤3s P95 target on emulator API 34 + `[deferred-physical-device]` Xiaomi 11T benchmark. |
| 7 | Testing | **PASS** | Every port has fake adapter (`FakeKeyRegistry`, `FakeRootKeyManager`, `FakeRecoveryKeyBackup`, `FakeAuthAvailability`) — CLAUDE.md rule 6. Wire-format has roundtrip + backward-compat tests + fixture v1. Provider-agnostic fitness test. Konsist grep guard for forbidden vendor tokens in `core/keys/commonMain` (SC-007). Worker side: vitest + miniflare for JWT / idempotency / R2 roundtrip. Cross-device + Autofill GPM gated as `[deferred-physical-device]`. UI tests `[deferred-local-emulator]` due to composeUiTest API 35+ mismatch (documented, not silent). |
| 8 | Simplicity | **PASS** | `KeyRegistry` justified by 3 known consumers (config / contacts / photos) — removal = rewrite (rule 4 test 1). `RecoveryKeyBackup` port justified by SRV-RECOVERY-001 exit ramp (Worker → own-server = single-file swap). `AuthAvailability` port justified — multiple future provider adapters (Email / Phone) expected. Round-2 owner pushback **removed** `NoOpRecoveryKeyBackup` and `RecoveryKeyBackupSelector` — anti-abstraction discipline visibly applied. No mediators or single-implementation interfaces remaining. |

**Overall**: **8/8 PASS** (with Gate 3 = N/A — appropriate for foundation feature).

---

## Article XIV §7 (new, 2026-06-28) — explicit check

Five clauses applied across plan + spec:

- **(a) Opaque identifiers**: `stableId` is a UUID (F-4 decision), not Google `sub` / email / phone. Worker sees opaque UUID only. ✓
- **(b) Ciphertext + routing metadata only**: `RecoveryKeyBackupBlob` = ciphertext + KDF params + schemaVersion. No plaintext PII in blob. ✓
- **(c) No cross-user correlation**: `/backup/{stableId}/v1.json` is per-user; no shared rooms / shared addresses. Identity-link operations isolated in **separate** `workers/identity/` Worker (split decision round 3). ✓
- **(d) Access logs as privacy surface**: Worker paths contain only opaque UUID + timestamp. Cloudflare access logs cannot reconstruct relationships from path + IP + timestamp alone (mapping `stableId → identity` stays in Firestore, not in Worker logs). ✓
- **(e) Worst-case provider assumption**: Plan explicitly states (Constitution Check §Article XIV) that Worker design assumes Cloudflare sees every request and could be subpoenaed; minimal data shape yields least-useful disclosure. R-9 risk row formalizes this. ✓

---

## Drift since initial plan.md Constitution Check

Initial plan.md §Constitution Check (round 1, Drive App Data design) → re-run (round 3, two-Worker split design). Three substantive changes:

### Drift 1 — Storage backend pivot (Drive → Worker, round 2, 2026-06-28)

- **Was**: Google Drive App Data folder (`drive.appdata` OAuth scope) as recovery backup MVP.
- **Now**: Cloudflare Worker `workers/backup/` + R2 storage.
- **Constitution impact**: Improves Gate 4 (no `drive.appdata` permission needed — closes security CHK018 moot); strengthens Gate 8 (removes `NoOpRecoveryKeyBackup` + `RecoveryKeyBackupSelector` abstractions that existed to handle non-GMS / no-Drive cases — single Worker adapter works on all network-reachable devices regardless of GMS). Improves Article XIV §7 alignment (we control wire-format and access-log shape; Drive shape was vendor-imposed).

### Drift 2 — Two-Worker microservice split (round 3, 2026-06-28)

- **Was** (early round 3): Single `workers/backup/` Worker handles both blob storage AND custom-claim setting via Firebase Admin SDK.
- **Now**: Split into `workers/backup/` (R2 blob CRUD) + `workers/identity/` (custom-claim setting, identity-link ops). Architectural rule added: each `workers/<name>/` mirrors one future Go microservice; do NOT bundle features.
- **Constitution impact**: Reinforces Gate 1 (Architecture) — preserves clean migration boundaries to own-server. Reinforces Article XIV §7 (c) — identity-link operations isolated from blob-storage access patterns, reducing cross-user correlation surface in Cloudflare access logs. Adds ~11 in-scope tasks (T653-T670) but scope stays under single backlog item TASK-6 per owner direction.

### Drift 3 — Article XIV §7 added to constitution (2026-06-28)

- **Was**: Constitution v1.9 (no explicit server-side data-minimization article).
- **Now**: Constitution v1.10 with Article XIV §7 (server-side data minimization, anti-traceability by design) — five clauses (a)-(e).
- **Constitution impact**: New gate dimension surfaced. Plan §Constitution Check explicitly addresses all five clauses (see above). Spec.md has dedicated §Privacy / data minimization — design note section. No FAIL — design already aligned (opaque UUID identity from F-4, ciphertext-only blob from initial design, separate endpoints from microservice split). Drift handled before merge.

### Drift 4 — Backlog scope unification

- **Was** (mid-discussion): Worker artifact proposed as separate backlog item (TASK-X) parallel to F-5 Android work.
- **Now**: All Worker work in-scope of TASK-6 (per owner direction «делаем в этой же таске», memory `feedback_one_task_per_feature.md`). Tracked in tasks.md Phase 4.
- **Constitution impact**: None on the 8 gates. Affects only Backlog discipline (CLAUDE.md portfolio rules). One feature = one TASK = one PR preserved.

---

## Summary

`constitution-check (analyze re-run): 8/8 PASS, no FAIL — drift from initial Constitution Check (Drive→Worker pivot, microservice split, Article XIV §7 addition) reviewed and handled in updated plan.md`
