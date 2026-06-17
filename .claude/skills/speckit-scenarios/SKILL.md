---
name: speckit-scenarios
description: Spec-kit scenario verification step — generates plain-language user-flow sequences from a clarified spec.md and weaves them into a `## Сценарии использования` section. Runs BETWEEN /speckit.clarify and /speckit.plan. User finds these sequences convenient to verify "is the spec heading the right direction" before planning. Owner mandate 2026-06-16 — proactively suggest this skill after every clarify, since user forgets.
---

# Orchestrator: speckit-scenarios

Bridges **clarify → plan**. Grounds abstract FRs in concrete user behaviour — so a non-technical owner can read the sequences and verify "yes, that's what I want" before committing to implementation planning.

Output language — Russian (`feedback_language_russian.md`); code, commands, identifiers — as-is.

---

## When to invoke

- A `specs/<id>/spec.md` has been through `/speckit.clarify` (Clarifications section exists, checklists ran).
- No `plan.md` yet.
- User has not yet explicitly said "skip scenarios for this spec" (some specs are too small to need them).
- **PROACTIVE**: after `speckit-clarify` finishes, **suggest this skill explicitly** to the user. Don't wait for them to remember (per memory `feedback_speckit_scenarios_proactive.md`).

## When NOT to invoke

- spec.md doesn't exist or has no User Stories yet (need /speckit.specify first).
- Section `## Сценарии использования` уже existing в spec.md (skill already ran). To re-run, user must explicitly say "regenerate scenarios".
- Spec is trivial (< 50 lines, single FR) — overkill.

---

## Procedure

### Step 1 — Identify scenario categories

Read spec.md and let the **content of the spec** drive the scenario count — not a target number. A two-FR spec needs 2-3 scenarios; a 70+ FR foundation spec may need 12-15. Stop when every US, every critical SC, and every edge case from the Edge Cases section is covered by either a top-level scenario or a trouble case inside one.

Identify candidate categories from the spec — include only those the spec actually has FRs/SCs/edge cases for:

- **Happy paths**: primary user journeys (success). Usually one per User Story. If two US share the same flow shape, fold them into one scenario instead of duplicating.
- **Failure / edge cases**: only those the spec explicitly addresses (look in Edge Cases section + FR error paths). Common candidates — include only when the spec covers them:
  - Process kill / Activity recreation
  - Network / external service unavailable
  - Permission denied
  - Bundled data corrupt / unknown version
  - User abandons mid-flow
  - Locale change / dynamic config change
- **Evolution paths**: only when the spec carries wire-format / migration / extensibility FRs:
  - App update with new step / new field
  - Adjacent app added (ecosystem reuse)
  - Forward/backward compat reads

**Anti-padding rule**: do NOT invent a failure scenario the spec doesn't have an FR for, just to hit a count. Better five tight scenarios than nine with two filler.

### Step 2 — Draft scenarios in plain Russian

For each scenario:
- **Title**: «Сценарий N — короткое имя» (e.g., «Сценарий 3 — App update с новым шагом»)
- **Context**: 1 line setup («Контекст: …»)
- **Steps**: numbered list, plain language, NO code / NO Android API names
  - Step describes "what app does" + "what user observes/does"
  - Use **★ обязательный** / **☆ опциональный** markers for wizard steps where applicable
- **What it covers**: 1 line ref to FRs / SCs that close this scenario
- **Trouble cases (sub-scenarios)**: «Trouble case N.b: …» — variations within same scenario

### Step 3 — Self-check coverage

Walk all User Stories (US-1, US-2, ...) — every US must appear in at least one scenario. If a US is missing — add a scenario for it.

Walk Success Criteria (SC-001, SC-002, ...) — every SC measurable outcome should correspond to a scenario step. If a critical SC is unscenario'd — add a scenario.

Walk Edge Cases section — every edge case = either own scenario or trouble case within an existing scenario.

### Step 4 — Show to user for validation

Present scenarios as one cohesive block. Ask:
- «Эти сценарии покрывают то, что ты ожидаешь?»
- «Какие сценарии добавить или убрать?»
- «Какие шаги внутри сценариев непонятны / технически перегружены?»

If user wants changes — iterate. Keep brief.

### Step 5 — Write to spec.md

Add (or replace) section `## Сценарии использования` near top of spec.md (after `## Контекст и цель спека`, before `## User Scenarios & Testing`). Format:

```markdown
## Сценарии использования

> Эти сценарии — концентрированный взгляд «как это будет работать в реальной жизни». 
> Читая их, можно проверить, движется ли спека в правильном направлении, 
> без необходимости погружаться в FRs.
> Каждый сценарий соответствует одному или нескольким FRs (помечено в конце сценария).

### Сценарий 1 — [Title]

**Контекст**: …

1. …
2. …
3. …

**Что закрывает**: FR-NNN, SC-NNN.

**Trouble case 1.b**: …

---

### Сценарий 2 — …
```

### Step 6 — Verify cross-references

After writing, do a final sanity pass:
- Every scenario has `**Что закрывает**` line citing concrete FR/SC IDs.
- No scenario invents behaviour not present in FRs.
- No scenario references concepts outside this spec's scope (those are cross-spec dependencies — note them explicitly).

### Step 7 — Report

```
SPECKIT-SCENARIOS for specs/<id>/spec.md:
  Scenarios written: N (M happy, K failure, L evolution)
  All US covered: yes/no
  All critical SC covered: yes/no
  Cross-spec dependencies noted: [spec-X behaviour, ...]
  Next step: /speckit.plan
```

---

## Heuristics

- **Plain language only.** No `WizardCheckpointStore.load()`, no `Intent.startActivity()`. Translate every code-like reference to "what app does" prose.
- **Brevity.** 5-10 sentences per scenario. Owner reads these to orient — long scenarios defeat the purpose.
- **Scenario count is derived, not prescribed.** Count is whatever the spec needs: every US covered, every critical SC measurable from a step, every edge case from the Edge Cases section addressed. Could be 3 for a small spec, 12 for a foundation spec. Do not pad to hit a number; do not trim if coverage is real.
- **Trouble cases preferred over new scenarios.** If a variation can fit inside existing scenario as "trouble case N.b" — do that. A trouble case is cheaper to read and keeps related behaviour co-located.
- **Senior-friendly framing.** If app has senior persona (бабушка), scenarios should mention them naturally; setup-time scenarios mention admin where setup is done by admin.
- **Required/Optional markers.** For any wizard / multi-step flow, use ★ / ☆ markers so owner sees the criticality structure.

---

## Output

- Updated `specs/<id>/spec.md` with `## Сценарии использования` section.
- Short report at end of orchestrator run.
- No separate file — section lives in spec.md.

---

## Relationship to other skills

- **`speckit-clarify`** — runs before. Produces clarified spec with Clarifications section.
- **`speckit-plan`** — runs after. Reads sequences as grounding for implementation plan.
- **`speckit-analyze`** — final gate. Can use scenarios as additional check: every scenario step traced to FR + test.

---

## Proactive invocation pattern

Per memory `feedback_speckit_scenarios_proactive.md`:
- After `speckit-clarify` completion: end response with «Следующий рекомендуемый шаг — `/speckit.scenarios`. Запустить?»
- After major spec.md update: same prompt.
- If user says «давай сразу `/speckit.plan`» — flag once, but don't block: «ОК, plan без сценариев. Если потом захочешь — `/speckit.scenarios` можно запустить позже.»
- Skip the prompt if `## Сценарии использования` section already exists in spec.md.
