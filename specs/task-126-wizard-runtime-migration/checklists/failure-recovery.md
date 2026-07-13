# Checklist: failure-recovery — task-126 Wizard Runtime Migration

Applies Article III §4 (deterministic fallback). Ref: `specs/002-whatsapp-tile-return/checklists/failure-recovery.md`.

Spec: `specs/task-126-wizard-runtime-migration/spec.md` (Draft, 414 lines).

## Error categories

- [x] CHK001 Each FR with external action lists failure mode. FR-002 (`LauncherRole`) — user denies default-home dialog; FR-005 (`StatusBarPolicy`) — MIUI fallback documented in Edge Cases; FR-006 (`PresetValidator`) — throws `PresetValidationException`; FR-007 (`hint-pool.json`) — missing → empty list; FR-013/E2E — schema mismatch handled by regen in Phase 5.
- [ ] CHK002 User-visible behaviour on each failure. **WARN**: `PresetValidationException` is fail-fast but spec does not say what the user sees (crash screen? preset-picker reappears? admin-facing error?). Not defined for the "admin sends malformed preset" case in SEQ-3. Similarly MIUI fallback (Edge Cases) doesn't specify UX if `FLAG_FULLSCREEN` also fails.
- [x] CHK003 No silent failures of user-initiated actions. `Provider.check()`/`apply()` return `Ok`/`NeedsApply`/error; ReconcileState machine surfaces state to UI (SEQ-1). Force-close/resume is explicit (US3).

## Fallbacks

- [x] CHK004 Fallback depth. MIUI `StatusBarPolicy` fallback is a single-hop `WindowInsetsController → FLAG_FULLSCREEN` — no chain, no cycle risk.
- [x] CHK005 Fallback specified by data where applicable. `required: Boolean` field (FR-006, CL-3) is data-driven — optional components don't block home. MIUI fallback is manufacturer-detection inline (documented deviation, acceptable per Edge Cases).
- [ ] CHK006 Terminal behaviour if fallback also fails. **WARN**: If MIUI `FLAG_FULLSCREEN` also fails, spec doesn't say what happens — status bar visible? Provider returns error? Wizard blocked? Not defined.

## Retries

- [x] CHK007 Retry behaviour explicit. `Provider.check()` re-runs on every resume (FR-008, CL-3) — no cached state. `ReconcileEngine` starts from `lastCompletedStepIndex + 1` after resume, re-runs check. User-driven retry via cancel/restart wizard (US1 scenario 4).
- [x] CHK008 No infinite retry loops. `ReconcileEngine` linear pass over `wizardFlow`; interactive steps block on user input (not auto-retry). BootCheck is one-shot per boot.
- [x] CHK009 Idempotency. `Provider.check()` documented as re-runnable each resume (CL-3). `Provider.apply()` implicitly idempotent — `check()` returns `Ok` if already applied (US2 scenario 2: LauncherRole re-check skips dialog).

## Offline / degraded modes

- [x] CHK010 Offline behaviour. Spec is offline-first: `PresetBootstrap` reads bundled JSON assets; no network for wizard/boot flow. `WizardStore` is device-local (Assumptions).
- [x] CHK011 Stale data / TTL. N/A — no network reads in this feature; bundled assets are immutable per app version.

## Permissions denied

- [ ] CHK012 First-time denial documented. **FAIL**: FR-002 says `LauncherRole.apply()` sends default-home Intent. Spec does not say what happens if user cancels the system dialog — does `check()` return `NeedsApply` again? Does wizard block? Does step get marked failed? US2 covers "already default" but not "user refused".
- [ ] CHK013 Permanent denial recovery. **FAIL**: No settings deep-link path defined for permanently-denied permissions (LauncherRole, or any permission accessed via `SystemPermissionProvider` FR-018). Post-migration ProfileStore has no "permanently-denied" status.

## Recovery from invalid state

- [x] CHK014 Corrupt persistent state recovery. `PresetValidator` fail-fast at deserialization (FR-006). Cancel/restore path exists (US1 scenario 4 → `preWizardSnapshot`). FR-017 deletes legacy assets outright — no migration corruption path.
- [x] CHK015 No "crash and restart" as strategy. Force-close resume documented (US3), `Loading` state during bootstrap (CL-1). Fail-fast on invalid preset is by design, not crash-recovery.

## Diagnostics

- [ ] CHK016 Failures observable. **WARN**: Spec doesn't call out diagnostic emission for `PresetValidationException`, provider failures, or MIUI fallback engagement. No logging/telemetry FR. Given zero-knowledge / no-analytics posture this may be intentional but spec should say so.
- [ ] CHK017 Failures aggregated by category. **WARN**: same as CHK016 — no diagnostics FR at all.

---

## Summary

- **Pass**: 11/17
- **Warn**: 4/17 (CHK002 validator UX, CHK006 MIUI terminal, CHK016 diagnostics, CHK017 categorization)
- **Fail**: 2/17 (CHK012 permission denial path, CHK013 permanent denial recovery)

**Action items for plan.md**:
1. Add FR: what user sees when `PresetValidationException` is thrown (probably: fallback to preset picker with error toast).
2. Add FR: `LauncherRole` / permission-denied behaviour — step marked pending, retry on next open, settings deep-link after N refusals.
3. Add FR (or explicit Assumption): diagnostic policy — likely "no telemetry, log to logcat only" per zero-knowledge posture.
