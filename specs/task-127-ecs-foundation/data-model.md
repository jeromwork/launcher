# Data Model: ECS Foundation (TASK-127)

**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Research**: [research.md](research.md)

Concrete field shapes for implementation. Complements `docs/architecture/preset-model.md` (two-dimensions doc).

**Revision history**:
- 2026-07-16 (audit #2): rewritten from real `Component.kt` / `Profile.kt` — an earlier revision sketched a fictional hierarchy that never existed in code.
- 2026-07-16 (scope expansion Q7-Q10): **hierarchy** (`parentId`), **new entity types** (Workspace/Flow/ToolbarButton), **`Unverifiable` status**, **ECS rename** (`ProfileComponent`→`Entity`, `ComponentDeclaration`→`Blueprint`).

---

## Mental model: ECS ≈ database table

| Database | ECS | Here |
|---|---|---|
| table | World | `Profile` |
| row | Entity | `Entity` (one item in `Profile.components`) |
| columns | Components | `Component.AppTile(packageName, labelKey)` |
| `WHERE tag='Tile'` | Query | `byTag(Tag.Tile)` |
| foreign key `parent_id` | `Parent`/`Children` | **`Entity.parentId`** |
| stored procedure | System | `ReconcileEngine` + `Provider` |

Storage stays **flat**. The tree is *computed* by queries, never nested in the wire format (research.md R-7).

---

## Tag (new enum) — 13 values

Location: `core/src/commonMain/kotlin/com/launcher/preset/model/Enums.kt` (existing file; `@Serializable` like its neighbours).

```kotlin
@Serializable
enum class Tag {
    // Semantic domain (what the component is about)
    Presentation,    // visible to owner on some UI surface
    Appearance,      // visual override (fontScale, theme, ...)
    System,          // OS-level settings (rotation lock, kiosk, launcher role)
    Safety,          // safety-related (SOS, emergency call routing)
    Capabilities,    // permissions, features, integrations
    Communication,   // call, SMS, messenger, contact list
    Accessibility,   // WCAG / TalkBack / senior-safe overrides
    Emergency,       // triggered in emergency (SOS, panic)

    // Structural role (what part of the screen it is) — FR-001, Q7
    Tile,            // renders in a flow's tile grid
    Toolbar,         // the bottom toolbar container
    Workspace,       // screen root
    Flow,            // one tab/page inside a workspace
    ToolbarButton,   // one button inside a toolbar
}
```

**Additive-only** per rule 5, with the honest caveat (contract § Forward compat): adding a value is safe for writers but an *older reader* fails loud on an unknown enum name. Fine while artifacts are same-device/same-binary; lenient reader required before cross-device exchange (TASK-131).

---

## Entity (was `ProfileComponent`) — FR-011, FR-015

Location: `core/src/commonMain/kotlin/com/launcher/preset/model/Profile.kt`.

```kotlin
@Serializable
data class Entity(
    val id: String,
    val component: Component,
    val wizardBehavior: WizardBehavior,
    val critical: Boolean,
    val status: ComponentStatus = ComponentStatus.Pending,
    /** FR-011: hierarchy by reference. null = root. Tree is computed, never nested. */
    val parentId: String? = null,
)
```

Rename impact: `ProfileComponent` appears in **51 places / 14 files** (engine, ports, fakes, wizard UI, tests). Mechanical IDE rename; wire format untouched (Kotlin class names never appear in JSON — only `@SerialName` discriminators inside `Component` do).

`Profile` itself is unchanged except the element type:

```kotlin
@Serializable
data class Profile(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,   // stays 2
    val basedOnPreset: String,
    val presetVersion: Int,
    val layoutKey: String,          // legacy fallback for profiles without Workspace/Flow
    val components: List<Entity> = emptyList(),
    val preWizardSnapshot: Profile? = null,
    val snapshotTimestamp: Long? = null,
    val unknownRefs: List<String> = emptyList(),
    val state: ProfileState = ProfileState(),
)
```

`Profile.layoutKey` keeps working for the degenerate one-flow case (US-1) — marked `// TODO(layout-key-migration)`: once every preset declares a `Workspace`+`Flow`, the field is removable (FR-013).

---

## Component subtypes — 11 total (8 existing + 3 new)

Location: `core/src/commonMain/kotlin/com/launcher/preset/model/Component.kt`.

```kotlin
@Serializable
sealed class Component {
    abstract val tags: Set<Tag>          // FR-002: abstract, defaulted per subtype

    // ---- existing 8 ----
    @Serializable @SerialName("AppTile")
    data class AppTile(
        val packageName: String,
        val labelKey: String,
        val iconKey: String? = null,
        val pinProtected: Boolean = false,
        override val tags: Set<Tag> = setOf(Tag.Presentation, Tag.Tile),
    ) : Component()

    @Serializable @SerialName("FontSize")
    data class FontSize(
        val scale: Float,
        override val tags: Set<Tag> = setOf(Tag.Appearance, Tag.Accessibility),
    ) : Component()

    @Serializable @SerialName("Sos")
    data class Sos(
        val shareLocation: Boolean = true,
        val autoAnswer: Boolean = true,
        override val tags: Set<Tag> = setOf(Tag.Presentation, Tag.Tile, Tag.Safety, Tag.Emergency),
    ) : Component()

    /** Container only — its buttons are separate ToolbarButton entities (parentId = this id). */
    @Serializable @SerialName("Toolbar")
    data class Toolbar(
        val items: List<String> = emptyList(),   // legacy; superseded by ToolbarButton children
        val layoutKey: String,
        override val tags: Set<Tag> = setOf(Tag.Presentation, Tag.Toolbar),
    ) : Component()

    // object → data class: objects cannot carry an overridable constructor default.
    // Wire-compatible: `{"type":"LauncherRole"}` still deserializes (all params defaulted).
    @Serializable @SerialName("LauncherRole")
    data class LauncherRole(
        override val tags: Set<Tag> = setOf(Tag.System),
    ) : Component()

    @Serializable @SerialName("Theme")
    data class Theme(
        val paletteSeedHex: String,
        val typographyScale: TypographyScale,
        val shapeStyle: ShapeStyle,
        val darkMode: Boolean,
        override val tags: Set<Tag> = setOf(Tag.Appearance),
    ) : Component()

    @Serializable @SerialName("Language")
    data class Language(
        val locale: String,
        override val tags: Set<Tag> = setOf(Tag.System),
    ) : Component()

    // object → data class (same reason as LauncherRole).
    // No read-back API on Android → provider returns NeedsUserConfirmation → status Unverifiable.
    @Serializable @SerialName("StatusBarPolicy")
    data class StatusBarPolicy(
        override val tags: Set<Tag> = setOf(Tag.System),
    ) : Component()

    // ---- new 3 (FR-013, Q7) ----

    /** Screen root. Children: Flow entities + one Toolbar entity. */
    @Serializable @SerialName("Workspace")
    data class Workspace(
        val layoutKey: String = "single",
        override val tags: Set<Tag> = setOf(Tag.Presentation, Tag.Workspace),
    ) : Component()

    /** One tab/page. Owns its grid (layoutKey moved here from Profile) and its order. */
    @Serializable @SerialName("Flow")
    data class Flow(
        val titleKey: String,
        val layoutKey: String = "2x3",
        val order: Int = 0,
        override val tags: Set<Tag> = setOf(Tag.Presentation, Tag.Flow),
    ) : Component()

    /** One toolbar button; switches to [targetFlowId]. parentId = the Toolbar entity. */
    @Serializable @SerialName("ToolbarButton")
    data class ToolbarButton(
        val targetFlowId: String,
        val labelKey: String,
        val iconKey: String? = null,
        val order: Int = 0,
        override val tags: Set<Tag> = setOf(Tag.Presentation, Tag.ToolbarButton),
    ) : Component()
}
```

**Design constraint**: every subtype MUST declare non-empty default `tags` — enforced by `ComponentTagsFitnessTest` (reflection walk; dummy values for required params like `AppTile.packageName`).

**Pool override**: `Blueprint` embeds `component: Component` directly, so a `pool.json` declaration overrides tags by including `"tags": [...]` **inside** its embedded component object. No new field on `Blueprint` (rule 4 MVA).

---

## Blueprint (was `ComponentDeclaration`) — FR-015

Location: `core/src/commonMain/kotlin/com/launcher/preset/model/Pool.kt`. Fields unchanged; rename only (42 usages / 13 files).

```kotlin
@Serializable
data class Blueprint(
    val id: String,
    val component: Component,
    val wizardBehavior: WizardBehavior = WizardBehavior.AutoApply,
    val critical: Boolean = false,
    val descriptionKey: String? = null,
    val requires: List<String>? = null,
    val required: Boolean = false,
)
```

`Pool.declarations: List<Blueprint>` — field name kept (`declarations`) to avoid touching `pool.json` key names (wire format).

---

## ComponentStatus — 5 values (FR-014)

```kotlin
@Serializable
enum class ComponentStatus {
    Pending,        // not attempted yet
    Applied,        // applied AND verified
    Failed,         // apply attempted, failed
    Skipped,        // user declined / not applicable
    Unverifiable,   // NEW: applied per user's word, OS gives no read-back
}
```

## Outcome — new variant (FR-014)

```kotlin
sealed class Outcome {
    object Ok : Outcome()
    object NeedsApply : Outcome()
    data class Failed(val reason: FailReason) : Outcome()
    object Unsupported : Outcome()
    /** NEW: intent fired; OS exposes no read-back. Ask the human, then record Unverifiable. */
    object NeedsUserConfirmation : Outcome()
}
```

**Engine rules** (FR-014):
- `Outcome.NeedsUserConfirmation` from `apply()`/`check()` → `ReconcileEngine` sets `ComponentStatus.Unverifiable` (never `Applied`).
- `RunMode.BootCheck` **skips** entities whose status is `Unverifiable` (no infinite re-nagging on every cold start).
- Re-verification only via `RunMode.Single` (explicit Settings action).

---

## Query API (FR-005, FR-012)

Location: `core/src/commonMain/kotlin/com/launcher/preset/query/ProfileQuery.kt` (new file). All extension functions; eager `List`; linear scan.

```kotlin
// ---- base ----
fun Profile.query(predicate: (Entity) -> Boolean): List<Entity> = components.filter(predicate)

// ---- tag selectors (FR-005) ----
fun Profile.byTag(tag: Tag): List<Entity> = query { tag in it.component.tags }
fun Profile.byAllTags(tags: Set<Tag>): List<Entity> = query { it.component.tags.containsAll(tags) }
fun Profile.byAnyTag(tags: Set<Tag>): List<Entity> = query { it.component.tags.any(tags::contains) }
fun Profile.byNotTag(tag: Tag): List<Entity> = query { tag !in it.component.tags }

// ---- hierarchy selectors (FR-012) ----
fun Profile.children(parentId: String): List<Entity> = query { it.parentId == parentId }
fun Profile.roots(): List<Entity> = query { it.parentId == null }
fun Profile.workspace(): Entity? = byTag(Tag.Workspace).firstOrNull()

fun Profile.flows(): List<Entity> =
    byTag(Tag.Flow).sortedBy { (it.component as? Component.Flow)?.order ?: 0 }

fun Profile.toolbar(): Entity? = byTag(Tag.Toolbar).firstOrNull()

fun Profile.toolbarButtons(): List<Entity> =
    byTag(Tag.ToolbarButton).sortedBy { (it.component as? Component.ToolbarButton)?.order ?: 0 }

/** Tiles of one flow. Render gating: Failed/Skipped never reach the screen. */
fun Profile.tilesOf(flowId: String): List<Entity> =
    query { it.parentId == flowId && it.component.tags.containsAll(setOf(Tag.Presentation, Tag.Tile)) }
        .filterNot { it.status == ComponentStatus.Failed || it.status == ComponentStatus.Skipped }

/**
 * Tiles for the home screen. flowId = null → tiles of the first flow, or (degenerate
 * legacy profile with no Flow entities) all tiles regardless of parent.
 */
fun Profile.homeScreenTiles(flowId: String? = null): List<Entity> {
    val target = flowId ?: flows().firstOrNull()?.id
    if (target != null) return tilesOf(target)
    return byAllTags(setOf(Tag.Presentation, Tag.Tile))
        .filterNot { it.status == ComponentStatus.Failed || it.status == ComponentStatus.Skipped }
}
```

**Render gating** (audit #2 finding): tags say *what* a component is; `status` says whether this device managed to apply it. `Failed`/`Skipped` tiles are excluded — a senior user must not face a dead button. `Pending` renders (benign transient). `Unverifiable` renders (the user said it is on).

**Mental model** (ADR-012): label selectors (Kubernetes `matchLabels`), not canonical ECS archetype filtering. `byNotTag` is a plain predicate, not Bevy `Without<T>`.

**Performance**: NFR-003 < 1 ms at MVP scale (~20-40 entities). Linear scan; index exit ramp in research.md R-7.

---

## ValidationError — hierarchy variants (FR-016)

Added to the existing sealed class (`core/src/commonMain/kotlin/com/launcher/preset/model/ValidationError.kt`):

```kotlin
data class DanglingParentRef(val entityId: String, val missingParentId: String) : ValidationError()
data class CircularParentRef(val cycle: List<String>) : ValidationError()
data class DanglingTargetRef(val buttonId: String, val missingFlowId: String) : ValidationError()
```

Each gets a `toI18nKey()` branch (`validator.error.dangling_parent_ref`, `…circular_parent_ref`, `…dangling_target_ref`). Checked in `ProfileFactory.create` (profile assembly) and reachable for authoring-time validation (TASK-132). Runtime queries never crash on orphans — `children()` simply does not return them.

---

## ProfileBackedFlowRepository (new adapter, FR-006)

Location: `core/src/commonMain/kotlin/com/launcher/adapters/flow/ProfileBackedFlowRepository.kt`.

Implements the **existing, unchanged** [`FlowRepository`](../../core/src/commonMain/kotlin/com/launcher/api/FlowRepository.kt) port — all four methods. The UI contract is already hierarchical (`FlowDescriptor(id, name, templateId, slots)`, `SlotDescriptor.action: Action?` where null = placeholder), so `Workspace → Flow → Tile` projects onto it directly — no port change, no `observeToolbar()`.

```kotlin
class ProfileBackedFlowRepository(
    private val profileStore: ProfileStore,
) : FlowRepository {

    // Regression path: HomeComponent.launchLoadFlows() (one-shot, 3s timeout).
    override suspend fun loadFlows(): List<FlowDescriptor> =
        profileStore.observe().filterNotNull().first().toFlowDescriptors()

    // Hot path: null Profile → no emission → HomeComponent stays Loading (SEQ-4).
    override fun observeFlows(): Flow<List<FlowDescriptor>> =
        profileStore.observe().filterNotNull().map { it.toFlowDescriptors() }

    override fun availableTemplates(presetId: String): List<FlowTemplate> = ALL_TEMPLATES // parity

    override suspend fun addFlow(templateId: String): FlowDescriptor =
        error("ProfileBackedFlowRepository.addFlow not supported yet") // TODO(profile-add-flow) — TASK-134

    /** Projection: Workspace → Flows → tiles. Degenerate profile (no Flow) → single synthetic flow. */
    private fun Profile.toFlowDescriptors(): List<FlowDescriptor> {
        val flowEntities = flows()
        if (flowEntities.isEmpty()) {
            val tiles = homeScreenTiles()
            if (tiles.isEmpty()) return emptyList()
            return listOf(FlowDescriptor(schemaVersion, id = "default", name = "", templateId = "contacts",
                slots = tiles.map { it.toSlot() }))
        }
        return flowEntities.map { flowEntity ->
            FlowDescriptor(
                schemaVersion = schemaVersion,
                id = flowEntity.id,
                name = (flowEntity.component as? Component.Flow)?.titleKey.orEmpty(),
                templateId = "contacts",
                slots = tilesOf(flowEntity.id).map { it.toSlot() },
            )
        }
    }
}
```

`Entity → SlotDescriptor` mapping: `AppTile` → `Action.OpenApp(packageName)`; `Sos` → SOS action; unmapped subtypes → `action = null` (placeholder card). Details in tasks.md.

**`filterNotNull` semantics** (SEQ-4): transient null (cold-start disk read) → `Loading`, never `Error`. Persistently absent Profile → `loadFlows()` suspends → HomeComponent's existing 3s timeout → `Error` + Retry (TASK-52 UX, unchanged).

**Toolbar rendering**: `toolbarButtons()` projects onto the existing tab mechanism — each button's `targetFlowId` maps to a `FlowDescriptor.id`, and switching uses `HomeComponent.selectFlow(flowId)` (TASK-52). No new port method.

---

## Deprecated: ConfigBackedFlowRepository

**Unchanged code** at `core/src/commonMain/kotlin/com/launcher/adapters/config/ConfigBackedFlowRepository.kt`, **unbound** from `FlowRepository` in DI (real binding sites: `core/src/android{Mock,Real}Backend/kotlin/com/launcher/di/BackendInit.kt`). Marked:

```kotlin
// TODO(config-deprecation, SRV-CONFIG-DEPRECATION): ConfigDocument stays for
// admin push (spec 009); remove entirely when unified Profile-sync ships.
```

Existing `ConfigBackedFlowRepository` tests stay green (class not removed).

---

## Relationships summary

```
Tag ──── (marks) ──── Component.tags: Set<Tag>
Component ──── (wrapped by) ──── Entity{id, parentId, status, wizardBehavior, critical} ──── (row of) ──── Profile
Blueprint ──── (spawns) ──── Entity            «Pool = catalogue of Blueprints»
Entity ──── (parentId) ──── Entity             «flat storage, computed tree»
Profile ──── (queried by) ──── ProfileQuery    «byTag / children / flows / tilesOf / toolbarButtons»
ReconcileEngine + Provider ──── (mutate) ──── Profile   «the ECS "systems"»
ProfileStore (port) ──── (observed by) ──── ProfileBackedFlowRepository ──── (implements) ──── FlowRepository (port, UNCHANGED)
FlowRepository ──── (consumed by) ──── HomeComponent ──── (renders) ──── HomeActivity
```

Example tree (owner's target screen), stored flat:

| id | component | parentId | tags |
|---|---|---|---|
| ws-main | `Workspace(layoutKey="single")` | — | Presentation, Workspace |
| flow-calls | `Flow(titleKey="calls", layoutKey="2x3", order=0)` | ws-main | Presentation, Flow |
| flow-apps | `Flow(titleKey="apps", order=1)` | ws-main | Presentation, Flow |
| tile-wa | `AppTile("com.whatsapp", "wa")` | flow-calls | Presentation, Tile |
| toolbar-main | `Toolbar(layoutKey="bottom")` | ws-main | Presentation, Toolbar |
| btn-calls | `ToolbarButton(targetFlowId="flow-calls", …)` | toolbar-main | Presentation, ToolbarButton |

---

## TL;DR для владельца

- **Профиль — плоская таблица.** Каждая строка (`Entity`) = один объект: workspace, flow, плитка, тулбар, кнопка. У строки есть колонка «родитель» (`parentId`). **Дерево не хранится — оно вычисляется запросом** («дай всех, у кого родитель = flow-calls»). Так устроен и обычный Android-лаунчер, и базы данных, и ECS-движки.
- **Три новых типа объектов**: `Workspace` (корень экрана), `Flow` (вкладка со своей сеткой 2×3 и порядком), `ToolbarButton` (кнопка со ссылкой на свой flow). Сетка `layoutKey` переехала на `Flow` — у каждой вкладки своя.
- **Переименование**: `ProfileComponent` → **`Entity`** (строка таблицы), `ComponentDeclaration` → **`Blueprint`** (заготовка в каталоге). Слово «component» значило три разные вещи. Это 93 механические правки в коде, формат хранения не меняется.
- **`Tag` — 13 ярлыков**: 8 смысловых (о чём объект) + 5 структурных (какая это часть экрана).
- **Пятый статус `Unverifiable`** — честное «не знаю»: Android не даёт спросить, скрыта ли системная шторка. Провайдер говорит «спроси человека» → пользователь жмёт «Я включил» → статус `Unverifiable`, а не враньё «применено». Проверка при старте такие настройки пропускает, чтобы не дёргать пожилого человека каждый раз.
- **Мёртвые кнопки не показываем**: плитка со статусом `Failed` (устройство не смогло) или `Skipped` (пользователь пропустил) на экран не попадает.
- **Валидация иерархии**: ссылка на несуществующего родителя, цикл, кнопка на несуществующий flow — три типизированные ошибки, ловятся при сборке профиля.
- **`schemaVersion` остаётся 2**: `parentId`, новые типы и новый статус — всё добавления, номер не трогаем, мигратор не пишем (приложение не выпущено).
- **Простой лаунчер = тот же код**: один workspace, один flow, ноль кнопок — вырожденное дерево, отдельной ветки кода нет.
