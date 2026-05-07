package com.launcher.core.actions

class ActionCycleGuard {
    private val activeByTile = mutableMapOf<String, String>()

    @Synchronized
    fun beginCycle(tileId: String, cycleId: String): Boolean {
        if (tileId.isBlank() || cycleId.isBlank()) return false
        if (activeByTile.containsKey(tileId)) return false
        activeByTile[tileId] = cycleId
        return true
    }

    @Synchronized
    fun clearByCycle(cycleId: String) {
        if (cycleId.isBlank()) return
        val tile = activeByTile.entries.firstOrNull { it.value == cycleId }?.key ?: return
        activeByTile.remove(tile)
    }

    @Synchronized
    fun clearByTile(tileId: String) {
        if (tileId.isBlank()) return
        activeByTile.remove(tileId)
    }
}

