package com.launcher.preset.query

import com.launcher.preset.model.Component
import com.launcher.preset.model.ComponentStatus
import com.launcher.preset.model.Entity
import com.launcher.preset.model.Profile
import com.launcher.preset.model.Tag

/**
 * Query API over a [Profile] (T127-013, FR-005 + FR-012).
 *
 * **Mental model — ECS ≈ a database table** (see `docs/architecture/preset-model.md`):
 * `Profile` is the table, [Entity] is a row, `Component` holds the columns, [Tag]
 * is a label to filter on, and `Entity.parentId` is a foreign key. These functions
 * are the `SELECT … WHERE`.
 *
 * The tree (`Workspace → Flow → Tile`, `Toolbar → ToolbarButton`) is **computed
 * here, never stored nested** — storage stays flat (research.md R-7; same pattern
 * as Bevy / Unity DOTS `Parent` and Android Launcher3's `favorites.container`).
 *
 * Per ADR-012 this is a **label-selector** system (closest industrial analogue:
 * Kubernetes `matchLabels`), not canonical ECS archetype filtering: [byNotTag] is
 * a plain predicate, not Bevy's `Without<T>`.
 *
 * Extension functions rather than members: keeps `Profile.kt` from growing, and
 * feature-specific selectors can live in their own files later without touching
 * the core model. Eager `List` (not `Sequence`) — at MVP scale (~20-40 entities)
 * laziness would be premature optimisation (NFR-003: < 1 ms per query).
 */

// ---- base ----

/** Every entity matching [predicate]. Foundation of every selector below. */
fun Profile.query(predicate: (Entity) -> Boolean): List<Entity> = components.filter(predicate)

// ---- tag selectors (FR-005) ----

/** Entities carrying [tag]. */
fun Profile.byTag(tag: Tag): List<Entity> = query { tag in it.component.tags }

/** Entities carrying **all** of [tags] (AND). */
fun Profile.byAllTags(tags: Set<Tag>): List<Entity> = query { it.component.tags.containsAll(tags) }

/** Entities carrying **at least one** of [tags] (OR). */
fun Profile.byAnyTag(tags: Set<Tag>): List<Entity> = query { it.component.tags.any(tags::contains) }

/** Entities **not** carrying [tag] (plain exclusion predicate). */
fun Profile.byNotTag(tag: Tag): List<Entity> = query { tag !in it.component.tags }

// ---- hierarchy selectors (FR-012) ----

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
    byTag(Tag.Flow).sortedBy { (it.component as? Component.Flow)?.order ?: 0 }

/** The bottom toolbar container, if any. Query-based — no `is Toolbar` type check. */
fun Profile.toolbar(): Entity? = byTag(Tag.Toolbar).firstOrNull()

/** Toolbar buttons ordered by `Component.ToolbarButton.order`. */
fun Profile.toolbarButtons(): List<Entity> =
    byTag(Tag.ToolbarButton).sortedBy { (it.component as? Component.ToolbarButton)?.order ?: 0 }

/**
 * Tiles belonging to [flowId], with **render gating** applied.
 *
 * Tags answer *what* a component is; `status` answers whether this device managed
 * to apply it. A [ComponentStatus.Failed] tile (the device could not apply it) or a
 * [ComponentStatus.Skipped] one (the user declined in the wizard) must never reach
 * the screen — an elderly user should not face a button that does nothing.
 * [ComponentStatus.Pending] renders (benign transient) and so does
 * [ComponentStatus.Unverifiable] (the user said it is on).
 */
fun Profile.tilesOf(flowId: String): List<Entity> =
    query { it.parentId == flowId && it.component.tags.containsAll(TILE_TAGS) }
        .filterNot { it.status.isHiddenFromScreen() }

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
    return byAllTags(TILE_TAGS).filterNot { it.status.isHiddenFromScreen() }
}

private val TILE_TAGS = setOf(Tag.Presentation, Tag.Tile)

private fun ComponentStatus.isHiddenFromScreen(): Boolean =
    this == ComponentStatus.Failed || this == ComponentStatus.Skipped
