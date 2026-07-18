# Contract: Query API surface (`ProfileQuery`) — TASK-136

`ProfileQuery` extension functions over a `Profile`, reshaped from reading `it.component.tags` (TASK-127) to reading **`entity.tags` + `entity.components`**. Same selector semantics as TASK-127 (Capability Story 2 + 5), new access path. Built on the ecs `family {}` + `get<T>()` primitives ([ecs-world-core.md](ecs-world-core.md)). Pure Kotlin, zero Android imports. Linear scan < 1 ms at MVP scale (NFR-002).

---

## Selector surface

### Base
```kotlin
fun Profile.query(predicate: (Entity) -> Boolean): List<Entity> = entities.filter(predicate)
inline fun <reified T : Component> Entity.get(): T?            // typed access (FR-007), no manual `as?`
```

### Tag selectors (over `entity.tags` — was `entity.component.tags`)
```kotlin
fun Profile.byTag(tag: Tag): List<Entity>            = query { tag in it.tags }
fun Profile.byAllTags(tags: Set<Tag>): List<Entity>  = query { it.tags.containsAll(tags) }
fun Profile.byAnyTag(tags: Set<Tag>): List<Entity>   = query { it.tags.any(tags::contains) }
fun Profile.byNotTag(tag: Tag): List<Entity>         = query { tag !in it.tags }
```

### Hierarchy selectors (unchanged logic; component reads via `get<T>()`)
```kotlin
fun Profile.children(parentId: String): List<Entity>
fun Profile.roots(): List<Entity>
fun Profile.workspace(): Entity?                         // byTag(Workspace).firstOrNull()
fun Profile.flows(): List<Entity>                        // byTag(Flow) sortedBy get<Flow>()?.order
fun Profile.toolbar(): Entity?
fun Profile.toolbarButtons(): List<Entity>               // sortedBy get<ToolbarButton>()?.order
fun Profile.tilesOf(flowId: String): List<Entity>        // render-gated (below)
fun Profile.homeScreenTiles(flowId: String? = null): List<Entity>
```

---

## Render gating (via `LifecycleState`, was `Entity.status`)

Tags say *what* a component is; `LifecycleState` says whether this device applied it. `tilesOf` / `homeScreenTiles` hide `Failed` and `Skipped` (an elderly user must never face a dead button); `Pending` and `Unverifiable` render.

```kotlin
private fun Entity.isHiddenFromScreen(): Boolean = when (get<LifecycleState>()) {
    is LifecycleState.Failed, LifecycleState.Skipped -> true
    else -> false     // Pending / Applied / Unverifiable / absent → renders
}
```
Absence of a `LifecycleState` component (structural-only entity) ⇒ renders (Pending-equivalent) — the degenerate profile path is unchanged from TASK-127.

---

## Semantics pinned by tests

| Selector | Test assertion |
|----------|----------------|
| `byTag`/`byAllTags`/`byAnyTag`/`byNotTag` | AND/OR/NOT/empty/not-present sets over `entity.tags` (Capability Story 2) |
| multi-tag membership | one entity with several tags found by any valid combination (Capability Story 1a) |
| `get<T>()` | returns typed component or `null`, no manual cast; unambiguous (at-most-one-per-type) |
| `flows`/`toolbarButtons` | ordered by `get<Flow/ToolbarButton>()?.order` |
| `tilesOf` | tiles of another flow excluded; `Failed`/`Skipped` hidden; `Pending`/`Unverifiable` shown |
| `homeScreenTiles` (degenerate) | no `Flow` entities → all tiles returned (one-level screen, US-1 parity) |
| orphan (`children`) | dangling `parentId` silently absent, no crash |

Queries are **never persisted** — only tags/components are, so there is no query-language version to migrate.

---

<!-- NOVICE-SUMMARY:BEGIN -->
## Кратко по-русски

Запросы к профилю (найти плитки, вкладки, кнопки) остаются те же, что и в TASK-127, но теперь читают теги **с сущности** (`entity.tags`), а не с компонента, и достают компонент типобезопасно через `entity.get<Тип>()` вместо ручного приведения `as?`. Правило «не показывать мёртвую кнопку» сохранено: плитки со статусом `Failed`/`Skipped` не попадают на экран — только теперь статус читается из компонента-состояния `LifecycleState`, а не из поля `status`. Вырожденный профиль без вкладок по-прежнему отдаёт все плитки одним экраном (тот же код, не особый случай). Запросы никогда не сохраняются в файл — сохраняются только теги и компоненты, поэтому версионировать «язык запросов» не нужно.
<!-- NOVICE-SUMMARY:END -->
