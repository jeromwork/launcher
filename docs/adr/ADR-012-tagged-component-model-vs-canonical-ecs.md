# ADR-012: Tagged-Component Model vs Canonical ECS

**Status**: Accepted
**Date**: 2026-07-16
**Source**: TASK-127 deep architecture audit (industry ECS research: Bevy, Flecs, Unity DOTS, EnTT)
**Related**: [TASK-127 spec.md](../../specs/task-127-ecs-foundation/spec.md), [TASK-127 research.md § R-6](../../specs/task-127-ecs-foundation/research.md), [TASK-120 Decision](../../backlog/tasks/task-120%20-%20Decision-Component-Preset-Profile-foundational-model.md)

---

## Context

TASK-120 introduced a `sealed class Component` hierarchy inspired by ECS terminology (Entity–Component–System). TASK-127 extends the model with `Component.tags: Set<Tag>` + `Profile.query { predicate }` and initially framed this as "ECS-native расширение".

A deep architecture audit against canonical ECS frameworks (Bevy `bevy_ecs`, Flecs, Unity DOTS Entities, EnTT) revealed:

1. **We are not canonical ECS.** Our `Component` sealed class is a **discriminated union entity** — one component per entity, all data attached. Canonical ECS = *N* small components composed onto an opaque entity ID (`entity.add<Position>().add<Velocity>().add<Player>()`).
2. **Our `Tag` enum is not canonical ECS tags.** Canonical ECS tags are zero-sized marker component types (`struct Grounded;`) queried via `With<T>` / `Without<T>` at compile-time. Our tags are enum values in a per-entity `Set<Tag>` field — closer to "labels" (Rails ActsAsTaggable, Jira labels) than to Bevy `With<T>`.
3. **The design is nonetheless legitimate** — for our scale (~20 entities, user-triggered queries, no per-frame iteration) canonical ECS would be premature complexity per rule 4 (MVA).

