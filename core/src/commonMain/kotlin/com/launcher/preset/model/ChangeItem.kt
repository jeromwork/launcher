package com.launcher.preset.model

sealed class ChangeItem {
    data class Added(val id: String, val component: Component) : ChangeItem()
    data class Removed(val id: String) : ChangeItem()
    data class ParamsChanged(val id: String, val newComponent: Component) : ChangeItem()
}
