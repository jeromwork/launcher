# Checklist: meta-minimization — analyze re-run after Worker addition

**Date:** 2026-06-28 (post round-3 owner direction: two Workers in-scope, microservice mapping)
**Trigger:** Previously 13/13 ✓ at spec-level + plan-level (round 2). Round 3 added `workers/backup/` + `workers/identity/` (T653-T670, 18 tasks). Re-run to verify rule 4 (MVA) still holds.

---

## Re-run results: 13/13 ✓

### New abstractions (CHK001-CHK004) — ✓ unchanged

No new domain ports or interfaces added in round 3. Worker code is TS, lives outside `core/keys/`. Same 4 ports (`KeyRegistry`, `RootKeyManager`, `RecoveryKeyBackup`, `AuthAvailability`) — all justified per round 2.

### New modules / packages (CHK005-CHK007) — ✓ holds with justification

- **CHK005 (`workers/backup/`)** — justified per Article V §3:
  - **Ownership boundary**: blob storage logic only, no identity ops.
  - **Independent deploy**: separate `wrangler deploy`, separate failure domain.
  - **Stable API**: `POST/GET/DELETE /backup/:stableId` (versioned contract).
  - **Future migration**: maps 1-to-1 to future Go microservice per `project_workers_microservice_mapping.md`.
- **CHK005 (`workers/identity/`)** — justified per same criteria:
  - **Ownership boundary**: custom-claim setting only, can scale to identity-link ops (multi-device, alias-recovery) without bloating backup Worker.
  - **Rule 4 Test 1** (inline check): if we put `setCustomUserClaims` into `workers/backup/`, when next identity op arrives (e.g. multi-device link, alias resolution) we either (a) keep adding to backup Worker → it becomes catch-all → migration to microservices requires splitting code = **rewrite**, or (b) create `workers/identity/` later and move claim logic → **two-step migration**, more risk than splitting upfront. → seam justified.
  - **Rule 4 Test 2**: if Firebase deprecates `setCustomUserClaims` → swap identity Worker contents (1 day); rest of system untouched. Bundled approach would force surgery in backup Worker. → seam pays for itself.
- **CHK006**: plan.md §«Architectural rule» explicitly answers «why two workers, not one package» — microservice mapping, exit ramp clarity.
- **CHK007**: No `workers/utils/` or `workers/common/` created. `workers/_shared/auth-jwt/` is a **named library** with single responsibility (JWT verification), not a dumping ground.

### New configuration (CHK008-CHK009) — ✓ unchanged

Two `BuildConfig` URLs (`RECOVERY_BACKUP_WORKER_URL`, `IDENTITY_INIT_CLAIM_WORKER_URL`) — both consumed by current code (T635, T668). Defaults documented, inline-TODO server-roadmap.

### CLAUDE.md rule 4 self-test (CHK010-CHK011) — ✓ with surfaced concerns

- **CHK010 Test 1 — `workers/identity/` with ONE endpoint**: borderline case. Currently `/init-claim` is the only consumer. Justification:
  - **If inlined into `workers/backup/`**: next identity op (multi-device link, planned in S-2 QR-pairing, or alias resolution for spec 011) forces split. Per memory `project_qr_pairing_trust_primitive.md`, identity-link operations are known to be reused — not «future optionality», but **known incoming consumer**.
  - **Verdict**: passes Test 1 — removing seam would force rewrite when 2nd identity op arrives, not just addition.
- **CHK011 Test 2 — swap cost**: if Firebase Auth deprecated → swap identity Worker contents (~1 day), backup Worker untouched. Bundled approach = 2-3 days surgery, higher risk. → seam justified.

### Removal validation (CHK012-CHK013) — ✓ unchanged

No removals in round 3. `NoOpRecoveryKeyBackup` and `RecoveryKeyBackupSelector` already removed in round 2 — references audited then.

---

## Surfaced concerns (advisory, not blocking)

1. **`workers/identity/` initial scope = 1 endpoint** — borderline premature. Mitigated by:
   - **Known 2nd consumer in pipeline** (S-2 QR-pairing identity-link, per memory note).
   - **Microservice rule** (`project_workers_microservice_mapping.md`) — architectural guardrail, not per-feature decision.
   - If 6 months pass without a 2nd endpoint → reconsider, merge back. Add to `docs/dev/project-backlog.md` as «review identity Worker scope at next touch».

2. **Two `wrangler.toml` + two `package.json` duplication** — justified by:
   - Independent deploy lifecycle (R2 binding only in backup; firebase-admin only in identity).
   - Different rate-limit policies, different secrets surface.
   - Cost: ~30 lines of TS boilerplate × 2 = ~60 lines. Cheap insurance vs. coupling cost.

3. **18 tasks for two Workers (T653-T670)** — proportionate, not bloated:
   - Track A (`backup/`): 9 tasks (scaffold + 3 endpoints + ratelimit + idempotency + 4 tests + README).
   - Track B (`identity/`): 4 tasks (scaffold + 1 endpoint + test + README).
   - Deployment + Android wiring: 5 tasks (T666-T670).
   - Ratio: ~1 task per ~50 LOC. Normal for green-field code.

4. **tasks.md = 86 tasks, file ~330 lines** — approaching but NOT past anti-pattern threshold:
   - Comparable spec sizes: TASK-51 had ~70 tasks across phases, TASK-3 (F-4) had ~60. F-5 is largest but **within precedent**.
   - All tasks tracked through single «one feature = one TASK-N» memory rule (`feedback_one_task_per_feature.md`) — splitting into TASK-6a/6b/6c would fragment backlog without semantic gain.
   - **Mitigation already in tasks.md**: 7 phases, clear trace tables (FR→T, SC→T), dependency chain documented. File is navigable.
   - If F-6 / F-7 also exceed 80 tasks → consider per-phase tasks-N.md split (precedent: none yet).

---

## Verdict: 13/13 ✓, MVA holds, no drift

All 13 checks pass. The Worker split (`backup/` vs `identity/`) is justified by microservice architectural rule (round-3 owner direction) + Test 1/Test 2 self-analysis. Tasks.md size proportionate to scope — feature is large because it touches Kotlin domain + Android adapters + Compose UI + 2 TS Workers + migration + docs, not because of bloat.
