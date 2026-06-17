package com.launcher.api.wizard.data

import kotlinx.serialization.Serializable

@Serializable
data class ScreenLayoutBody(
    val gridRows: Int,
    val gridCols: Int,
    val bottomToolbar: ToolbarSpec? = null,
    val topTabs: List<TabSpec>? = null,
)

@Serializable
data class ToolbarSpec(
    val actions: List<String>,
)

@Serializable
data class TabSpec(
    val labelKey: String,
    val iconKey: String,
)
