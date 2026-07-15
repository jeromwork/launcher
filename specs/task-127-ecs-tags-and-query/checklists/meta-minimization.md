# Checklist: meta-minimization

Applied: 2026-07-15
Spec: `specs/task-127-ecs-tags-and-query/spec.md`

## New abstractions

- [x] CHK001 Every new concept has consumer in this spec:
  - `Tag` enum → consumed by Query API (FR-005) + `homeScreenTiles()` selector (FR-006).
  - `Profile.query` → consumed by `ProfileBackedFlowRepository` (FR-006).
  - `ProfileBackedFlowRepository` → consumed by HomeScreen DI wiring (FR-007) — closes US1 regression.
- [x] CHK002 New port justified: `ProfileBackedFlowRepository` implements existing `FlowRepository` port — no new port added. Reuse.
- [x] CHK003 No new mediator class — `ProfileBackedFlowRepository` is data-transformation adapter (Profile → List<FlowDescriptor>), not pass-through.
- [x] CHK004 No custom DSL/registry/plugin. Query API is 4 vanilla selector methods + generic predicate — simplest composition.

## New modules / packages

- [x] CHK005 No new gradle module.
- [x] CHK006 N/A — no new module.
- [x] CHK007 N/A — no utils/helpers.

## New configuration

- [x] CHK008 `Component.tags` field has current FR consumer: FR-005 Query API needs it. `ComponentDeclaration.tags` override justified by FR-003 (pool.json declarations override defaults).
- [x] CHK009 Defaults documented per subtype in FR-002. Backward-compat policy = Profile v2→v3 migration writer in FR-004; pool.json is bundled build-time artifact — no runtime migration needed per Clarification Q2.

## CLAUDE.md rule 4 self-test

- [x] CHK010 Test 1 applied:
  - Inlining `Tag` → hard-coded per-Component subtype filtering (regression to ad-hoc getters per US2 rationale). Loss: extensibility for new Components without touching selectors.
  - Inlining `Profile.query` → each caller re-implements list filter — code dup. Loss: single-place query semantics.
  - Inlining `ProfileBackedFlowRepository` → HomeComponent reads ProfileStore directly, breaks port pattern (rule 1 domain isolation).
- [x] CHK011 Test 2 applied:
  - `ProfileStore` deprecation → swap = adapter body change only, ≤1 day → but adapter still needed for rule 1 (domain-infra boundary).
  - Tag enum obsolescence → additive-only per FR-001; can never remove without wire-format migration — but that's rule 5, not premature abstraction.

## Removal validation

- [x] CHK012 `ConfigBackedFlowRepository` explicitly NOT removed (FR-007: stays in code, unbound in DI). No dangling refs.
- [x] CHK013 SRV-CONFIG-DEPRECATION entry mandated in `docs/dev/server-roadmap.md` (FR-010) — concrete future task recorded per CLAUDE.md rule 8, not "eventually".

**Result**: 13/13 passed. No speculative abstraction — every new type has in-spec consumer, no new ports/modules, existing `FlowRepository` port reused.
