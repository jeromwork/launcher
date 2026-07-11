package com.launcher.app.preset.task120.facade

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * ACL over the launcher's home-screen model. Domain calls this to place / query
 * tiles + toolbar; concrete impl bridges to whatever the current UI layer uses.
 *
 * MVP: in-memory placeholder — the actual launcher UI wiring for pin-protected
 * tiles + toolbar layouts lives in the draft-1 wizard refactor.
 */
interface HomeScreenFacade {
    fun hasTile(packageName: String): Boolean
    fun addTile(packageName: String, labelKey: String, iconKey: String?, pinProtected: Boolean)
    fun removeTile(packageName: String)
    fun getToolbar(): List<String>
    fun setToolbar(items: List<String>, layoutKey: String)
    fun hasSosTile(): Boolean
    fun addSosTile(pinProtected: Boolean)
}

class InMemoryHomeScreenFacade : HomeScreenFacade {
    private val tiles = MutableStateFlow<Map<String, TileMeta>>(emptyMap())
    private val toolbar = MutableStateFlow<List<String>>(emptyList())
    private val toolbarLayout = MutableStateFlow("layout.toolbar.minimal")
    private val sosPresent = MutableStateFlow(false)

    override fun hasTile(packageName: String): Boolean = tiles.value.containsKey(packageName)

    override fun addTile(packageName: String, labelKey: String, iconKey: String?, pinProtected: Boolean) {
        tiles.value = tiles.value + (packageName to TileMeta(labelKey, iconKey, pinProtected))
    }

    override fun removeTile(packageName: String) {
        tiles.value = tiles.value - packageName
    }

    override fun getToolbar(): List<String> = toolbar.value

    override fun setToolbar(items: List<String>, layoutKey: String) {
        toolbar.value = items
        toolbarLayout.value = layoutKey
    }

    override fun hasSosTile(): Boolean = sosPresent.value

    override fun addSosTile(pinProtected: Boolean) {
        sosPresent.value = true
    }

    private data class TileMeta(val labelKey: String, val iconKey: String?, val pinProtected: Boolean)
}
