---
name: speckit-clarify
description: Spec-kit clarification orchestrator — runs after a draft spec.md is written, before plan.md exists. Identifies grey areas, asks the user ≤5 targeted questions, writes answers into a Clarifications section of spec.md, then runs the always-on quality checklists (requirements-quality + meta-minimization) and any triggered by procedure-assess-spec-complexity. Invoke this whenever a new or significantly changed spec.md exists.
---

# Orchestrator: speckit-clarify

Bridges **spec → plan**. Catches what the human author left implicit and surfaces it before architectural decisions are baked in.

---

## When to invoke

- A `specs/<id>/spec.md` exists, no `plan.md` yet.
- A `spec.md` was significantly edited (new US added, scope shifted) — re-run.
- Before `speckit-plan` — never skip on the first pass.

## When NOT to invoke

- Spec was written by `speckit-plan` flow as a side-effect (already gone through clarify).
- Tiny spec (< 50 lines, < 3 FRs) — just run `checklist-requirements-quality` directly.

---

## Procedure

### Step 1 — Assess

Invoke `procedure-assess-spec-complexity` with the spec.md path. It returns:
- always-on checklists (`checklist-requirements-quality`, `checklist-meta-minimization`),
- triggered checklists,
- size hint.

### Step 2 — Identify grey zones

Read spec.md. Look for:

| Grey zone | Example | Action |
|-----------|---------|--------|
| Vague qualifiers | "fast", "simple", "intuitive" without metric | Ask: "What number defines fast here?" |
| Implicit assumption | "assume user has WhatsApp installed" not stated | Ask: "What if not installed?" |
| Missing edge case | No double-tap / no-network / kill-switch behaviour | Ask: "What happens when …?" |
| Open architectural choice presented as decided | "use Decompose" but spec doesn't say why | Ask: "Why this choice over X / Y?" |
| Out-of-scope ambiguity | Spec says "out of scope: X" but X is touched in scope | Ask: "Confirm boundary." |
| Wire-format without schema-version | New JSON in spec without `schemaVersion` | Ask: "What schemaVersion does this start at?" |
| One-way door without exit ramp | Decision that's hard to reverse, no §"alternatives considered" | Ask: "If we wanted to leave this choice, what would it cost?" |

### Step 3 — Ask ≤ 5 questions

**Question budget is organic, not numeric.** Per constitution Article XIX (Organic Question Budgets), do NOT pad to a target count and do NOT trim below what the spec actually needs:

- Typical range: **3–7** questions per pass.
- < 3 — fine if spec is genuinely simple; don't invent grey zones to hit a floor.
- > 8 — propose splitting into two passes with the user: «У меня N серьёзных вопросов с большим blast radius. Хочешь все сразу или разбить на два прохода?» Let user decide.
- Pick ones with the **highest blast radius** if wrong (ones that would invalidate plan.md). If five grey zones tie at high blast radius and a sixth is medium — ask five. If all seven are high blast — ask seven, not «top-5».

**Padding to a target = bug.** Trimming below organic count leaks architectural risk past clarify. Both fail the user.

Format each question as:
- **Q:** the question
- **Why it matters:** one sentence
- **Suggested default:** what you'd write if user says "you decide"

User answers in plain prose; you don't force structured input.

### Step 4 — Update spec

Add (or extend) `## Clarifications` section near top of spec.md with:

```markdown
## Clarifications

### 2026-MM-DD — Pre-plan clarification pass

| # | Question | Resolution |
|---|----------|------------|
| 1 | …        | …          |
| 2 | …        | …          |
```

Then weave the resolutions into the relevant FR / scope sections (don't leave the answer only in the Clarifications table).

### Step 5 — Run checklists

For each checklist returned by `procedure-assess-spec-complexity`:
1. Invoke `Skill` tool with that checklist name.
2. Each checklist writes / updates `specs/<id>/checklists/<name>.md`.

### Step 5b — Add novice summaries

**Mandatory.** For every artifact this orchestrator touched (updated `spec.md`, `checklists/_overview.md`, individual `checklists/<name>.md` files with open items), invoke `procedure-add-novice-summary` to append a plain-Russian "for newcomers" section at the bottom.

### Step 5c — Sync backlog AC

**Mandatory if backlog-task exists for this spec.** Invoke `procedure-sync-backlog-ac` to propagate Success Criteria marked with `[backlog]` into the Acceptance Criteria of the matching backlog-task (via MCP `backlog`). Если backlog-task'а нет — skill сам сообщит и завершится без ошибки.

### Step 6 — Report

```
SPECKIT-CLARIFY for specs/<id>/spec.md:
  Asked: 4 questions
  Updated spec.md: 4 resolutions woven into §FR-007, §Scope, §5.2, §Clarifications table
  Checklists run: requirements-quality (12/16 ✓), meta-minimization (10/13 ✓), wire-format (15/18 ✓), domain-isolation (16/16 ✓), failure-recovery (12/17 ✓)
  Open issues: 8 — see checklist files for detail
  Next step: address open checklist items, then run speckit-plan
```

---

## Heuristics

- **Don't ask questions you can answer from the spec** — re-read first.
- **Don't ask ≥3 questions about the same subject** — pick the one that resolves most.
- **One question per concern**: don't bundle "What about A and also B and C?"
- **No yes/no questions for one-way doors** — always include alternatives.
