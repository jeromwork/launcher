# Contract: Own ECS core (Fleks-shaped World) — TASK-136

The in-house core (~200–400 LOC, NFR-003) that gives canonical-ECS semantics while mirroring Fleks's API **vocabulary** so the exit ramp (swap to Fleks) is real. **Not Fleks-direct** (OQ-1: game runtime + Kotlin 2.4.0; we're on 2.0.21). Pure Kotlin, zero Android imports (rule 1). Lives in `core/src/commonMain/kotlin/com/launcher/preset/ecs/`.

Internals are **concrete to our `Component`/`Tag`** — no generic component universe (a second consumer would be speculative, rule 4). The Fleks-shaped *vocabulary at call sites*, not generic internals, is what preserves swap-compatibility.

---

## Public surface

### Spawn + compose (`EntityDsl.kt`) — mirrors Fleks `world.entity {}` / `entity[Type]`
```kotlin
fun entity(id: String, block: EntityBuilder.() -> Unit): Entity
class EntityBuilder {           // world.entity { … }
    fun component(c: Component)  // add one (at-most-one-per-type enforced)
    fun tag(t: Tag)
    fun parent(id: String?)
    var wizardBehavior: WizardBehavior
    var critical: Boolean
}

inline fun <reified T : Component> Entity.get(): T? =        // ≈ Fleks entity[AppTile]
    components.filterIsInstance<T>().firstOrNull()

fun Entity.with(c: Component): Entity                        // add / replace same-type
inline fun <reified T : Component> Entity.without(): Entity
fun Entity.withTag(t: Tag): Entity
fun Entity.withoutTag(t: Tag): Entity
```

### Query (`Family.kt`) — mirrors Fleks `world.family { all/any/none }`
```kotlin
class FamilyBuilder {
    fun all(vararg t: Tag)   // entity carries ALL
    fun any(vararg t: Tag)   // entity carries AT LEAST ONE
    fun none(vararg t: Tag)  // entity carries NONE
}
fun Profile.family(block: FamilyBuilder.() -> Unit): List<Entity>   // ≈ world.family { … }
```
Linear scan over `entities` (~20–40, < 1 ms, NFR-002). `ProfileQuery` named selectors ([query-api.md](query-api.md)) are thin wrappers over `family {}` + `get<T>()`.

### World seam (`World.kt`)
The **World is `Profile`'s entity bag** (spec Key Entities: "Profile = World"). This file documents that mapping and carries the swap seam:
```kotlin
// TODO(ecs-fleks-migration): World internals swappable to Fleks;
// cost = Kotlin 2.0→2.4 upgrade + String→Int id remap; persistence stays ours.
```

---

## Invariants (fitness-enforced)

| Invariant | Test | Source |
|-----------|------|--------|
| At most one component per Kotlin type per entity ⇒ `get<T>()` unambiguous | `AtMostOneComponentPerTypeFitnessTest` | CL-3 / FR-015d |
| Tags assigned only explicitly (spawn / composing code); no auto-derivation | `TagConsistencyFitnessTest` | CL-4 / FR-015e |
| Core ≤ ~400 LOC; no per-frame system scheduler | LOC-budget fitness | NFR-003 / SC-011 |
| Zero Android/vendor imports in `preset/ecs/` | `checklist-domain-isolation` + source-set placement | NFR-001 / SC-008 |
| `TODO(ecs-fleks-migration)` seam present on World | grep | FR-012 |

---

## What this core deliberately does NOT have

- **No per-frame system scheduler** (Fleks's `IteratingSystem` loop) — our "systems" are `ReconcileEngine` + `Provider`, invoked on events, not every frame (FR-011). Adding a scheduler would blow the LOC budget and regress to the rejected game-runtime use-case.
- **No generic component universe** — concrete to `Component`/`Tag` (rule 4).
- **No archetype / sparse-set storage** — flat `List<Entity>`; type-grouped index is an exit ramp only if scale ever demands it (additive, no wire change).

---

## Exit ramps (rule 3)

| Ramp | Trigger | Cost |
|------|---------|------|
| Swap World internals to Fleks | own core proves insufficient | Kotlin 2.0→2.4 upgrade + `String`→`Int` id remap; persistence stays ours (wire format is ours forever, rule 5+13) |
| Type-grouped index | scale ≫ 40 entities | build `Map<KClass, List<Entity>>` at load — additive, no wire change |
| Marker/data component from a tag | a tag must carry data / typed-query | promote enum value → `Component` — additive, same bag |

---

<!-- NOVICE-SUMMARY:BEGIN -->
## Кратко по-русски

Пишем **свой маленький движок** (200–400 строк), который повторяет *словарь* игрового движка Fleks: `entity(id){ component(...); tag(...) }` чтобы создать сущность, `entity.get<AppTile>()` чтобы достать компонент, `profile.family { all(...); none(...) }` чтобы найти сущности по тегам, `entity.with(...) / without()` чтобы навесить/снять компонент. Fleks напрямую не берём — он игровой и требует новее Kotlin. Внутренности делаем под наши типы (не «универсальные»), но словарь на местах вызова — флексовский, чтобы при желании можно было переехать на Fleks (запасной выход; не бесплатный — обновление Kotlin + смена id со строки на число). Чего специально НЕ делаем: планировщик систем «каждый кадр» (у нас движок примирения работает по событиям), универсальные дженерики, хитрое хранилище. Бюджет 400 строк проверяется тестом — если превысили, значит скатились в игровой движок.
<!-- NOVICE-SUMMARY:END -->
