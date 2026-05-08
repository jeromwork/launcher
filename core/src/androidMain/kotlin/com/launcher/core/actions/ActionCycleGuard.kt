package com.launcher.core.actions

class ActionCycleGuard {
    private val lock = Any()
    private val activeByTile = mutableMapOf<String, String>()

    fun beginCycle(tileId: String, cycleId: String): Boolean = synchronized(lock) {
        if (tileId.isBlank() || cycleId.isBlank()) return@synchronized false
        if (activeByTile.containsKey(tileId)) return@synchronized false
        activeByTile[tileId] = cycleId
        true
    }

    fun clearByCycle(cycleId: String): Unit = synchronized(lock) {
        if (cycleId.isBlank()) return@synchronized
        val tile = activeByTile.entries.firstOrNull { it.value == cycleId }?.key ?: return@synchronized
        activeByTile.remove(tile)
    }

    fun clearByTile(tileId: String): Unit = synchronized(lock) {
        if (tileId.isBlank()) return@synchronized
        activeByTile.remove(tileId)
    }
}
