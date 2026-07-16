# Data Model: ECS Tags Foundation

**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)

Describes new / modified types introduced by TASK-127. Complements `docs/architecture/preset-model.md` (two-dimensions doc) with concrete field shapes for implementation.

---

## Tag (new enum)

Location: `core/src/commonMain/kotlin/com/launcher/preset/model/Enums.kt` (existing file, add `Tag` enum).

```kotlin
enum class Tag {
    Presentation,    // visible to owner on some UI surface
    Appearance,      // visual override (fontScale, theme, ...)
    System,          // OS-level settings (rotation lock, kiosk, launcher role)
    Safety,          // safety-related components (SOS, emergency call routing)
    Capabilities,    // permissions, features, integrations
    Communication,   // call, SMS, messenger, contact list
    Accessibility,   // WCAG / TalkBack / senior-safe overrides
    Emergency,       // triggered in emergency (911, SOS, panic)
    Tile,            // renders in HomeScreen tile grid (excludes Toolbar)
    Toolbar,         // marker for bottom Toolbar panel (query-based lookup, no `is` check)
}
```

**Additive-only** per rule 5 (wire-format versioning). Adding a new tag is a schemaVersion-safe change (existing v3 Profiles do not carry it in their `tags` field, so no data loss). Removing or renaming a tag = breaking change = requires migration.

**Closed set**: `enum class`, not free-form `String`. Freeform strings would drift into typo hell (`"presention"`, `"presentaion"`). Owner-visible tag names are not exposed; owner never types tags.

---

## Component.tags (new field)

Location: `core/src/commonMain/kotlin/com/launcher/preset/model/Component.kt` — existing sealed hierarchy.

```kotlin
sealed class Component {
    abstract val id: ComponentId
    abstract val tags: Set<Tag>       // NEW field, default per subtype

    data class AppTile(
        override val id: ComponentId,
        val packageName: String,
        val label: String,
        override val tags: Set<Tag> = setOf(Tag.Presentation, Tag.Tile),
    ) : Component()

    data class Sos(
        override val id: ComponentId,
        val targetPhone: String,
        override val tags: Set<Tag> = setOf(Tag.Presentation, Tag.Tile, Tag.Safety, Tag.Emergency),
    ) : Component()

    data class Toolbar(
        override val id: ComponentId,
        val buttons: List<ToolbarButton>,
        override val tags: Set<Tag> = setOf(Tag.Presentation, Tag.Toolbar),  // no Tile — separate panel; Tag.Toolbar enables query-based lookup
    ) : Component()

    data class FontSize(
        override val id: ComponentId,
        val scale: Float,
        override val tags: Set<Tag> = setOf(Tag.Appearance, Tag.Accessibility),
    ) : Component()

    data class LauncherRole(
        override val id: ComponentId,
        val requested: Boolean,
        override val tags: Set<Tag> = setOf(Tag.System),
    ) : Component()

    data class Theme(
        override val id: ComponentId,
        val name: String,
        override val tags: Set<Tag> = setOf(Tag.Appearance),
    ) : Component()

    // ... any additional subtypes: same pattern, non-empty default tags.
}
```

**Design constraint**: every subtype MUST declare non-empty default `tags`. Enforced by `ComponentTagsFitnessTest` (reflection walk over sealed subclasses).

**Override**: `ComponentDeclaration` in `pool.json` supports optional `"tags": [...]` field. If provided, overrides the subtype default at pool-load time; if absent, subtype default applies. Enables preset authors to add extra semantic tags to a specific `AppTile` instance without touching Kotlin code (e.g., `AppTile(WhatsApp) → +Tag.Communication`).

---

## Query API (new)

Location: `core/src/commonMain/kotlin/com/launcher/preset/query/ProfileQuery.kt` (new file).

