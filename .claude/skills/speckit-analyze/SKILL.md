---
name: speckit-analyze
description: Spec-kit pre-implementation audit — full cross-artifact consistency, constitution re-check, all triggered checklists re-run with current artifacts, dangling-reference scan. Run after speckit-tasks completes, before any implementation starts. The "second pair of eyes" catching what slipped through speckit-clarify / speckit-plan / speckit-tasks.
---

# Orchestrator: speckit-analyze

Final consistency audit before code is written. Runs everything that earlier orchestrators ran, but on the **complete artifact set** (spec + plan + tasks + contracts + checklists). Reports a punch list; does not auto-fix.

This is the **most valuable orchestrator** — it catches drift between artifacts that individually seemed fine.

---

## When to invoke

- `tasks.md` exists, `speckit-tasks` reported success.
- Before opening implementation work or PR description.
- After significant edits to any artifact (drift detection).

## When NOT to invoke

- spec.md is the only artifact (run `speckit-clarify` instead).
- After implementation has started — too late to find architectural drift cheaply.

---

## Procedure

### Step 1 — Re-assess

Re-run `procedure-assess-spec-complexity` (spec may have changed since first pass).

### Step 2 — Re-run constitution check

Invoke `procedure-constitution-check` against current `plan.md`. Failures here are **stop-the-line** — code can't start until resolved or exception documented per Article XVII.

### Step 3 — Cross-artifact trace

Invoke `procedure-cross-artifact-trace`. Failures listed; user resolves before proceeding.

### Step 4 — Re-run all checklists

For each checklist returned by Step 1 (always-on + triggered):
1. Re-invoke. Compare against existing `specs/<id>/checklists/<name>.md`.
2. Note new failures since clarify pass (drift signals).
3. Note still-open items.

### Step 5 — Specific scans

- **Deleted-file dangling references**: for each file in plan.md "DELETE:" list, grep across `docs/**`, `specs/**`, `*.md` files. Flag mentions.
- **Wire-format files audit**: for each new file in plan.md, ensure if it's persistent / cross-process — it has `schemaVersion`.
- **Source-set placement audit**: for each new file, verify it lives in the source-set declared in plan.md (`commonMain` / `androidMain` / `iosMain`).
- **Required-context omissions**: for each `docs/adr/*`, `docs/compliance/*`, `docs/product/*` referenced in spec/plan but not linked, flag.
- **Vague language sweep**: re-grep spec for "intuitive", "smooth", "fast", "simple", "should be" without subsequent operationalisation. Flag survivors.

### Step 5b — Add novice summary

**Mandatory.** Invoke `procedure-add-novice-summary` on the `analyze-report.md` artifact to append a plain-Russian "for newcomers" section at the bottom.

### Step 6 — Verdict

```
SPECKIT-ANALYZE for specs/<id>/:

CONSTITUTION CHECK: 8/8 PASS

CROSS-ARTIFACT TRACE:
  ✓ All 18 FRs covered by tasks
  ✓ All contracts have roundtrip + backward-compat
  ⚠ "ADR-005" mentioned 4 times in spec, 1 link

CHECKLISTS:
  always-on/requirements-quality   : 16/16 ✓
  always-on/meta-minimization      : 13/13 ✓
  triggered/domain-isolation       : 16/16 ✓
  triggered/wire-format            : 18/18 ✓
  triggered/failure-recovery       : 16/17 ✓ (CHK-014 still open: corrupt-state recovery undefined)
  triggered/state-management       : N/A (no lifecycle scope per spec §1)
  triggered/security               : 22/24 ✓ (2 PII concerns)
  triggered/performance            : 20/20 ✓

SCANS:
  ✓ No dangling deleted-file references
  ✓ All wire-format files have schemaVersion
  ✓ Source-set placement consistent
  ⚠ ADR-001 referenced in spec §10.2 but not linked (1 occurrence)
  ✓ No vague-language survivors

VERDICT: READY-WITH-CAVEATS
  3 open items must be addressed or accepted-as-risk before implementation:
    1. failure-recovery CHK-014 — define corrupt-state recovery for ReturnContext cleanup
    2. security CHK-019 — confirm no PII in dispatch event logs
    3. security CHK-022 — data minimisation for Custom payload
  After resolution: cleared for implementation.
```

---

## Verdict types

- **READY** — all checks PASS, no open items. Start implementation.
- **READY-WITH-CAVEATS** — small open items, listed. User decides accept-as-risk or fix-first.
- **NOT READY** — Constitution Check FAIL or major cross-artifact gaps. Go back to plan / clarify.

---

## After analyze

The artifact set is locked. Subsequent edits should re-run `speckit-analyze` to catch drift. For micro-edits (typo, link), skip — judgement call.
