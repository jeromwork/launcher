# Data Model: Tagged-Component Foundation (TASK-127)

**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)

Describes new / modified types introduced by TASK-127. Complements `docs/architecture/preset-model.md` (two-dimensions doc) with concrete field shapes for implementation.

**Rewritten 2026-07-16 (deep pre-implement audit)**: earlier revision sketched a fictional `Component` hierarchy (`AppTile(label)`, `Sos(targetPhone)`, `Toolbar(buttons)`, abstract `id` on Component) that never existed in code. This revision is generated from the real [Component.kt](../../core/src/commonMain/kotlin/com/launcher/preset/model/Component.kt) / [Profile.kt](../../core/src/commonMain/kotlin/com/launcher/preset/model/Profile.kt) on the branch.

---

## Tag (new enum)

Location: `core/src/commonMain/kotlin/com/launcher/preset/model/Enums.kt` (existing file, add `Tag` enum, `@Serializable` like its neighbours).

```kotlin
@Serializable
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

**Additive-only** per rule 5 — with an honest caveat (see contract § Forward compat): adding a value is safe for writers, but an *older reader* fails loud on an unknown enum name inside a `tags` array. Acceptable while the Profile is same-device/same-binary; lenient serializer required before cross-device artifacts ship. Removing or renaming a tag = breaking change = requires migration.

**Closed set**: `enum class`, not free-form `String` — prevents typo drift. Known tension (ADR-012): a closed enum blocks third-party preset authors from minting semantic tags once rule-9 preset sharing lands; exit ramp = namespaced string tags (wire format already stores strings, so that is a lenient-serializer change, not a format break).

---

## Component.tags (new field on the REAL hierarchy)

Location: `core/src/commonMain/kotlin/com/launcher/preset/model/Component.kt` — existing sealed hierarchy (8 subtypes, NOT 6). `Component` has **no `id`** — identity lives on the `ProfileComponent` wrapper.

```kotlin
@Serializable
sealed class Component {
    abstract val tags: Set<Tag>          // NEW abstract field, default per subtype

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

    @Serializable @SerialName("Toolbar")
    data class Toolbar(
        val items: List<String>,
        val layoutKey: String,
        override val tags: Set<Tag> = setOf(Tag.Presentation, Tag.Toolbar),
    ) : Component()

    // WAS `object` — converted to data class so `tags` can be a constructor
    // param (objects cannot carry overridable constructor-defaults; object
    // properties are not part of kotlinx wire shape). Wire-compatible:
    // old JSON `{"type":"LauncherRole"}` still reads (all params defaulted).
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

    // WAS `object` — same conversion as LauncherRole.
    @Serializable @SerialName("StatusBarPolicy")
    data class StatusBarPolicy(
        override val tags: Set<Tag> = setOf(Tag.System),
    ) : Component()
}
```

Deltas vs the earlier fictional sketch (all corrected against real code):
- **8 subtypes**, including `Language` and `StatusBarPolicy` (earlier revision missed both).
- Real field names: `labelKey` (not `label`), `Sos(shareLocation, autoAnswer)` (not `targetPhone` — that would be PII in a shareable artifact, rule 9), `Toolbar(items, layoutKey)` (not `buttons`), `Theme` has 4 flat fields (not `name`).
- No `ComponentId` / abstract `id` — `ProfileComponent.id` is the identity.
- `LauncherRole` / `StatusBarPolicy` **object → data class conversion** is an explicit, wire-compatible task (T127-004).

**Design constraint**: every subtype MUST declare a non-empty default `tags`. Enforced by `ComponentTagsFitnessTest` — reflection walk; for subtypes with required params (e.g. `AppTile.packageName`) the test supplies dummy values and asserts the `tags` default is non-empty.

**Pool override — no ComponentDeclaration change needed**: [Pool.kt](../../core/src/commonMain/kotlin/com/launcher/preset/model/Pool.kt) `ComponentDeclaration` embeds `component: Component` directly, so a `pool.json` declaration overrides tags simply by including `"tags": [...]` inside its embedded component object. The earlier plan to add a separate `tags: List<String>?` field to `ComponentDeclaration` is dropped (rule 4 MVA — mechanism already exists). FR-003 restated accordingly.

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

// Convenience: NOT — components lacking this tag
fun Profile.byNotTag(tag: Tag): List<ProfileComponent> =
    query { tag !in it.component.tags }

// HomeScreen: tiles (Presentation AND Tile — excludes Toolbar).
// Excludes ComponentStatus.Failed / Skipped — see § Render gating below.
fun Profile.homeScreenTiles(): List<ProfileComponent> =
    byAllTags(setOf(Tag.Presentation, Tag.Tile))
        .filterNot { it.status == ComponentStatus.Failed || it.status == ComponentStatus.Skipped }

// Bottom Toolbar lookup (query-based, no `is Toolbar` type check).
// NOTE: selector exists at query-API level; HomeComponent does NOT consume it
// in TASK-127 (toolbar rendering is out of scope — see spec § Out of Scope).
fun Profile.toolbar(): ProfileComponent? =
    byTag(Tag.Toolbar).firstOrNull()
```

