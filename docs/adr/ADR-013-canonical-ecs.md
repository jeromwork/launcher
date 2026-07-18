# ADR-013: Canonical ECS as the foundational launcher-config model

**Status**: Accepted
**Date**: 2026-07-18
**Source**: TASK-136 (Decision block = authority) — mentor session inside speckit-clarify TASK-69
**Supersedes**: [ADR-012](ADR-012-tagged-component-model-vs-canonical-ecs.md)
**Related**: [TASK-136 Decision](../../backlog/tasks/task-136%20-%20Decision-ECS-canonical-foundational-model.md), [spec](../../specs/task-136-ecs-canonical-foundational-model/spec.md), **[ecs.md](../architecture/ecs.md)** (single source of truth for the model)

---

## Context

ADR-012 (TASK-120/127) adopted a **tagged-component discriminated-union** model: an
`Entity` carried exactly **one** `Component`, `tags` lived on the `Component`, and apply-state
was a `status` field. ADR-012 explicitly documented that this was *not* canonical ECS and
argued canonical ECS would be premature (rule 4).

TASK-136 reverses ADR-012 on two points:

1. **Composition need** — the owner adopted canonical ECS not for a single confirmed feature
   but to **reuse a thought-through, industry-standard model** (entity / component / tag /
   system / query) and stop paying a recurring "correction tax": a novice owner + AI agents
   repeatedly having to hold "this isn't real ECS" in mind and catch drift. Tag-level
   composition (an entity carrying several markers at once) is genuine and used; data-level
   multi-component is the model's intrinsic shape, welcomed additively.
2. **Pre-release timing** — the app is unreleased (no users, no persisted prod data), so the
   switch cost is a code refactor only, not a user migration (Article XX). Cheapest possible
   moment for this one-way door.

The earlier ADR-012 motivation "admin-lock as a per-entity cross-cutting component" was
**retracted in clarify** — admin-lock is a *profile-level* server edit-lock (TASK-70), not a
per-entity flag.

## Decision

Adopt **canonical ECS (composition)** as the foundational launcher-config model, implemented
as a **small in-house core (~200–400 LOC) mirroring Fleks's API shape** (swap-compatible), not
Fleks-direct (Fleks is a game runtime and needs Kotlin 2.4.0; we are on 2.0.21).

Concrete shape:

- **Entity** = `Entity(id: String, components: List<Component>, tags: Set<Tag>, parentId: String?, wizardBehavior, critical)` — a **free bag**. No `status` field; no single `component`.
- **Component** = `sealed interface`, **closed set** of 11 data subtypes (no `tags` member) + the state component `LifecycleState`. Closed set preserves the exhaustive `when` for serialization + coverage (compile-time safety recovered).
- **Tag** = `Set<Tag>` enum on the **entity** (compact encoding of Fleks `Snapshot.tags`), assigned explicitly (CL-4). Exit ramp: promote a tag to a marker/data component when it must carry data.
- **LifecycleState** = a sealed **state component** (`Pending`/`Applied`/`Skipped`/`Unverifiable`/`Failed(reason)`) in the bag, transitioned by the System — replaces the `ComponentStatus` enum + `Entity.status` field (CL-5). Mutually exclusive + `Failed` carries data ⇒ one component slot, not state-tags.
- **Blueprint** = a **Bundle** (component set + tags), spawn template only, not retained as identity (verified against Bevy Bundle).
- **ProfileFactory** = the "spawn": a preset entry's bundle-ref expands into a flat component set (+ inline + `paramsOverride` + `parentRef`); each spawned entity gets `LifecycleState.Pending`.
- **Serialization** = entity-grouped, mirror of Fleks `Snapshot`: `{id, parentId, components:[{type,…}], tags:[…]}`. kotlinx polymorphic (`classDiscriminator="type"`), zero custom serializer. `schemaVersion` present, **value stays 2, no bump, no migrator** (Article XX pre-MVP).
- **Systems** = existing `ReconcileEngine` + `Provider`/`ProviderRegistry`, per-component dispatch; state recorded by swapping `LifecycleState`. No game-loop scheduler.
- **Own core** = `core/src/commonMain/kotlin/com/launcher/preset/ecs/` (`EntityDsl`, `Family`, `World`), Fleks-shaped vocabulary, concrete to our `Component`/`Tag`, `≤ 400 LOC` (fitness-enforced), carrying the `// TODO(ecs-fleks-migration)` swap seam.

## Consequences

- Gain: composition, a documented industry-standard model (agents/owner lean on standard ECS
  knowledge), typed `get<T>()`, canonical serialization.
- Lose: exhaustiveness-over-*combinations* (exponential, unwanted anyway) — exhaustiveness
  over the *type set* is preserved by the sealed interface.
- Cost: one big-bang pre-release rewrite (cheap now; would be a user migration after ship).
- One acknowledged tension (Article XVII deviation, plan Complexity Tracking): data-level
  multi-component has no current product consumer — consciously accepted as the model's
  intrinsic shape, exercised today by tags + real two-type composition in tests.

## Exit ramps (rule 3)

- **To Fleks runtime** if the own core proves insufficient: API already mirrors Fleks → swap
  World internals; cost = Kotlin 2.0→2.4 upgrade + `String`→`Int` id remap; persistence stays
  ours forever (wire format is ours, rule 5+13).
- **To type-grouped storage** if scale ≫ 40 entities: build a `Map<KClass, List<Entity>>`
  index at load — additive, no wire change.
- **Marker → data component** if a tag must carry data / typed query: promote the enum value
  to a `Component` — additive, same bag.
- **Revert to discriminated union** (re-collapse `components: List` → single `component`):
  viable **only while pre-release**; after ship the entity-grouped shape is frozen.
