---
name: checklist-failure-recovery
description: Verifies that error paths, fallbacks, retries, offline behaviour, and unrecoverable-state UX are explicit in the spec. Catches "happy-path-only" specs. Triggered by mentions of error, failure, fallback, retry, offline, network, timeout, missing app, permission denied.
---

# Checklist: failure-recovery

Verifies the spec describes **what happens when things go wrong**, not just the happy path. Aligned with Article III §4 (deterministic fallback) of [`/.specify/memory/constitution.md`](/.specify/memory/constitution.md).

Reference: [`specs/002-whatsapp-tile-return/checklists/failure-recovery.md`](specs/002-whatsapp-tile-return/checklists/failure-recovery.md).

---

## Error categories

- [ ] CHK001 Each FR involving an external action lists at least one failure mode (target app missing, permission denied, network unavailable, timeout, malformed response).
- [ ] CHK002 For each failure mode: user-visible behaviour specified (toast, snackbar, blocking dialog, silent log) — not "show error".
- [ ] CHK003 No silent failures of user-initiated actions. If an action fails, user knows.

## Fallbacks

- [ ] CHK004 If feature has fallback chains: maximum depth defined (cycle protection).
- [ ] CHK005 Fallback is **specified by the data** (e.g., `Action.fallback`), not hardcoded in dispatch.
- [ ] CHK006 If fallback also fails: terminal behaviour defined (final error UI, log, exit code).

## Retries

- [ ] CHK007 Retry behaviour explicit: who triggers (user vs auto), how many attempts, backoff.
- [ ] CHK008 No infinite retry loops without user intervention point.
- [ ] CHK009 Idempotency: actions that may be retried are safe to repeat (or made idempotent in the contract).

## Offline / degraded modes

- [ ] CHK010 If feature reads from network: offline behaviour defined (cached, blocked, degraded).
- [ ] CHK011 Stale data: TTL / freshness requirements defined.

## Permissions denied

- [ ] CHK012 Each permission required: behaviour when denied first time documented.
- [ ] CHK013 Permanent denial ("don't ask again"): explicit recovery path (settings deep-link, feature disabled with explanation).

## Recovery from invalid state

- [ ] CHK014 If persistent state can become corrupt (schema drift, partial write): recovery path defined (reset, re-fetch, notify user).
- [ ] CHK015 No "crash and restart" as the recovery strategy for handled error types.

## Diagnostics

- [ ] CHK016 Failures are observable: at minimum one diagnostic event emitted with category, not PII.
- [ ] CHK017 Failures aggregated by category (not unique per error message string), to enable rate measurement.

---

## How to apply

1. Walk every FR; ask "what's the failure mode here?".
2. Walk Edge Cases; verify they cover the categories above.
3. Failures → add FR or AC for the missing path.

## Output

Chat only — one red-only summary line per ADR-011 §5:
`checklist-failure-recovery: N/Total ✓, FAIL: CHK-XXX (short why)`.
Do NOT create `specs/<id>/checklists/failure-recovery.md`. Scratch buffer permitted, must be deleted before returning. Grey items land as edits to `spec.md` / `plan.md`.
