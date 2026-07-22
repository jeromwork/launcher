---
name: procedure-archpack-integrity
description: Keep the architecture packs (docs/architecture/*.md — the ecs.md-standard single-source-of-truth files + their router skills) internally consistent — no dangling cross-references, no live pointers to superseded decisions, no INDEX drift, and no "truth" leaking into backlog tasks. Invoke after editing/adding/removing any docs/architecture/*.md file, after superseding an architectural decision, after renaming a zone file or section, or on request ("check arch-pack integrity", "any stale arch references", "did I leave a dangling link"). Companion to procedure-decision-drift-check (which walks backlog task dependencies); this one audits the arch-pack docs themselves.
---

# Skill: procedure-archpack-integrity — no dangling / stale arch-pack references

**Goal**: the arch-pack files (`docs/architecture/*.md` + their router skills) are the single source of truth (the `ecs.md` standard). This skill catches the ways that truth silently rots: dead links, references to decisions that were superseded, INDEX drift, and truth that leaked back into backlog tasks. Run it after any arch-pack edit or decision supersession.

## The five checks

1. **Dead cross-references.** For every markdown link between arch-pack files (`[..](x.md)`, `(x.md#section)`) and every prose "see `<file>`": verify the target **file exists** and, if a `#anchor`/section is named, that the **section still exists**. A link to a renamed/moved/deleted file or a gone section is a defect. (Common cause: a zone file was split or renamed and back-references were not updated.)

2. **Live pointers to superseded decisions.** Search for the marking convention `⚠️ SUPERSEDED`. For each superseded item, verify **nothing still references the old choice as current** — the superseded item must live only in the zone's **Rejected/Superseded** section, and every other mention must point at the replacement. A body paragraph still asserting a superseded choice as truth is a defect.

3. **INDEX drift.** Cross-check [`docs/architecture/INDEX.md`](../../../docs/architecture/INDEX.md) against the actual files: every `docs/architecture/*.md` is registered in INDEX's `domains:` frontmatter + registry; every INDEX entry points to an existing file; the registry status matches the file's stated status.

4. **Truth-in-task leak (the ecs.md-standard violation).** Flag any arch-pack sentence that makes a backlog task the SOURCE of an architectural truth — patterns like *"the contract is TASK-N's Decision block"*, *"see TASK-N for the model"*, *"designed — read TASK-N"*. Tasks may be named as **history/owner** ("decided in TASK-N", "owner: TASK-N"), never as the place to LEARN the current architecture. If found, the fix is to consolidate the truth INTO the file (per `procedure-architecture-sourcing`).

5. **Router-skill sync.** For each `docs/architecture/<domain>.md` with a matching skill (`crypto`, `ecs`, `messaging`, …): verify the skill is still a thin router (trigger + pointer + guardrail invariants + sync rule), not a second copy that has drifted from the SoT. Verify the skill's reading-map links resolve.

## Marking convention (how to supersede correctly — enforced by check 2)

When a decision in an arch-pack is replaced:

```
> ⚠️ SUPERSEDED (YYYY-MM-DD): <old choice> → replaced by <new choice / file#section>. Reason: <one line>.
```

- Put the marker where the old decision was; **move the superseded content into the zone's Rejected/Superseded section** so the current body stays the single truth.
- Update **every** cross-reference to the old choice in the **same commit**.
- Never silently delete (loses the audit trail) and never leave the old choice asserted as current elsewhere.

## Output

Report per check: ✅ clean, or a list of defects as `file:line — <defect> → <fix>`. Empty = the arch-pack is consistent. This skill only **reports** (and applies the marking convention on request); it does not invent architecture — consolidation is `procedure-architecture-sourcing`.

## Related skills
- `procedure-architecture-sourcing` — builds/extends an arch-pack (self-sufficiency standard); this skill audits the result.
- `procedure-decision-drift-check` — walks backlog task `dependencies:` for superseded upstream Decisions; orthogonal (that audits tasks, this audits docs).
- Domain router skills (`crypto`, `ecs`, `messaging`) — the things check 5 keeps in sync with their SoT.
