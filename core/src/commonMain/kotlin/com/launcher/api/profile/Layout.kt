package com.launcher.api.profile

import kotlinx.serialization.Serializable

/**
 * Visual layout of a preset: ordered screens, each with a grid of slots.
 *
 * `Slot.kind` is a forward-compat hook (Clarification #11) — null today,
 * future values: "tile", "shortcut", "widget".
 */
@Serializable
data class Layout(
    val screens: List<Screen> = emptyList(),
) {
    companion object {
        fun empty(): Layout = Layout(screens = emptyList())
    }
}

@Serializable
data class Screen(
    val id: String,
    val grid: Grid,
)

@Serializable
data class Grid(
    val rows: Int,
    val cols: Int,
    val slots: List<Slot> = emptyList(),
)

@Serializable
data class Slot(
    val row: Int,
    val col: Int,
    val kind: String? = null,
)
