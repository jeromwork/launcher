---
name: procedure-architecture-sourcing
description: Before building an architecturally-significant feature, source the architecture from published prior art instead of inventing it — research working systems (specs / RFCs / whitepapers / threat models), map each block to off-the-shelf lib vs. must-write, cut the ports so the volatile is swappable, then consolidate into a SoT doc under docs/architecture/ + a thin router skill. Invoke when starting a NEW architectural domain, a one-way-door decision, or a feature pulling in an external dependency / spanning multiple layers. Do NOT invoke for small two-way-door features (anti-bloat gate). Codifies the pattern proven by TASK-145 (crypto SoT) and TASK-148 (messaging SoT).
---

# Skill: procedure-architecture-sourcing — copy proven architecture, don't invent it

**Goal**: never re-derive an architecture that the industry has already solved and published. Research the prior art once, cut the seams correctly, write it into a single source of truth + a router skill — so future sessions read the SoT instead of re-thinking. This is the pattern behind `crypto.md`+`crypto` skill (TASK-145) and `messaging.md`+`messaging` skill (TASK-148).

## The significance gate (run FIRST — do NOT skip)

This procedure is heavy. Running it on a small feature is itself bloat (violates rule 4 MVA + skill `meta-minimization`). Invoke the FULL cycle **only** if the feature is **architecturally significant** — at least one of:

- **New architectural domain** (crypto, config, messaging, identity, backend topology…).
- **One-way door** (rule 3: data migration, wire-format, public contract, identity choice).
- **New external dependency / SDK** pulled toward the domain (rule 1/2 pressure).
- **Spans multiple layers** (domain + adapter + wire + server), not a local file.

If none apply → **STOP, do not run this.** Use the normal flow (mentor for discussion, spec-kit for build). Right-sizing is enforced alongside `procedure-assess-spec-complexity`.

## The five steps

1. **Prior-art research (clean-room).** Find how working systems solved it — **published** specs / RFCs / whitepapers / threat models / audits. Read the DESIGN, reimplement it as ours. **Never read copyleft (AGPL/GPL) source code to reimplement** — that taints the clean-room. Copyright protects code, not architecture; the reusable asset is the *published spec*, not the repo. Timebox: research must terminate in a decision + a build-list, not perpetual comparison.

2. **Block-mapping (build-vs-buy).** Decompose the feature into blocks. For each: is there an off-the-shelf **permissive** lib / managed component (🟢 import), a thin app-logic with only a spec-taxonomy to copy (🟡 write from the spec), or a heavy custom build / major component adoption (🔴)? Flag every copyleft trap. Maximize 🟢; minimize 🔴. State bluntly when nothing turnkey exists.

3. **Cut the seams (the anti-rewrite step).** Isolate the **volatile** (anything you might swap — vendor, server, transport, provider) behind ports; keep the **stable** (domain types + product rules) pure. The load-bearing invariant every time: **domain logic/taxonomy lives in the domain, adapters only marshal** — a feature implemented inside a vendor adapter forces a rewrite on swap. Each volatile choice gets a rule-8 exit ramp as an inline `TODO(exit-ramp)`.

4. **Consolidate into a SELF-SUFFICIENT SoT doc + a router skill.** The gold standard is [`ecs.md`](../../../docs/architecture/ecs.md): **exhaustive, non-contradictory, current, researched, complete, correctly-divided — ALL the truth lives IN the file.** Write `docs/architecture/<domain>.md`: `AI-TLDR` beacon + the full model + invariants + class/port architecture + build-vs-buy + **industry grounding with source-verified URLs** + Rejected + exit ramps + **Open questions stated COMPLETELY in-file** (like `ecs.md` §9 "Open" — options + criteria + owner, never "see TASK-X" for the answer). Split into zone files when a domain is large, but each zone file is itself complete, not a stub. Then a thin router skill on the `ecs`/`crypto`/`messaging` template (never a second copy — points at the SoT).

5. **Land the decision as change-control (rule 11) — NOT as the read-truth.** A backlog Discussion→Decision task records the decision *process* (Choice / Rationale / Applies to / Trade-offs / Exit ramp) and is the mechanism to *change* a decided invariant later (`decision-supersedes`). But the **current truth is consolidated INTO the SoT file** — a reader never opens a task to learn what was decided. Downstream feature tasks add `dependencies: [TASK-N]`; register the new doc in `INDEX.md` in the same commit.

## Guardrails
- **Gate before cycle** — significance gate (above) or you bloat.
- **Self-sufficient truth (the `ecs.md` standard) — THE load-bearing rule.** The arch-pack FILE is the complete, current architecture; a reader must **never** need to open a backlog task to learn what was decided. Tasks are ephemeral change-control/audit; they are NOT the read-surface. Writing *"the contract is TASK-X's Decision block"* as the source of an architectural truth is the anti-pattern this procedure exists to kill. Open questions are stated completely in-file (options + criteria + owner), not deferred to a task.
- **Clean-room** — published specs in, our code out; never AGPL/GPL source in.
- **Cut for swap** — volatile behind ports, taxonomy in domain (step 3), or you rebuild on every swap.
- **MVA governs CODE, not the doc** — rule 4 forbids premature *code* abstractions; it does NOT license withholding documented architecture. The research is done → capture it completely now, or context is lost and re-researched (the exact failure this procedure prevents). Completeness of the *architecture doc* is the goal; only *code* stays minimal. Do NOT stub a zone you have already researched.
- **Grounding required** — every SoT choice cites a real published source (URL), per memory `verify_architecture_primary_sources`. "Hybrid / good enough" without a source is rejected.
- **Sync rule** — change the model → update the SoT doc in the same commit; the skill is a router, never a copy.
- **Supersede, don't rot** — when a decision is replaced, mark it `> ⚠️ SUPERSEDED (date): <old> → <new>. Reason: …`, move it to the zone's Rejected section, and fix every cross-reference in the same commit. Never silently delete; never leave a live pointer to the old choice. Audited by `procedure-archpack-integrity`.

## Reference instances
- **Crypto**: `docs/architecture/crypto.md` (+ per-zone files) + skill `crypto` — TASK-145.
- **Config/launcher**: `docs/architecture/ecs.md` + skill `ecs`.
- **Messaging**: `docs/architecture/messaging.md` + `messaging-substrate.md` + skill `messaging` — TASK-148 (this procedure's own first run).

## Related skills
- `procedure-archpack-integrity` — audits the produced arch-packs for dangling refs, stale/superseded pointers, INDEX drift, and truth-in-task leaks. Run after building/editing an arch-pack.
- `mentor` — the discussion that precedes a significant decision.
- `procedure-assess-spec-complexity` — right-sizes process; this gate plugs in alongside it.
- `backlog-task-format` — the Discussion→Decision task shape (step 5).
- `wire-format` — versioning of any format the new architecture emits (owned separately).