Calling this "ECS-native" would mislead contributors familiar with Bevy semantics (they'd expect archetype iteration, multi-component composition, `Changed<T>` filters). Wrong vocabulary = confusion. Wrong vocabulary + accidentally-canonical expectations = bad PRs.

---

## Decision

**Adopt the term "tagged-component model, ECS-inspired"** for TASK-120 + TASK-127 architecture. Explicitly document the deviation from canonical ECS and its trade-offs.

**Concrete language rules** for all docs (spec.md, plan.md, data-model.md, research.md, MENTOR-DETAIL blocks, PR descriptions, code comments):

- ✅ "tagged-component model"
- ✅ "ECS-inspired"
- ✅ "tags as classification markers"
- ❌ "ECS-native" (implies canonical ECS)
- ❌ "canonical ECS pattern" (we're not)
- ❌ "archetype" (we don't have them)
- ❌ "system iteration" (we call queries from adapters, not from systems)

**Concrete design consequences** (already applied in TASK-127):

- **Both tile-side and toolbar-side lookups are query-based.** `homeScreenTiles() = byAllTags({Presentation, Tile})`; `toolbar() = byTag(Tag.Toolbar).firstOrNull()`. **No `is Toolbar` type checks in query code paths** — one paradigm (tags), consistent.
- **`Profile.byNotTag(tag)` present in Query API** — mirrors canonical ECS `Without<T>` / Flecs `!tag` / EnTT `exclude<>`. Prevents future callers from reinventing exclusion filters ad-hoc.
- **Storage is `List<ProfileComponent>` linear scan.** Canonical ECS uses archetype/chunk storage for cache-locality. We don't need it — ~20 entities, ~2µs per query (measured), 500× under NFR-003 budget (< 1 ms). Exit ramp: indexed `Map<Tag, List<ProfileComponent>>` if we ever scale (research.md R-4).

---

## Latent one-way door

The most important consequence future maintainers must understand:

**Sealed hierarchy = one Component per entity. Cannot compose.**

If a future feature requires "any tile may temporarily gain a Cooldown/Disabled/Highlighted marker" — the current model cannot express this via `+ Component.Cooldown`. Options at that moment:

1. **Add nullable data field to every subtype** (`disabledUntil: Instant?` on AppTile, Sos, Toolbar, ...). Death by field-per-Component. Sealed hierarchy grows unboundedly.
2. **Refactor to canonical ECS** — Entity = opaque ID, N Components stored separately, `entity.get<Cooldown>()` composition. Wire format v3 → v4 breaking change. **Months of work.**
3. **Fake it via a nested map on Profile** — `Profile.overlays: Map<ComponentId, Set<OverlayComponent>>` for out-of-sealed-hierarchy modifiers. Ugly but bounded.

**Estimated cost of option 2 refactor**: 8–16 weeks (redesign wire-format, migration writer, all consumers, all tests). Wire-format bump v3 → v4 is another one-way door per rule 5.

**Trigger to watch for**: first PR whose description says "we need to add a temporary modifier to an existing Component without changing its type." That's the signal to open a decision task for canonical-ECS migration OR accept the field-per-Component death spiral.

---

## Rationale

Why accept the deviation instead of refactoring to canonical ECS now?

1. **MVP scale.** ~20 entities, user-triggered queries. Canonical ECS archetype/chunk storage designs for 10k+ entities at 60fps. We are 3–4 orders of magnitude below that. Optimising for it would be textbook premature complexity (rule 4).
2. **Sealed hierarchy has real benefits.** Kotlin's exhaustive `when` on sealed classes gives compile-time coverage checks (see `ProfileMigrationV2toV3.defaultTagsFor`). Canonical ECS's dynamic component composition loses that safety net — we'd have runtime checks or reflection.
3. **kotlinx.serialization plays well with sealed hierarchies.** JSON discriminator on `type` field is idiomatic; multi-component archetype serialisation is bespoke.
4. **No composition pressure today.** Every existing Component subtype makes sense as a discriminated union entry. `AppTile` is an atomic thing; nothing wants to bolt a modifier onto it right now.
5. **Vocabulary honesty ≠ design change.** Renaming "ECS-native" → "tagged-component model" is a 15-minute doc edit, not a refactor. It sets accurate expectations without changing a single line of production code.

---

## What we're intentionally NOT doing

Explicitly deviated from canonical ECS (in case future contributors wonder why):

- **No multi-component composition per entity.** `entity.add<Cooldown>()` doesn't work; Component is atomic.
- **No type-safe tag markers.** `With<Presentation>` compile-time filter doesn't exist; we use `Set<Tag>` with runtime enum membership.
- **No archetype/chunk storage.** Everything lives in `List<ProfileComponent>`, linear scan.
- **No `Changed<T>` / `Added<T>` query filters.** Kotlin `Flow` emissions replace change-detection semantics.
- **No Systems.** Queries called directly from adapters (`ProfileBackedFlowRepository`), not from a system scheduler.
- **No relations / hierarchy** (Flecs `ChildOf(parent)`). Composite Toolbar uses `List<ToolbarButton>` field — cheap composition where needed.

These absences are intentional. If any becomes necessary, that's the trigger for a canonical-ECS decision task.

---

## Exit ramp

To migrate to canonical ECS if the latent one-way door bites:

1. Introduce a new wire format v4: Entity = opaque UUID + N Components in a separate array. Type discriminator moves from top-level Component to per-Component-instance.
2. Write migration writer v3 → v4: every `Component` in v3's `components: List<Component>` becomes an `Entity` with one child `Component` in v4 (initial equivalence preserved).
3. Extend query API: add `entity.get<T>()`, `Query<With<T>, Without<U>>` typed filters.
4. Update all consumers (`ProfileBackedFlowRepository`, etc.) — the query surface stays similar for existing usages; new composition-requiring features get canonical shape.
5. Backward-compat: v3 readers refuse v4 (schemaVersion bump, standard rule 5 semantics).

Estimated: **8–16 weeks**. Not something we take lightly.

---

## Related decisions

- **TASK-120 Decision (2026-06 range)** — introduced `Component` sealed hierarchy. Retroactively re-framed by this ADR from "ECS entities" → "discriminated-union entities in a tagged-component model." No code change; wording only.
- **TASK-127 Decision (2026-07-15)** — extended TASK-120 with tags + query API. This ADR sharpens the terminology (and rejects `is Toolbar` in favour of `Tag.Toolbar`).
- **Future**: if canonical ECS migration is triggered, that will be its own decision task with its own ADR.

---

## Sources cited by the deep audit (2026-07-16)

- [Bevy Cheat Book — Component Storage (Table vs Sparse Set)](https://bevy-cheatbook.github.io/patterns/component-storage.html)
- [Bevy `QueryFilter` docs](https://docs.rs/bevy/latest/bevy/ecs/query/trait.QueryFilter.html) — `With`, `Without`, `Or`, `Changed`, `Added`.
- [Unity DOTS `EntityQuery` (All/Any/None)](https://docs.unity3d.com/Packages/com.unity.entities@0.0/manual/component_group.html)
- [Flecs Queries (And/Or/Not/Optional)](https://www.flecs.dev/flecs/md_docs_2Queries.html)
- [Flecs Quickstart — tags = zero-data components](https://www.flecs.dev/flecs/md_docs_2Quickstart.html)
- [EnTT `view<>` / `exclude<>`](https://skypjack.github.io/entt/)

---

## TL;DR (по-русски, для новичка и для будущего AI)

**Суть.** Мы называем нашу архитектуру **«tagged-component model, ECS-inspired»**, а не «ECS-native» — это точнее. У нас sealed hierarchy `Component` = один компонент на entity (discriminated union). В canonical ECS (Bevy, Flecs, Unity DOTS) — N компонентов на entity через композицию. Для нашего масштаба (~20 сущностей, редкие пользовательские правки) это ок.

**Конкретика, которую стоит запомнить:**
- ✅ Пишем «tagged-component model» / «ECS-inspired». ❌ Не пишем «ECS-native», «archetype», «system iteration».
- **Обе выборки (плитки и тулбар) — через теги**: `homeScreenTiles() = byAllTags({Presentation, Tile})`, `toolbar() = byTag(Tag.Toolbar).firstOrNull()`. Никаких `is Toolbar`.
- **`byNotTag(tag)` в API** — эквивалент canonical `Without<T>`. Добавлен превентивно.
- **Storage = `List<ProfileComponent>` linear scan** — 2 микросекунды на 20 элементов. Экономим 500× бюджет NFR-003.

**На что смотреть с осторожностью (latent one-way door):**
- **Первая же фича вида «любая плитка может получить Cooldown-маркер»** — не работает в текущей модели. Sealed hierarchy = один Component на entity, композиции нет.
- Три пути когда это случится: (1) добавлять nullable field на каждый субтип — death by field-per-Component; (2) рефакторить в canonical ECS — **8–16 недель**, wire-format v3→v4 breaking; (3) вложенная map `overlays: Map<ComponentId, Set<OverlayComponent>>` — уродливо но bounded.
- **Триггер**: PR с описанием «нужно добавить временный модификатор к существующему Component без смены его типа». В этот момент — открываем decision-task на canonical ECS migration.
