# Data Model: Canonical ECS foundational model (TASK-136)

Concrete Kotlin shapes. Grounds each change against the **real current code** (TASK-120/127): what survives, what is reshaped, what is deleted. Authority = TASK-136 Decision block. Pre-MVP (Article XX): no migrator, no schemaVersion bump.

---

## 1. `Entity` — free bag (REWRITE)

**Current** ([Profile.kt:16](../../core/src/commonMain/kotlin/com/launcher/preset/model/Profile.kt#L16)):
```kotlin
data class Entity(
    val id: String,
    val component: Component,          // exactly ONE
    val wizardBehavior: WizardBehavior,
    val critical: Boolean,
    val status: ComponentStatus = ComponentStatus.Pending,   // special field
    val parentId: String? = null,
)
```

**New**:
```kotlin
@Serializable
data class Entity(
    val id: String,
    val components: List<Component> = emptyList(),   // free bag (was single `component`)
    val tags: Set<Tag> = emptySet(),                 // moved off Component (was Component.tags)
    val parentId: String? = null,                    // survives: flat storage, tree computed
    val wizardBehavior: WizardBehavior = WizardBehavior.AutoApply,
    val critical: Boolean = false,
    // NO `status` field — apply-state is a LifecycleState component in `components` (CL-5)
)
```

- **Survives**: `id: String` (FR-013 — stable for serialization + zero-knowledge, not Fleks `Int`), `parentId` (TASK-127 hierarchy), `wizardBehavior`, `critical` (bundle-declared metadata).
- **Reshaped**: `component: Component` → `components: List<Component>`; `Component.tags` → `Entity.tags`.
- **Deleted**: `status: ComponentStatus` field (→ `LifecycleState` component, § 5).
- **Invariant** (CL-3, fitness-enforced): at most one component per Kotlin type in `components`.

---

## 2. `Component` — sealed interface, closed set, no `tags` (REWRITE)

**Current** ([Component.kt](../../core/src/commonMain/kotlin/com/launcher/preset/model/Component.kt)): `sealed class Component` with `abstract val tags: Set<Tag>`; 11 subtypes each declaring a `tags` default.

**New**: `sealed interface Component`, **no `tags` member**. The 11 domain-data subtypes keep their data fields verbatim, lose only the `tags` constructor parameter:

```kotlin
@Serializable
sealed interface Component

@Serializable @SerialName("AppTile")
data class AppTile(
    val packageName: String,
    val labelKey: String,
    val iconKey: String? = null,
    val pinProtected: Boolean = false,
) : Component
// … FontSize(scale) · Sos(shareLocation, autoAnswer) · Toolbar(items, layoutKey) ·
//   LauncherRole · Theme(paletteSeedHex, typographyScale, shapeStyle, darkMode) ·
//   Language(locale) · StatusBarPolicy · Workspace(layoutKey) ·
//   Flow(titleKey, layoutKey, order) · ToolbarButton(targetFlowId, labelKey, iconKey, order)
```

- **Closed set = 11 domain-data subtypes** (unchanged from TASK-127) **+ `LifecycleState`** (§ 5, a state component). Exhaustive `when` for serialization + coverage survives (compile-time safety recovery, FR-002).
- `LauncherRole` / `StatusBarPolicy` stay `data class` (were converted from `object` in TASK-127; interface members carry no state so they *could* be `data object` now, but `data class` with no fields serializes identically — keep as-is to minimise churn).
- `AdminLocked` is **NOT** added (CL-1 retracted — admin-lock is profile-level, TASK-70). Data-level composition is proven by a **test-only** `TestFlag : Component` fake in `commonTest`, never shipped.
- Adding a subtype later = additive (`@SerialName`), no edit to existing subtypes (FR-002).

**Nesting note**: subtypes currently nest inside `Component` (`Component.AppTile`). With `sealed interface`, keep them nested (`sealed interface Component { … }`) so existing references `Component.AppTile` and `@SerialName` discriminators are unchanged — zero JSON impact.

---

## 3. `Tag` — unchanged enum, now on `Entity` (SURVIVES)

`Tag` enum ([Enums.kt:36](../../core/src/commonMain/kotlin/com/launcher/preset/model/Enums.kt#L36)) — **13 values, unchanged** (8 semantic + 5 structural). Only its home moves: `Component.tags` → `Entity.tags` (FR-003).

- Zero-data marker (ECS FAQ: "a tag is a component that has no data"), compact enum encoding of Fleks `Snapshot.tags`.
- Assigned **explicitly** (CL-4): bundle declares tags at spawn; composing code sets them via `entity.withTag(...)`. No auto-derivation from components.
- One entity carries several tags at once (composition, Capability Story 1).
- **Exit ramp** (Decision): if a tag must carry data / need typed-query → promote the enum value to a marker/data `Component` — additive, same bag.

---

## 4. `Blueprint` — Bundle (REWRITE)

**Current** ([Pool.kt:6](../../core/src/commonMain/kotlin/com/launcher/preset/model/Pool.kt#L6)): `Blueprint(id, component: Component, wizardBehavior, critical, descriptionKey, requires, required)` — one component.

**New** (Bundle — verified against Bevy `Bundle`, "zero runtime significance after creation"):
```kotlin
@Serializable
data class Blueprint(
    val id: String,
    val components: List<Component> = emptyList(),   // was single `component`
    val tags: Set<Tag> = emptySet(),                 // bundle declares spawn-time tags (CL-4)
    val wizardBehavior: WizardBehavior = WizardBehavior.AutoApply,
    val critical: Boolean = false,
    val descriptionKey: String? = null,
    val requires: List<String>? = null,              // survives (TASK-126)
    val required: Boolean = false,                    // survives
)
```
Spawn **template only** — not retained as entity identity (FR-004). `Pool` unchanged in shape (`declarations: List<Blueprint>`, `byId`).

---

## 5. `LifecycleState` — state encoding (NEW; replaces `ComponentStatus` field)

**Decision made in plan** (CL-5 / FR-STATE): a single sealed **state component** in the closed `Component` set, transitioned by the System. Rejects state-tags (mutual exclusivity + `Failed` carries data). Full rationale in [plan.md § State encoding decision](plan.md).

```kotlin
@Serializable @SerialName("LifecycleState")
sealed interface LifecycleState : Component {
    @Serializable @SerialName("Pending")      data object Pending      : LifecycleState
    @Serializable @SerialName("Applied")      data object Applied      : LifecycleState
    @Serializable @SerialName("Skipped")      data object Skipped      : LifecycleState
    @Serializable @SerialName("Unverifiable") data object Unverifiable : LifecycleState  // FR-014 honest state
    @Serializable @SerialName("Failed")       data class  Failed(val reason: FailReason) : LifecycleState
}
```

- **Deletes** `enum class ComponentStatus { Pending, Applied, Failed, Skipped, Unverifiable }` ([Enums.kt:19](../../core/src/commonMain/kotlin/com/launcher/preset/model/Enums.kt#L19)) — same five semantics, now a state component.
- `FailReason` (existing) rides on `Failed`.
- At most one `LifecycleState` per entity (CL-3) → `entity.get<LifecycleState>()` is unambiguous. `Outcome.NeedsUserConfirmation` (existing, [Outcome.kt](../../core/src/commonMain/kotlin/com/launcher/preset/model/Outcome.kt)) stays; the engine maps it to `LifecycleState.Unverifiable`.
- No `Provider` (state component has no OS effector) — resolves to `NoOpProvider`, like structural subtypes.

---

## 6. `Profile` — World container (REWRITE of accessors)

**Current** ([Profile.kt:37](../../core/src/commonMain/kotlin/com/launcher/preset/model/Profile.kt#L37)): `components: List<Entity>`; `mark(id, status)` / `replaceComponent(id, newComponent)`.

**New**:
```kotlin
@Serializable
data class Profile(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,   // stays 2 (Article XX — present, not bumped)
    val basedOnPreset: String,
    val presetVersion: Int,
    val layoutKey: String,
    val entities: List<Entity> = emptyList(),          // renamed from `components` (World rows)
    val preWizardSnapshot: Profile? = null,
    val snapshotTimestamp: Long? = null,
    val unknownRefs: List<String> = emptyList(),
    val state: ProfileState = ProfileState(),
) {
    fun setState(id: String, s: LifecycleState): Profile = …    // was mark(id, ComponentStatus)
    fun with(id: String, c: Component): Profile = …             // add/replace one component in the bag
    fun without(id: String, type: KClass<out Component>): Profile = …
    companion object { const val CURRENT_SCHEMA_VERSION = 2 }
}
```
- `components` → `entities` (the World's rows). `layoutKey`, snapshots, `unknownRefs`, `ProfileState` survive.
- `mark`/`replaceComponent` (single-component ops) → bag ops `setState`/`with`/`without` built on the ecs compose primitives (§ 8).

---

## 7. Consumers reading a component (bodies rewritten, signatures mostly intact)

| Site | Was | Becomes |
|------|-----|---------|
| `ProfileQuery.byTag` etc. | `it.component.tags` | `it.tags` |
| `ProfileQuery.flows/toolbarButtons` | `(it.component as? Component.Flow)?.order` | `it.get<Component.Flow>()?.order` |
| `ProfileQuery.tilesOf` render gating | `it.status.isHiddenFromScreen()` | `it.get<LifecycleState>()?.isHidden() ?: false` |
| `ProfileFactory` spawn | `applyOverride(decl.component, …)`; one Entity | expand `decl.components` (+ inline) to flat set; `entity(id){…}` |
| `ProfileFactory.validateHierarchy` | `it.component is Component.Flow`; `entity.component as? ToolbarButton` | `it.get<Component.Flow>() != null`; `entity.get<Component.ToolbarButton>()` |
| `ReconcileEngine` | one `registry.resolve(pc.component)`; `profile.mark(id, status)` | per-component in bag; `profile.setState(id, LifecycleState.X)` |
| `PresetValidator` / `PresetDiff` | `decl.component` | `decl.components` |
| `ProfileBackedFlowRepository` | `flowEntity.component as? Component.Flow`; `when(component)` | `flowEntity.get<Component.Flow>()`; `entity.get<Component.AppTile>()` |
| `WizardScreen` | `when(pc.component)` big `when` | `when` over the entity's domain-data component (`pc.get<T>()`) |
| `PostWizardKioskApply` | `comp is StatusBarPolicy \|\| comp is LauncherRole` | `pc.get<StatusBarPolicy>() != null \|\| pc.get<LauncherRole>() != null` |

Ports **unchanged**: `Provider<T : Component>`, `ProviderRegistry.resolve(component)`, `ProfileStore`, `FlowRepository`.

---

## 8. Own ECS core primitives (`preset/ecs/`, NEW — the ~200-400 LOC, Fleks-shaped)

```kotlin
// EntityDsl.kt — spawn + compose (mirrors Fleks world.entity {} / entity[Type])
fun entity(id: String, block: EntityBuilder.() -> Unit): Entity
inline fun <reified T : Component> Entity.get(): T? = components.filterIsInstance<T>().firstOrNull()
fun Entity.with(c: Component): Entity            // add/replace same-type (upholds at-most-one-per-type)
inline fun <reified T : Component> Entity.without(): Entity
fun Entity.withTag(t: Tag): Entity ; fun Entity.withoutTag(t: Tag): Entity

// Family.kt — query matcher (mirrors Fleks world.family { all/any/none })
class Family(val all: Set<Tag>, val any: Set<Tag>, val none: Set<Tag>)
fun Profile.family(block: FamilyBuilder.() -> Unit): List<Entity>

// World.kt — World = Profile's entity bag; carries the swap seam
// TODO(ecs-fleks-migration): World internals swappable to Fleks;
// cost = Kotlin 2.0→2.4 upgrade + String→Int id remap; persistence stays ours.
```
Concrete to `Component`/`Tag` (no generic component universe — rule 4). `ProfileQuery` named selectors (§ 7) are thin wrappers over `family {}` + `get<T>()`.

---

## 9. Serialization DTO (entity-grouped, mirror Fleks `Snapshot`)

No separate DTO layer — the domain types are the `@Serializable` wire types (as today). Entity-grouped JSON, kotlinx polymorphic list. Full shape + fixtures in [contracts/profile-serialization.md](contracts/profile-serialization.md).

```json
{
  "schemaVersion": 2,
  "basedOnPreset": "simple-launcher", "presetVersion": 1, "layoutKey": "single",
  "entities": [
    { "id": "tile-wa", "parentId": "flow-calls",
      "components": [
        { "type": "AppTile", "packageName": "com.whatsapp", "labelKey": "wa" },
        { "type": "LifecycleState.Applied" }
      ],
      "tags": ["Presentation", "Tile", "Communication"] }
  ]
}
```
(`LifecycleState` variants serialize via their own `@SerialName`s; exact discriminator form pinned by the contract.)

---

## Delete / rename summary

| Action | Symbol |
|--------|--------|
| **Delete** | `enum class ComponentStatus`; `Component.tags` member; `Entity.status` field; `Entity.component`; `Blueprint.component`; `Profile.mark`/`replaceComponent` (→ bag ops) |
| **Rename** | `Profile.components: List<Entity>` → `entities` |
| **Add** | `LifecycleState` (state component); `preset/ecs/` (EntityDsl, Family, World); `Entity.get<T>()`/`with`/`without`; `Entity.tags`, `Blueprint.tags`/`components` |
| **Survive (body-only edits)** | `Tag`, `Outcome`, `FailReason`, `ValidationError`, `WizardBehavior`, `Provider`, `ProviderRegistry`, `ProfileStore`, `FlowRepository`, `ReconcileEngine` role, `ProfileFactory.validateHierarchy` |

---

<!-- NOVICE-SUMMARY:BEGIN -->
## Кратко по-русски

Конкретные Kotlin-формы. Что меняется в реальном коде:
- **`Entity`**: было «id + один компонент + поле status», стало «id + список компонентов + набор тегов + родитель». Поле `status` удалено.
- **`Component`**: было `sealed class` с полем `tags` на каждом подтипе, стало `sealed interface` без `tags` (теги уехали на сущность). 11 подтипов данных сохраняют свои поля, теряют только `tags`. Добавляется 12-й — `LifecycleState` (состояние).
- **`Tag`**: тот же enum (13 значений), только живёт теперь на `Entity`.
- **`Blueprint`**: стал «бандлом» — набор компонентов + теги, шаблон для рождения сущности, после сборки забывается.
- **`LifecycleState`**: заменяет enum `ComponentStatus` — состояние стало компонентом.
- **`Profile`**: `components` → `entities`; методы `mark`/`replaceComponent` → операции над мешком.
- **Свой ECS-core** (пакет `preset/ecs/`): `entity{}`, `get<T>()`, `with/without`, `family{}` — в форме Fleks.
Порты (`Provider`, `FlowRepository`, `ProfileStore`) и их сигнатуры не меняются.
<!-- NOVICE-SUMMARY:END -->