```kotlin
// Base predicate-based query
fun Profile.query(predicate: (ProfileComponent) -> Boolean): List<ProfileComponent> =
    components.filter(predicate)

// Convenience: single tag match
fun Profile.byTag(tag: Tag): List<ProfileComponent> =
    query { tag in it.component.tags }

// Convenience: AND — all listed tags must be present
fun Profile.byAllTags(tags: Set<Tag>): List<ProfileComponent> =
    query { it.component.tags.containsAll(tags) }

// Convenience: OR — at least one listed tag present
fun Profile.byAnyTag(tags: Set<Tag>): List<ProfileComponent> =
    query { it.component.tags.any(tags::contains) }

// Convenience: NOT — components lacking this tag (canonical ECS Without<T> / Flecs !tag / EnTT exclude<>)
fun Profile.byNotTag(tag: Tag): List<ProfileComponent> =
    query { tag !in it.component.tags }

// HomeScreen: tiles (Presentation AND Tile — excludes Toolbar)
fun Profile.homeScreenTiles(): List<ProfileComponent> =
    byAllTags(setOf(Tag.Presentation, Tag.Tile))

// HomeScreen: bottom Toolbar (query-based, no `is Toolbar` type check)
fun Profile.toolbar(): ProfileComponent? =
    byTag(Tag.Toolbar).firstOrNull()
```

**Extension functions** — not member methods on `Profile`. Rationale: keeps `Profile.kt` from growing; each selector is small and testable in isolation; new selectors can be added in feature-specific files (e.g., `SafetyQueries.kt` when Phase-2 safety features land) without touching core `Profile.kt`.

**Return type**: `List<ProfileComponent>` (not `Sequence`) — eager evaluation. MVP Profile size (~20 Components) makes eager+linear the right choice; laziness would be premature optimization.

**Null semantics**: never returns `null` for list queries; returns empty list. `toolbar()` returns `ProfileComponent?` — Toolbar is optional UI element.

**Performance target**: < 1 ms per call at MVP scale (NFR-003, SC-008). Verified by `ProfileQueryBenchmarkTest`.

---

## ProfileMigrationV2toV3 (new)

Location: `core/src/commonMain/kotlin/com/launcher/preset/serialization/ProfileMigrationV2toV3.kt` (new).

```kotlin
internal object ProfileMigrationV2toV3 {
    fun migrate(v2Profile: ProfileV2): ProfileV3 = ProfileV3(
        schemaVersion = 3,
        components = v2Profile.components.map { component ->
            component.withTags(defaultTagsFor(component))
        },
    )

    private fun defaultTagsFor(component: Component): Set<Tag> = when (component) {
        is Component.AppTile      -> setOf(Tag.Presentation, Tag.Tile)
        is Component.Sos          -> setOf(Tag.Presentation, Tag.Tile, Tag.Safety, Tag.Emergency)
        is Component.Toolbar      -> setOf(Tag.Presentation, Tag.Toolbar)
        is Component.FontSize     -> setOf(Tag.Appearance, Tag.Accessibility)
        is Component.LauncherRole -> setOf(Tag.System)
        is Component.Theme        -> setOf(Tag.Appearance)
        // exhaustive when: adding a new subtype forces adding a case here
        // (Kotlin sealed exhaustiveness check catches missed subtypes at compile time).
    }
}
```

**Idempotency (NFR-004)**: applying `migrate()` to a `ProfileV3` (via ProfileSerializer path) is a no-op because the serializer skips migration when `schemaVersion == 3`. Roundtrip test verifies.

**Co-location constraint (R-1 mitigation)**: default `tags` values on `Component` subtypes (see `Component.tags` section above) and mapping in `defaultTagsFor` MUST match. Enforced by `ComponentTagsFitnessTest` (which reflects on `Component` subtypes and reads their default `tags`, then verifies migration writer produces the same values).

**Exhaustiveness**: `when` block over sealed hierarchy — Kotlin compiler enforces coverage. Adding a new `Component` subtype forces compilation failure until migration writer is updated.

---

## ProfileBackedFlowRepository (new adapter)

Location: `core/src/commonMain/kotlin/com/launcher/adapters/flow/ProfileBackedFlowRepository.kt` (new).

```kotlin
class ProfileBackedFlowRepository(
    private val profileStore: ProfileStore,
) : FlowRepository {

    override fun observeFlows(): Flow<List<FlowDescriptor>> =
        profileStore.observe()
            .filterNotNull()                            // SEQ-4: null Profile → stay in Loading
            .map { profile -> profile.homeScreenTiles() }
            .map { components -> components.map(::toFlowDescriptor) }

    override fun observeToolbar(): Flow<ToolbarDescriptor?> =
        profileStore.observe()
            .filterNotNull()
            .map { profile -> profile.toolbar()?.let(::toToolbarDescriptor) }

    private fun toFlowDescriptor(pc: ProfileComponent): FlowDescriptor = TODO("map ProfileComponent → FlowDescriptor")
    private fun toToolbarDescriptor(pc: ProfileComponent): ToolbarDescriptor = TODO("map ProfileComponent → ToolbarDescriptor")
}
```

