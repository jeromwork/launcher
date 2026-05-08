---
name: speckit-tasks
description: Spec-kit task-decomposition orchestrator — generates tasks.md from plan.md with each task traced to a spec FR or US. Verifies coverage via procedure-cross-artifact-trace. Invoke after speckit-plan completes successfully (Constitution Check PASS, plan-level checklists clean).
---

# Orchestrator: speckit-tasks

Bridges **plan → executable work units**. Decomposes plan.md into ordered, dependency-aware tasks that trace back to spec.md.

---

## When to invoke

- `plan.md` exists, Constitution Check PASS.
- No `tasks.md` yet OR major scope change demands regeneration.

## When NOT to invoke

- `plan.md` is incomplete (Constitution Check FAIL) — go back to `speckit-plan`.
- Adding 1-2 tasks to an existing tasks.md — edit directly, no full regenerate.

---

## Procedure

### Step 1 — Source

Read `spec.md`, `plan.md`, all `contracts/*.md`. Don't re-derive what's there — reference it.

### Step 2 — Decompose

Group tasks into phases per [`/.specify/templates/tasks-template.md`](/.specify/templates/tasks-template.md). Typical phases:

1. **Foundation** — gradle/DI/module setup, no behaviour.
2. **Domain types** — pure-Kotlin models, ports.
3. **Wire format / contracts** — serializers + roundtrip tests.
4. **Adapters** — platform-specific implementations + their tests.
5. **UI** — composables + UI tests.
6. **Integration** — wiring, smoke.
7. **Cleanup** — file deletions, doc updates, perf-checkpoint, smoke-checkpoint.

Each task has:
- **ID**: `T0NN` consistent with existing pattern in repo (spec 003 uses T3NN, spec 005 should use T5NN).
- **Title**: imperative ("Add ProviderId value class").
- **Trace**: `(FR-NNN, US-NNN, Plan §X.Y)` — non-optional.
- **Acceptance**: how to know it's done (compile, test name, manual check).
- **Dependencies**: `requires: T0MM` if applicable.
- **Parallel-safe marker**: `[P]` if task can run alongside others without conflict.

### Step 3 — Required-task gates

These tasks MUST appear or the spec ships incomplete:

- For every contract in `contracts/`: a roundtrip-test task and a backward-compat-test task (per CLAUDE.md §5).
- For every new port: a fake-adapter task (per CLAUDE.md §6).
- For every new module: a Konsist / lint-rule task verifying boundary (per CLAUDE.md §7 fitness functions).
- For every removed file (plan.md "DELETE" list): a deletion task and a grep-verification task.
- For every doc impacted (`docs/compliance/permissions-and-resource-budget.md`, `roadmap.md`, `docs/dev/*`): an update task.
- For UI features: a screenshot / smoke-checkpoint task (`android-emulator` skill).
- For perf-sensitive features: a `perf-checkpoint.md` measurement task.

### Step 4 — Run cross-artifact trace

Invoke `procedure-cross-artifact-trace`. Report findings.

If any FR has no covering task → add task. If any contract has no roundtrip → add task. If any plan-introduced module has no FR → escalate (potential Article XII §1 violation).

### Step 4b — Add novice summary

**Mandatory.** Invoke `procedure-add-novice-summary` on `tasks.md` to append a plain-Russian "for newcomers" section at the bottom.

### Step 5 — Report

```
SPECKIT-TASKS for specs/<id>/:
  Generated: tasks.md with 47 tasks across 7 phases
  Trace: 18/18 FRs covered, 8/8 USs have test evidence
  Gates: ✓ all wire formats have roundtrip+backcompat ✓ all ports have fakes ✓ all deletes have grep-verify
  ⚠ Plan §3.4 mentions ProviderHealthMonitor — no spec FR — added open issue (potential drift, please review)
  Next step: human review tasks.md, then run speckit-analyze before starting work
```

---

## Anti-patterns to avoid

- **Mega-tasks**: a task that takes > 1 day or touches > 5 files. Split.
- **Bundle tasks**: "Add provider system" with no breakdown. Split.
- **Vague acceptance**: "implement it correctly". Bad. Use test name or measurable.
- **Forward dependencies**: T012 says "depends on T015". Re-order.
