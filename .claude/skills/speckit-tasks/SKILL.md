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

### Step 3b — `[deferred-*]` markers (mandatory for AI-session tasks)

Per CLAUDE.md Portfolio tracker §AC hybrid model: any task that **cannot be closed in the AI session** must carry an explicit `[deferred-<type>]` marker so that `pre-pr-backlog-sync` can pick it up as a separate `[auto:deferred-*]` backlog AC. Without the marker the backlog Kanban silently drifts (incident 2026-06-24 TASK-49).

| Marker | When to use |
|---|---|
| `[deferred-local-emulator]` | Task requires AVD that the AI session can't spin up reliably (e.g. composeUiTest 1.7.x needs API ≤34 per memory `reference_compose_ui_test_api_mismatch`; instrumented test that fails on current local AVD; smoke test the AI can't visually verify). |
| `[deferred-physical-device]` | Task requires a real phone (Xiaomi 11T, Huawei, OEM-specific verification) per memory `reference_testing_environment.md`. |
| `[deferred-firebase-emulator]` | Task requires Firebase emulator suite running locally and the session can't start it. |
| `[deferred-external]` | Task waits on third party (Play Console review, provider provisioning, hardware delivery, owner manual approval). |

Format inside `tasks.md`:

```markdown
- [ ] **T041** [deferred-physical-device] Manual verification on Xiaomi 11T:
       fresh install → packet capture 5 min → 0 requests to Firebase / Firestore / FCM.
       Owner runs manually; AI session does not have access. (SC-007)
```

Group deferred tasks under an explicit section header to make grep cheap:

```markdown
### Instrumented integration tests (emulator)

> **[deferred-local-emulator]** T031–T036 + T043 deferred until AVD API ≤34 is available
> on the owner's machine (see memory `reference_compose_ui_test_api_mismatch.md`).

- [ ] **T031** [deferred-local-emulator] ...
```

`pre-pr-backlog-sync` greps for these markers and emits a `[auto:deferred-<type>]` AC into the backlog task. The backlog task stays in `Verification` (or `In Progress`, if PR is not merged yet) until the deferred AC are closed.

**Anti-pattern**: writing «Acceptance: passes on emulator» without marker. The AI session marks it as `[x]` because the code compiles — but the smoke was never run. Same for physical-device tasks.

### Step 4 — Run cross-artifact trace

Invoke `procedure-cross-artifact-trace`. Report findings.

If any FR has no covering task → add task. If any contract has no roundtrip → add task. If any plan-introduced module has no FR → escalate (potential Article XII §1 violation).

### Step 4b — Add novice summary

**Mandatory.** Invoke `procedure-add-novice-summary` on `tasks.md` to append a plain-Russian "for newcomers" section at the bottom.

### Step 4c — Sync backlog AC

**Mandatory if backlog-task exists for this spec.** If `tasks.md` re-formulation touched `## Success Criteria` markers in `spec.md`, invoke `procedure-sync-backlog-ac` to propagate `[backlog]`-marked SC into the matching backlog-task via MCP. Если backlog-task'а нет — skill сам сообщит и завершится без ошибки.

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
