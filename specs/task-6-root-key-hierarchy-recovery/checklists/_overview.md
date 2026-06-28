# Checklists Overview — TASK-6 / F-5 Root Key Hierarchy + Owner Recovery

**Spec**: [spec.md](../spec.md)
**Date**: 2026-06-28
**Trigger**: `/speckit.clarify` autonomous pass

## Summary (ADR-011 red-only)

| # | Checklist | Score | Status | Failures |
|---|-----------|-------|--------|----------|
| 1 | requirements-quality | 14/16 ✓ | OPEN | CHK001 (impl names in spec — Composable/library), CHK009 (SC names Konsist/Detekt/pixel_5_api_34/Google Password Manager — not tech-agnostic) |
| 2 | meta-minimization | 13/13 ✓ | PASS | — |
| 3 | dev-experience | 21/22 ✓ (1 N/A) | PASS | — (minor caveats: DI flavor-split wording, explicit TODO(physical-device) markers, Logcat tag namespace FR, cancellation-path logging) |
| 4 | wire-format | 9/13 ✓ | OPEN | CHK003 (no named schemaVersion constant required), CHK005 (additive-field / missing-default policy unstated), CHK008 (newer-version read strategy not chosen — forward-compat), CHK018 (contracts/recovery-key-backup-v1.md referenced in US-6 AS#4 but not defined) |
| 5 | domain-isolation | 16/16 ✓ | PASS | — **provider-agnostic дизайн подтверждён** (центральная проверка по запросу владельца 2026-06-28) |
| 6 | security | 17/19 ✓ | OPEN | CHK018 (permissions-and-resource-budget.md update for drive.appdata scope not in any FR), CHK024 (android:allowBackup / data_extraction_rules.xml posture for new Keystore + DataStore artefacts not addressed) |
| 7 | failure-recovery | 15/17 ✓ | OPEN | CHK016 (diagnostic events not enumerated explicitly), CHK017 (no explicit per-category metric aggregation FR) |
| 8 | backend-substitution | 15/16 ✓ (1 N/A) | PASS | — (non-blocking note: SRV-RECOVERY-001 in server-roadmap.md was written for legacy Firestore vault; recommend one-line update for Drive App Data adapter) |
| 9 | device-self-sufficiency | 17/17 ✓ | PASS | — |
| **TOTAL** | | **137/149 ✓ = 92%** | 4 OPEN, 5 PASS | 10 open items |

## Open items priority for `/speckit.plan`

**Wire-format (CHK018 — must fix in plan)**:
- Create `specs/task-6-root-key-hierarchy-recovery/contracts/recovery-key-backup-v1.md` defining the wire format formally (referenced in US-6 acceptance scenario #4 + FR-006 / FR-023). Plan phase will own this.

**Wire-format (CHK003 / CHK005 / CHK008 — fix in spec or contract)**:
- Add named `SCHEMA_VERSION_V1 = 1` constant requirement to FR-006.
- Specify additive-field policy: «new optional fields acceptable additive; required fields = schemaVersion bump».
- Specify newer-version read strategy: «schemaVersion > known → graceful refuse with `BackupError.UnsupportedSchema`; do not attempt partial parse».

**Security (CHK018 — fix in spec)**:
- Add FR-X: «`docs/compliance/permissions-and-resource-budget.md` MUST be updated to document `drive.appdata` scope before merge.»

**Security (CHK024 — fix in spec)**:
- Add FR-Y: «`AndroidManifest.xml` MUST set `android:allowBackup="false"` for Keystore + DataStore artefacts (recovery-attempts counter, schema-version memory). data_extraction_rules.xml configured to exclude these.»

**Requirements-quality (CHK001 / CHK009 — fix in spec or move to plan)**:
- Move Composable names (`RecoveryPassphraseSetupScreen`, etc.) from FR-014/015/016 into plan.md — spec FR can say «UI screen for passphrase setup with these behaviours» tech-agnostic.
- Move Konsist/Detekt fitness-function tooling from SC-007 to plan.md — SC can stay tech-agnostic: «grep over `core/keys/src/commonMain/` for forbidden tokens returns 0 lines».
- Same for `pixel_5_api_34` (move to plan / Local Test Path is OK) and «Google Password Manager» (acceptable as it's a user-visible identifier).

**Failure-recovery (CHK016 / CHK017 — defer to plan or accept as scoped-out)**:
- Diagnostic events enumeration + per-category metric aggregation are observability concerns that typically live in plan / future observability spec. Open to defer.

## Next steps

1. Address must-fix items (CHK018 wire-format → create contract, CHK018/CHK024 security → add FRs) — either inline in spec.md or in `/speckit.plan` Phase.
2. Consider tech-agnostic rewording for requirements-quality CHK001/CHK009.
3. Run `/speckit.scenarios` (recommended — sequence diagrams for cross-device recovery, owner's mandate 2026-06-16 to proactively suggest).
4. Then `/speckit.plan`.
