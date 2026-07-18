package com.launcher.preset.query

import com.launcher.preset.ecs.get
import com.launcher.preset.model.Component
import com.launcher.preset.model.Entity
import com.launcher.preset.model.LifecycleState
import com.launcher.preset.model.Profile
import com.launcher.preset.model.Tag

/**
 * Query API over a [Profile] (canonical ECS, TASK-136 — reshaped from TASK-127).
 *
 * **Mental model — ECS ≈ a database table** (see `docs/architecture/preset-model.md`):
 * `Profile` is the table, [Entity] is a row, its `components` hold the columns, its
 * [Entity.tags] are the labels to filter on, and `Entity.parentId` is a foreign
 * key. These functions are the `SELECT … WHERE`. Tags now read off the **entity**
 * (`it.tags`), not the component; typed component access is `entity.get<T>()`
 * (`preset/ecs/EntityDsl.kt`), no manual `as?`.
 *
 * The tree (`Workspace → Flow → Tile`, `Toolbar → ToolbarButton`) is **computed
 * here, never stored nested** — storage stays flat (research.md R-7; same pattern
 * as Bevy / Unity DOTS `Parent` and Android Launcher3's `favorites.container`).
 *
 * Extension functions rather than members: keeps `Profile.kt` from growing, and
 * feature-specific selectors can live in their own files later without touching
 * the core model. Eager `List` (not `Sequence`) — at MVP scale (~20-40 entities)
 * laziness would be premature optimisation (NFR-002: < 1 ms per query).
 */

// ---- base ----

/** Every entity matching [predicate]. Foundation of every selector below. */
fun Profile.query(predicate: (Entity) -> Boolean): List<Entity> = entities.filter(predicate)

// ---- tag selectors (FR-008) ----

/** Entities carrying [tag]. */
fun Profile.byTag(tag: Tag): List<Entity> = query { tag in it.tags }

/** Entities carrying **all** of [tags] (AND). */
fun Profile.byAllTags(tags: Set<Tag>): List<Entity> = query { it.tags.containsAll(tags) }

/** Entities carrying **at least one** of [tags] (OR). */
fun Profile.byAnyTag(tags: Set<Tag>): List<Entity> = query { it.tags.any(tags::contains) }

/** Entities **not** carrying [tag] (plain exclusion predicate). */
fun Profile.byNotTag(tag: Tag): List<Entity> = query { tag !in it.tags }

// ---- hierarchy selectors (FR-009) ----

/**
 * Direct children of [parentId]. An orphan (whose parent id does not exist) is
 * simply never returned — queries do not crash on a broken tree; assembly-time
 * validation reports it instead (FR-016).
 */
fun Profile.children(parentId: String): List<Entity> = query { it.parentId == parentId }

/** Entities with no parent. */
fun Profile.roots(): List<Entity> = query { it.parentId == null }

/** The screen root, if the profile declares one. */
fun Profile.workspace(): Entity? = byTag(Tag.Workspace).firstOrNull()

/** Flows (tabs) ordered left-to-right by `Component.Flow.order`. */
fun Profile.flows(): List<Entity> =
    byTag(Tag.Flow).sortedBy { it.get<Component.Flow>()?.order ?: 0 }

/** The bottom toolbar container, if any. Query-based — no `is Toolbar` type check. */
fun Profile.toolbar(): Entity? = byTag(Tag.Toolbar).firstOrNull()

/** Toolbar buttons ordered by `Component.ToolbarButton.order`. */
fun Profile.toolbarButtons(): List<Entity> =
    byTag(Tag.ToolbarButton).sortedBy { it.get<Component.ToolbarButton>()?.order ?: 0 }

/**
 * Tiles belonging to [flowId], with **render gating** applied.
 *
 * Tags answer *what* a component is; [LifecycleState] answers whether this device
 * managed to apply it. A [LifecycleState.Failed] tile (the device could not apply
 * it) or a [LifecycleState.Skipped] one (the user declined in the wizard) must
 * never reach the screen — an elderly user should not face a button that does
 * nothing. [LifecycleState.Pending] renders (benign transient) and so does
 * [LifecycleState.Unverifiable] (the user said it is on); an entity with no
 * lifecycle component renders too (structural skeleton).
 */
fun Profile.tilesOf(flowId: String): List<Entity> =
    query { it.parentId == flowId && it.tags.containsAll(TILE_TAGS) }
        .filterNot { it.isHiddenFromScreen() }

/**
 * Tiles for the home screen.
 *
 * [flowId] `null` → the first flow's tiles. A degenerate profile (tiles but no
 * [Component.Flow] entities — the simple-launcher case, US-1) returns every tile
 * regardless of parent, so the one-level screen is the same code path, not a
 * special case.
 */
fun Profile.homeScreenTiles(flowId: String? = null): List<Entity> {
    val target = flowId ?: flows().firstOrNull()?.id
    if (target != null) return tilesOf(target)
    return byAllTags(TILE_TAGS).filterNot { it.isHiddenFromScreen() }
}

private val TILE_TAGS = setOf(Tag.Presentation, Tag.Tile)

/**
 * Render gating via the entity's [LifecycleState] component (was `Entity.status`).
 * `Failed`/`Skipped` hidden; `Pending`/`Applied`/`Unverifiable`/absent → renders.
 */
private fun Entity.isHiddenFromScreen(): Boolean = when (get<LifecycleState>()) {
    is LifecycleState.Failed, LifecycleState.Skipped -> true
    else -> false
}
