---
name: ecs
description: The ecosystem's configuration architecture — an ECS-shaped data model executed by a Kubernetes-style desired-state reconcile engine (per-component Terraform-style providers). Invoke whenever work touches ECS / entity / component / tag / query / family / preset / profile / pool / blueprint / ReconcileEngine / provider / LifecycleState / wizard-settings-home config, or asks "how does our config/launcher model work". Routes to docs/architecture/ecs.md (single source of truth) so the approach is never re-derived or re-decided. Cross-app — this model spans the whole ecosystem, not only the launcher.
---

# Skill: ecs — the adopted configuration architecture (router)

**This skill is a thin router, not a copy of the model.** The single source of truth is [`docs/architecture/ecs.md`](../../../docs/architecture/ecs.md). Read its **AI-TLDR** first (the beacon block + core types). **Do not re-derive the model or re-decide settled questions** — they are decided and industry-verified there. If this skill and `ecs.md` ever disagree, **`ecs.md` wins** — fix the skill.

## When this fires
Any work touching: ECS, entity, component, tag, query/family, preset, profile, pool, blueprint, `ReconcileEngine`, provider, `LifecycleState`, or wizard/settings/home configuration — in the launcher or any ecosystem app; or the question "how does our config model work / what's our approach".

## The adopted approach in one line (the beacon)
**ECS-shaped data model** (entity = free bag of components + tags, queried by type/tag) **executed by a Kubernetes-style declarative desired-state reconcile loop**, where **each component type has a Terraform-style provider doing check→apply**. Precedent: data model — Apple GameplayKit et al. (production, first-party mobile); engine — Kubernetes controllers / Terraform providers. It is an **intersection of two established patterns, not an invention**. Full detail, coverage proof, and rejected alternatives live in `ecs.md` §11.

## Guardrail invariants (never violate; authoritative list = ecs.md §9-§10)
1. **Entity = free bag of `components` + `tags`, `parentId`.** No `status` field — apply-state is a `LifecycleState` component. At most one component per Kotlin type (⇒ `get<T>()` unambiguous).
2. **Profile is self-contained for behaviour + home render; presentation (`settingsMap`) lives on the Preset**, re-read at runtime. Transfer / share / remote unit = **Profile + its Preset** (I1-I3). We mirror korge-fleks `snapshot + AssetStore` reference-by-id.
3. **Domain (`core/preset/`) has zero Android/vendor imports.** UI/VM depend on **ports only**, never the engine directly — use a purpose-shaped gateway seam (e.g. `SettingsGateway`) with the engine behind it. No `when(component)` and no Android calls in engine or UI (fitness).
4. **Versioning of Preset/Profile/Pool is owned by [`wire-format.md`](../../../docs/architecture/wire-format.md)** (skill: `wire-format`) — not restated here. Storage is flat; the screen tree is computed by queries, never nested.
5. **Do NOT adopt an external game ECS engine** (Fleks / Bevy / Unity DOTS / Ashley …) nor bring one across the Rust FFI bridge — rejected in `ecs.md` §11c (no config layer, unused per-frame scheduler, vendor-type leak violating rule 1/2). The hand-rolled core (`core/preset/ecs/`, Fleks-*named* for readability) is the **permanent, correct choice**; growth is additive (more providers / components / tags), the reconcile way.

## Hard sync rule
If you change the model, an invariant, or the class/app architecture, **update `ecs.md` in the same commit** (`ecs.md` §12). Never leave `ecs.md` behind — it is the one file the whole ecosystem reads.

## Reading map (jump straight to the section)
- Routine question → `ecs.md` AI-TLDR, stop there.
- Add a new `Component` / `Tag` → `ecs.md` §4 / §5 checklists.
- Self-containment / remote / "does Settings read the Preset?" → `ecs.md` §9 (decided — do not re-derive).
- Ports / gateway seam / DI / layering → `ecs.md` §10.
- "Why not engine X / Rust FFI?" / industry basis / beacon → `ecs.md` §11 (a/b/c).
