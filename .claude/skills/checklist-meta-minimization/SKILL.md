---
name: checklist-meta-minimization
description: Anti-bloat checklist — verifies the spec doesn't add abstractions, modules, or configuration layers without current need. Enforces Article XI of constitution.md and rule 4 of CLAUDE.md ("Minimum Viable Architecture, not Minimum Viable Product"). Always runs.
---

# Checklist: meta-minimization

Catches **speculative architecture** — things added "for future flexibility" that have no current consumer. Per Article XI of [`/.specify/memory/constitution.md`](/.specify/memory/constitution.md) and rule 4 of [`CLAUDE.md`](CLAUDE.md).

Reference: [`specs/002-whatsapp-tile-return/checklists/meta-minimization.md`](specs/002-whatsapp-tile-return/checklists/meta-minimization.md).

---

## New abstractions

- [ ] CHK001 Every new interface/port has at least one concrete consumer **in this spec** (not "in spec 008 we'll need it").
- [ ] CHK002 If a new interface has only one implementation: justified by port-shape need (DI, fakes, platform asymmetry) — not by "extensibility".
- [ ] CHK003 Mediator/orchestrator/manager class is justified by data transformation, not by pass-through.
- [ ] CHK004 No custom DSL, registry, or plugin system unless simpler composition has been tried and documented as failing.

## New modules / packages

- [ ] CHK005 New gradle module satisfies at least one of Article V §3 criteria: ownership boundary, build isolation, independent enable/disable, stable API, or material testability gain.
- [ ] CHK006 If new module is added: plan answers "Why is a package not enough?" explicitly.
- [ ] CHK007 No "utils" / "common" / "helpers" dumping ground module created.

## New configuration

- [ ] CHK008 New config field has a current FR consuming it (not "future feature might use it").
- [ ] CHK009 Config field defaults documented; backward-compat policy defined; migration path documented if non-trivial structure.

## CLAUDE.md rule 4 self-test

- [ ] CHK010 **Test 1** applied: if abstraction were inlined, what would be lost? Answer documented for each new abstraction. If answer is only "future optionality" — abstraction is removed.
- [ ] CHK011 **Test 2** applied: if dependency on the other side doubled in price / was deprecated / violated privacy, how long to swap? If ≤ 1 day — seam is unnecessary.

## Removal validation

- [ ] CHK012 If spec removes existing abstractions/modules: dangling references in `docs/**`, `specs/**` audited.
- [ ] CHK013 If spec marks code "deprecated, will remove later" — there is a concrete removal task in `tasks.md` of this or next spec, not "eventually".

---

## How to apply

1. Read `## Scope` and any new types/interfaces from `spec.md`.
2. For each new entity ask the gate questions.
3. Failures → reduce scope, not justify with hypotheticals.

## Output

Chat only — one red-only summary line per ADR-011 §5:
`checklist-meta-minimization: N/Total ✓, FAIL: CHK-XXX (short why)`.
Do NOT create `specs/<id>/checklists/meta-minimization.md`. Scratch buffer permitted, must be deleted before returning. Grey items land as edits to `spec.md` / `plan.md`.
