---
name: procedure-cross-artifact-trace
description: Verify traceability across spec.md ↔ plan.md ↔ tasks.md ↔ contracts/. Catches FRs without tasks, contracts without roundtrip tests, removed files referenced by other artifacts, US without acceptance criteria. Checklists are chat-only per ADR-011 §5 (revised 2026-07-16) and are not part of the trace graph. Call from speckit-tasks (after generation) and from speckit-analyze (before implementation).
---

# Procedure: cross-artifact-trace

Cross-artifact consistency audit. Reads the full set of `specs/<id>/*` files and reports inconsistencies.

This skill is **read-only** — it does not modify artifacts. Output is a punch-list the caller addresses.

---

## Checks performed

### 1. Spec → Tasks coverage

For every `FR-NNN` or `**FR-NNN**:` in `spec.md`, find at least one task in `tasks.md` whose body or trace-link references that FR.

- **FAIL**: FR-NNN with no covering task → "FR-007 (loseless Wizard reflow) has no task — possibly forgotten."

### 2. User stories → acceptance evidence

For every `US-NNN` in `spec.md`, find at least one task that produces test evidence (UI test, e2e, smoke, contract).

- **FAIL**: US-NNN with no test task.
- **WARN**: US-NNN whose acceptance criteria mention a metric (latency, %, ≤ Nms) but no perf-checkpoint task exists.

### 3. Plan → Spec ground

For every section of `plan.md` that introduces a new module, port, abstraction, or dependency: there must be a matching FR or User Story in `spec.md`.

- **FAIL**: plan introduces `WidgetService` but spec doesn't mention widgets — "smuggled architecture" per Article XII §1.

### 4. Contracts → tests

For every file in `contracts/*.md`: there must be a task in `tasks.md` for **roundtrip** test and a **backward-compat** test (per CLAUDE.md rule §5).

- **FAIL**: contract `whatsapp-handoff.md` exists but no `T0NN — write roundtrip test for whatsapp-handoff contract`.

### 5. Checklists → spec citations

For each `[CHK-NNN]` item surfaced by a checklist skill run in the current session's chat log: it must cite a section of `spec.md` (e.g. `Spec §FR-002`). Items without citations are "free-floating" and tend to drift. Note: checklists no longer live in files per ADR-011 §5 revised — trace is against the chat log of the current session's checklist run, not against `specs/<id>/checklists/*.md`.

- **WARN**: CHK-007 has no `Spec §...` citation.

### 6. Deleted-file references

If `plan.md` says "DELETE: foo.kt", grep across all `docs/**` and `specs/**/plan.md` for references to `foo.kt` or symbols defined in it. Surface dangling references.

- **FAIL**: spec 005 plans to delete `WhatsAppLaunchabilityResolver.kt` but `docs/dev/handoff-troubleshooting.md` still references it.

### 7. Tasks → ordering

Every task that depends on another task must order them correctly (later tasks reference earlier tasks by ID, never forward).

- **FAIL**: T012 depends on T015.

### 8. Required context links

Every relevant `docs/adr/*`, `docs/product/*`, `docs/compliance/*` mentioned in spec must be **linked** (markdown link), not just named in prose.

- **WARN**: spec mentions "ADR-005" 4 times, but only the first usage is a link.

---

## Output format

```
CROSS-ARTIFACT TRACE for specs/005-action-architecture-v2/:

✓ All 18 FRs covered by tasks
✓ All 8 USs have test evidence
✗ Plan introduces ProviderHealthMonitor — not grounded in any spec FR (Article XII §1)
✗ Contract action-wire-format.md exists, no roundtrip-test task
⚠ Checklist accessibility/CHK-005 has no Spec §... citation
✓ No dangling deleted-file references
✓ Task ordering valid
⚠ "ADR-005" mentioned 4 times — 3 are bare text, no link

PUNCH LIST:
  1. Either remove ProviderHealthMonitor from plan, or add FR-019 to spec.
  2. Add task T0NN: "write roundtrip test for action-wire-format contract".
  3. Cite spec section for accessibility/CHK-005.
  4. Convert remaining "ADR-005" mentions to markdown links.
```

---

## When to call

- From `speckit-tasks` — immediately after `tasks.md` is generated.
- From `speckit-analyze` — full audit before declaring "ready for implementation".
- After significant edits to any single artifact (drift detection).

## When NOT to call

- Before `tasks.md` exists (nothing to trace).
- For pure documentation changes not tied to a spec.
