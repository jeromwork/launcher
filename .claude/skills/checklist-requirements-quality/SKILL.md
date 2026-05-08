---
name: checklist-requirements-quality
description: Standard spec-kit "Specification Quality" checklist — verifies the spec is content-complete, technology-agnostic, testable, and unambiguous. Always runs (procedure-assess-spec-complexity returns this for every spec). Apply to spec.md before allowing speckit-plan to proceed.
---

# Checklist: requirements-quality

Validates **the quality of the specification document**, not the feature behind it. This is the spec-kit baseline — every spec must pass.

Source: spec-kit canonical [`checklists/requirements.md`](https://github.com/github/spec-kit) template; mirrored in [`specs/002-whatsapp-tile-return/checklists/requirements.md`](specs/002-whatsapp-tile-return/checklists/requirements.md).

---

## Content Quality

- [ ] CHK001 No implementation details (programming languages, frameworks, vendor APIs) appear in `spec.md`. *(architecture belongs in `plan.md`)*
- [ ] CHK002 Focus is on **user value and business need**, not on the technical "how".
- [ ] CHK003 Written so a non-technical stakeholder can read and validate.
- [ ] CHK004 All mandatory sections present: User Stories, Scope (In/Out), Functional Requirements, Success Criteria.

## Requirement Completeness

- [ ] CHK005 No `[NEEDS CLARIFICATION]` markers remain.
- [ ] CHK006 Every requirement is **testable** — there is at least one observable assertion that can verify it.
- [ ] CHK007 Every requirement is **unambiguous** — no "fast", "simple", "intuitive" without operationalisation.
- [ ] CHK008 Success criteria are **measurable** (numbers, percentages, time bounds).
- [ ] CHK009 Success criteria are **technology-agnostic** (no class names, protocols, framework features).
- [ ] CHK010 All acceptance scenarios for each User Story are explicit (Given/When/Then or equivalent).
- [ ] CHK011 Edge cases are identified — at minimum: empty state, error state, retry, double-action.
- [ ] CHK012 Scope is clearly bounded — In Scope and Out of Scope are exhaustive for the area.
- [ ] CHK013 Dependencies and assumptions are explicit (other specs, external services, device capabilities).

## Feature Readiness

- [ ] CHK014 All functional requirements have clear acceptance criteria (mapped to a US or independent).
- [ ] CHK015 User scenarios cover **primary flows** — not just happy path; at minimum one error path per US.
- [ ] CHK016 Feature meets **measurable outcomes** defined in Success Criteria (no SC without an FR producing the measurement).

---

## How to apply

1. Open `spec.md`.
2. Walk the list. Mark each `[x]` if covered, `[ ]` if not, `N/A` with one-sentence reason if intentionally absent.
3. Failures — surface to caller (typically `speckit-clarify` or `speckit-analyze`).

## Output

Inline checklist into `specs/<id>/checklists/requirements.md`. If file exists, update it.