**Contract with `FlowRepository` port** unchanged — signatures match existing `ConfigBackedFlowRepository`. Callers (HomeComponent) do not change.

**`filterNotNull` semantics** (SEQ-4): when `ProfileStore.observe()` emits `null` (cold start, transient migration), no downstream emission occurs. `HomeComponent` stays in `HomeLoadingState.Loading` (default state). No `Error` emitted — closes TASK-52 regression.

**Mapping to `FlowDescriptor`**: TODO in the sketch above — mapping logic details land in tasks.md (Phase 2), not plan.md.

---

## Deprecated: ConfigBackedFlowRepository

**Unchanged code**, but **unbound from `FlowRepository`** in DI. Marked with inline TODO:

```kotlin
// TODO(config-deprecation, SRV-CONFIG-DEPRECATION): ConfigDocument stays for
// admin push (spec 009); remove entirely when unified Profile-sync ships.
class ConfigBackedFlowRepository(...) : FlowRepository { ... }
```

Existing tests (`ConfigBackedFlowRepositoryTest`) stay green (class not removed, coverage preserved).

---

## Relationships summary

```
Tag ──── (referenced by) ──── Component.tags: Set<Tag>
Component ──── (contained in) ──── ProfileComponent ──── (contained in) ──── Profile
Profile ──── (queried by) ──── query / byTag / byAllTags / byAnyTag / homeScreenTiles / toolbar
ProfileStore ──── (observed by) ──── ProfileBackedFlowRepository ──── (implements) ──── FlowRepository
FlowRepository ──── (consumed by) ──── HomeComponent ──── (renders in) ──── HomeActivity
ProfileMigrationV2toV3 ──── (invoked by) ──── ProfileSerializer ──── (used by) ──── ProfileStore.readFromDisk()
```

---

## TL;DR для владельца

- **`Tag`** — это ярлык на компоненте: «плитка», «безопасность», «внешний вид», всего **10 штук** (`Presentation, Appearance, System, Safety, Capabilities, Communication, Accessibility, Emergency, Tile, Toolbar`).
- **`Component.tags`** — множество ярлыков на одном компоненте. `SOS` = «показывается» + «плитка» + «безопасность» + «экстренная». `Toolbar` = «показывается» + «тулбар» (`Tag.Toolbar` — маркер отдельной панели).
- **`Profile.query`** — «отдай все компоненты подходящие под условие». Удобные обёртки: `byTag`, `byAllTags`, `byAnyTag`, `byNotTag` (эквивалент canonical ECS `Without<T>`), `homeScreenTiles`, `toolbar`.
- **`toolbar()`** реализован через `byTag(Tag.Toolbar).firstOrNull()` — чистый query, без `is Toolbar` type check. Одна парадигма — теги.
- **Terminology (ADR-012)**: это **tagged-component-model, ECS-inspired**, не canonical ECS (Bevy / Flecs / Unity DOTS). Sealed hierarchy = один Component на entity, а не multi-component composition. Для MVP-масштаба (~20 компонентов) это ок.
- **Миграция v2 → v3** — маленький файл `ProfileMigrationV2toV3.kt`, знает какие теги проставить каждому субтипу компонента. Компилятор Kotlin (sealed exhaustive) не даст забыть новый субтип.
- **`ProfileBackedFlowRepository`** — новый переходник, читает `Profile` и отдаёт плитки на `HomeScreen`. Заменяет старый `ConfigBackedFlowRepository` в проводке (тот остаётся в коде для будущего сценария админ-пуш).
- **Гарантия «не забыть теги»**: fitness-тест `ComponentTagsFitnessTest` пробегает по всем субтипам через reflection и проверяет что у каждого не пустой набор дефолтных тегов. Если разработчик добавит новый субтип и забудет теги — тест упадёт на билде.
