package com.launcher.api.wizard.data

import kotlinx.serialization.Serializable

@Serializable
data class TileSetBody(
    val tiles: List<TileSpec>,
)

@Serializable
data class TileSpec(
    val position: GridPosition,
    val actionType: String,
    val labelKey: String,
    val iconKey: String,
)

@Serializable
data class GridPosition(
    val row: Int,
    val col: Int,
)