**Mental model** (per ADR-012): this is a **label-selector system (Kubernetes-style `matchLabels`), not canonical ECS**. `byNotTag` is a plain predicate, not Bevy `Without<T>` archetype filtering — the earlier "canonical ECS equivalent" framing overstated parity and is dropped from docs.

**Extension functions** — not member methods on `Profile`: keeps `Profile.kt` from growing; new selectors land in feature-specific files without touching core model.

**Return type**: `List<ProfileComponent>` (eager). MVP Profile ~20 Components — laziness would be premature optimization.

**Null semantics**: list queries never return `null` (empty list); `toolbar()` returns `ProfileComponent?`.

**Performance target**: < 1 ms per call at MVP scale (NFR-003, SC-008), verified by `ProfileQueryBenchmarkTest`.

### Render gating (capability / status policy — NEW, closes audit hole M1)

Tags answer "*what is this component about*"; `ProfileComponent.status` answers "*did this device manage to apply it*" (`Pending / Applied / Failed / Skipped`, set by `ReconcileEngine` providers; `Outcome.Unsupported` on a NoOp fallback → `Failed`/`Skipped`). MVP policy: **`homeScreenTiles()` excludes `Failed` and `Skipped`** — a tile whose component this device could not apply (or the user declined in the wizard) does not render as a dead button (senior-UX: no tiles that do nothing). `Pending` renders (benign transient). One unit test pins this. If segments later need "render with tap-time fallback" instead — that is a preset field candidate per rule 11, revisit then.

---

## Profile schemaVersion (stays 2 — additive change)

