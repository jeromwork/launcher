package com.launcher.test.fakes

import com.launcher.api.capability.IconResolution
import com.launcher.api.capability.IconStorage

/**
 * In-memory fake of [IconStorage] для тестов (FR-048).
 *
 * Default behaviour — все unknown iconIds возвращают [IconResolution.Placeholder]
 * (а не NotFound), чтобы тесты UI не падали при отсутствующих регистрациях.
 * Используйте [register] для конкретных iconId → IconResolution mappings.
 */
class FakeIconStorage : IconStorage {
    private val map = mutableMapOf<String, IconResolution>()

    override fun resolve(iconId: String): IconResolution =
        map[iconId] ?: IconResolution.Placeholder

    /** Test helper: register a specific resolution for an iconId. */
    fun register(iconId: String, resolution: IconResolution) {
        map[iconId] = resolution
    }

    /** Test helper: clear all registrations. */
    fun clear() = map.clear()
}
