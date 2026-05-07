---
name: procedure-constitution-check
description: Run the mandatory Constitution Check against a plan.md per Article XVI of .specify/memory/constitution.md. Checks 8 gates (Architecture, Core/System Integration, Configuration, Required Context Review, Accessibility, Battery/Performance, Testing, Simplicity) and returns pass/fail per gate with concrete remediation. Call this from speckit-plan during plan generation and from speckit-analyze before implementation.
---

# Procedure: constitution-check

Verifies that a `plan.md` includes — and passes — the 8 gates required by **Article XVI** of [`/.specify/memory/constitution.md`](/.specify/memory/constitution.md).

A `plan.md` that fails this check is **incomplete**. The skill never auto-fixes; it **reports** so the human/agent can address each gate explicitly.

---

## The 8 gates

For each gate: read the corresponding section of the plan, evaluate against the question, return PASS / FAIL / N/A with one-sentence justification.

### Gate 1 — Architecture

- Does the feature fit the layered architecture (UI / domain / data) or clearly justify deviation?
- Is any new module justified per Article V §3 (clear ownership, build isolation, independent enable/disable, stable API, testability)?
- Are boundaries explicit and minimal?

**FAIL signals**: new gradle module without justification; cross-feature dependency outside approved shared contracts; abstraction layer "for future flexibility" with no current consumer.

### Gate 2 — Core/System Integration

- Does the feature require system events (package broadcasts, boot, lifecycle)?
- If yes, are they centralized in `:core` or another approved boundary per Article VI?
- Are event contracts typed and documented?

**FAIL signals**: feature module registering its own `BroadcastReceiver`; raw Android callback (`Intent`, `PackageInfo`) leaked into feature code; event without documented frequency/threading/battery cost (Article VI §6).

### Gate 3 — Configuration

- Does this change affect profiles, schema, defaults, or migrations?
- Are validation and compatibility covered per Article VII §3 (versioning, validation, backward-compat, defaults, migration policy)?
- Per CLAUDE.md rule §5: any persisted/wire-format change carries explicit `schemaVersion`?

**FAIL signals**: schema field added/renamed without migration; persisted JSON without `schemaVersion`; profile change without test.

### Gate 4 — Required Context Review

- Which files from `docs/governance`, `docs/adr`, `docs/product`, `docs/compliance`, `docs/research`, `docs/operations` are relevant?
- Are they explicitly linked in the plan?
- For each normally relevant document omitted, is the omission explained?

**FAIL signals**: plan touches an ADR-governed area without linking the ADR; plan doesn't reference `docs/compliance/permissions-and-resource-budget.md` when adding/removing permissions.

### Gate 5 — Accessibility

- How does this feature behave for elderly users and accessibility-sensitive users (Article VIII)?
- What acceptance criteria verify that behavior?
- Tap target ≥ 56dp (project senior-safe override), contrast ≥ 4.5:1, TalkBack path documented?

**FAIL signals**: UI feature without accessibility acceptance criteria; new component below 56dp; contrast/contentDescription unspecified.

### Gate 6 — Battery/Performance

- What is the background or runtime cost?
- Is polling avoided or explicitly justified per Article IX §3 (event-driven preferred)?
- Is startup impact controlled per Article IX §4?
- Are performance-sensitive features paired with measurable targets per Article IX §8?

**FAIL signals**: new background task without justification; polling loop; new dependency on boot/package-change/widget-refresh without documented frequency, cold-start cost, memory, power.

### Gate 7 — Testing

- What contract, integration, regression, and UI tests are required (Article X §3 preferred order)?
- For wire-format changes: roundtrip + backward-compat tests written?
- For Core event handling: translation, dedup, throttling, fallback tests?
- Per CLAUDE.md rule §6: every external port has fake adapter + real adapter + DI wiring?

**FAIL signals**: test plan absent or vague ("add tests"); wire-format change without roundtrip test; new port without fake adapter.

### Gate 8 — Simplicity

- Is any abstraction speculative (Article XI §2: no abstraction "for future flexibility" without current consumer)?
- Can the design be reduced further without losing correctness?
- Per CLAUDE.md rule §4 — Test 1: if abstraction is removed and inlined, what is lost? Per Test 2: if dependency on the other side doubled in price, how long to swap?

**FAIL signals**: single-implementation interface with no clear port-shape rationale; mediator/orchestrator that just passes data through; new DSL/registry without simpler-composition-failed evidence.

---

## Output format

```
CONSTITUTION CHECK for <path/to/plan.md>:
  Gate 1 Architecture          : PASS  — single-module change, no new abstractions
  Gate 2 Core/System Integration: PASS — uses existing AppIndex via EventRouter
  Gate 3 Configuration         : FAIL  — wire-format Action lacks schemaVersion in §5.2
  Gate 4 Required Context Review: PASS — links ADR-005, decisions #6, roadmap §005
  Gate 5 Accessibility         : FAIL  — wizard tap-target spec missing
  Gate 6 Battery/Performance   : PASS  — no new background work; cold-start budget defined
  Gate 7 Testing               : PASS  — roundtrip + backward-compat + per-handler intent tests
  Gate 8 Simplicity            : N/A   — handler-registry justified by 7 providers (>3 = warranted)

OVERALL: 2 FAIL, 5 PASS, 1 N/A — plan is INCOMPLETE.

Remediation:
  Gate 3: Add schemaVersion: 1 to wire-format example in spec §5.2; add dedicated FR.
  Gate 5: Add acceptance criterion AC-W4 for AddSlotWizardScreen tap target ≥ 56dp.
```

---

## When to call

- From `speckit-plan` — after the plan body is generated, before declaring it complete.
- From `speckit-analyze` — as part of pre-implementation audit.
- Manually — when reviewing someone else's plan.

## When NOT to call

- On `spec.md` (no plan-level decisions yet) — call `procedure-assess-spec-complexity` instead.
- On `tasks.md` — call `procedure-cross-artifact-trace` instead.