Real state: `Profile.CURRENT_SCHEMA_VERSION = 2` ([Profile.kt:39](../../core/src/commonMain/kotlin/com/launcher/preset/model/Profile.kt#L39)), DataStore key `profile_json_v2`. The `tags` addition is **additive** (optional field, constructor-defaults) → **no bump, stays 2**. The earlier "reset to 1" wording (Q6 remediation commit b297979) touched only spec artifacts, contradicted shipped code and the immutable TASK-120 Decision, and is corrected in this revision.

**No migration writer in TASK-127** (Q6 essence unchanged):
- MVP unreleased — no consumer for a migration (rule 4 MVA).
- Missing `tags` in JSON → kotlinx.serialization constructor-default (single source of truth).
- First post-release breaking change = `ProfileMigrationV2toV3` + bump to 3.

**Fitness test**: `ComponentTagsFitnessTest` — every subtype has non-empty default tags. Single gate; no duplicate `defaultTagsFor()` mapping anywhere.

---

## ProfileBackedFlowRepository (new adapter)

Location: `core/src/commonMain/kotlin/com/launcher/adapters/flow/ProfileBackedFlowRepository.kt` (new; note the existing config adapter lives at `core/src/commonMain/kotlin/com/launcher/adapters/config/ConfigBackedFlowRepository.kt`).

Implements the **existing, unchanged** [`FlowRepository`](../../core/src/commonMain/kotlin/com/launcher/api/FlowRepository.kt) port — all four methods (the earlier sketch invented `observeToolbar()` and omitted three real methods; corrected):

```kotlin
class ProfileBackedFlowRepository(
    private val profileStore: ProfileStore,
) : FlowRepository {

    // One-shot path — THIS is what HomeComponent.launchLoadFlows() calls with
    // its 3s timeout; the actual TASK-52 regression fires here, not in observe.
    // Awaits first non-null Profile: post-wizard it is already saved → returns
    // immediately. Genuinely absent Profile → suspends → caller's 3s timeout
    // → Error + Retry (existing TASK-52 UX, unchanged — no eternal Loading).
    override suspend fun loadFlows(): List<FlowDescriptor> =
        profileStore.observe().filterNotNull().first()
            .homeScreenTiles().map(::toFlowDescriptor)

    // Hot path — SEQ-4: null Profile → no emission → HomeComponent stays Loading.
    override fun observeFlows(): Flow<List<FlowDescriptor>> =
        profileStore.observe()
            .filterNotNull()
            .map { profile -> profile.homeScreenTiles().map(::toFlowDescriptor) }

    // Template catalogue — reuse the existing static catalogue semantics
    // (same behaviour as ConfigBackedFlowRepository.availableTemplates).
    override fun availableTemplates(presetId: String): List<FlowTemplate> = …

    // Parity with the only existing impl (ConfigBackedFlowRepository.addFlow
    // also throws). Profile-based addFlow = separate future task.
    override suspend fun addFlow(templateId: String): FlowDescriptor =
        error("ProfileBackedFlowRepository.addFlow not supported yet — TODO(profile-add-flow)")

    private fun toFlowDescriptor(pc: ProfileComponent): FlowDescriptor = TODO("T127-014")
}
```

**`filterNotNull` semantics** (SEQ-4): transient null (cold-start disk read) → `Loading`, never `Error`. Persistently absent Profile → `loadFlows()` suspends → HomeComponent's existing 3s timeout → `Error("timeout 3s")` with Retry — deliberate: no infinite-Loading watchdog gap.

**Mapping to `FlowDescriptor`**: details in tasks.md (T127-014).

---

## Deprecated: ConfigBackedFlowRepository

**Unchanged code** at `core/src/commonMain/kotlin/com/launcher/adapters/config/ConfigBackedFlowRepository.kt`, but **unbound from `FlowRepository`** in DI (real binding sites: `core/src/androidMockBackend/kotlin/com/launcher/di/BackendInit.kt` and `core/src/androidRealBackend/kotlin/com/launcher/di/BackendInit.kt` — NOT `app/.../di/{Mock,Real}BackendModule.kt`, which do not exist). Marked with inline TODO:

```kotlin
// TODO(config-deprecation, SRV-CONFIG-DEPRECATION): ConfigDocument stays for
// admin push (spec 009); remove entirely when unified Profile-sync ships.
```

Existing `ConfigBackedFlowRepository` tests stay green (class not removed).

---

## Relationships summary

```
Tag ──── (referenced by) ──── Component.tags: Set<Tag>
Component ──── (wrapped by) ──── ProfileComponent{id, wizardBehavior, critical, status} ──── (contained in) ──── Profile
Profile ──── (queried by) ──── query / byTag / byAllTags / byAnyTag / byNotTag / homeScreenTiles / toolbar
ProfileStore (port) ──── (observed by) ──── ProfileBackedFlowRepository ──── (implements) ──── FlowRepository (port, UNCHANGED)
FlowRepository ──── (consumed by) ──── HomeComponent (loadFlows + observeFlows) ──── (renders in) ──── HomeActivity
DataStoreProfileStore (app adapter) / FakeProfileStore (commonTest) ──── (implement) ──── ProfileStore
```

---

## TL;DR для владельца

- **`Tag`** — ярлык на компоненте («плитка», «безопасность», «внешний вид»), всего **10 штук**. Закрытый список в коде.
- **`Component.tags`** — множество ярлыков на одном компоненте. У SOS их четыре сразу: «показывается» + «плитка» + «безопасность» + «экстренная».
- **Подтипов компонентов — 8** (в старой версии этого документа было выдумано 6 с несуществующими полями): плитка приложения, размер шрифта, SOS, тулбар, роль лаунчера, тема, язык, скрытие статус-бара. Два последних из «синглтонов» станут обычными классами — иначе на них нельзя навесить теги (совместимо со старыми данными).
- **`Profile.query`** — «отдай компоненты по условию» + удобные обёртки. Правильная аналогия — **метки в Kubernetes**, не игровой ECS (мы это честно зафиксировали в ADR-012).
- **Новое правило показа**: плитка, которую устройство не смогло применить (`Failed`) или которую пользователь пропустил в мастере (`Skipped`), **не рендерится** на главном экране — пожилой пользователь не увидит мёртвую кнопку. Это дефолт, его можно пересмотреть.
- **`schemaVersion` остаётся 2** — как в коде. Добавление `tags` — аддитивно, номер не трогаем, миграцию не пишем (решение Q6 по сути сохранено).
- **`ProfileBackedFlowRepository`** — новый переходник; реализует **все четыре** метода существующего порта (в старой версии документа был выдуман метод `observeToolbar`, а три настоящих пропущены). Чинит именно тот путь (`loadFlows`), где рождалась ошибка «Не удалось загрузить настройки».
