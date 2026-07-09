package com.launcher.adapters.pools

import com.launcher.api.pools.Pool
import com.launcher.api.pools.PoolEntry
import com.launcher.api.pools.PoolSource
import com.launcher.api.preset.Criticality
import com.launcher.api.wizard.data.ApplySpec
import com.launcher.api.wizard.data.CheckSpec

/**
 * Primary [PoolSource] adapter for TASK-65 — pools are baked into binary as
 * Kotlin constants. Future [JsonAssetPoolSource] (scaffold) will load them
 * from assets without code change.
 *
 * Pool ids per ../../../docs/architecture/pool-naming.md:
 *   - "system-settings": OS-level requirements (HOME role, notifications, ...).
 *   - "ui-customization": app-internal preferences (font scale, theme, ...).
 *
 * TODO(TASK-73): entries below carry a single CheckSpec / ApplySpec, so on
 * Xiaomi MIUI / Huawei EMUI without GMS / Samsung One UI the behaviour may
 * silently fail. TASK-73 adds `perVendor` overrides + vendor-recipes wire
 * format so each pool entry can carry per-OEM alternatives without
 * duplicating the whole entry.
 */
class HardcodedPoolSource : PoolSource {

    override suspend fun load(poolId: String): Pool? = POOLS[poolId]

    override suspend fun version(poolId: String): Int? = POOLS[poolId]?.schemaVersion

    override suspend fun listEntries(poolId: String): List<PoolEntry> =
        POOLS[poolId]?.entries.orEmpty()

    private companion object {
        const val POOL_VERSION: Int = 1

        val SYSTEM_SETTINGS = Pool(
            id = "system-settings",
            schemaVersion = POOL_VERSION,
            entries = listOf(
                PoolEntry(
                    id = "android.role.home",
                    title = "settings_role_home_title",
                    description = "settings_role_home_description",
                    check = CheckSpec.AndroidRole(role = "android.app.role.HOME"),
                    apply = ApplySpec.AndroidRoleRequest(role = "android.app.role.HOME"),
                    criticality = Criticality.Required,
                ),
                PoolEntry(
                    id = "android.permission.POST_NOTIFICATIONS",
                    title = "settings_post_notifications_title",
                    description = "settings_post_notifications_description",
                    check = CheckSpec.AndroidPermission(
                        permission = "android.permission.POST_NOTIFICATIONS",
                    ),
                    apply = ApplySpec.StandardPermissionRequest(
                        permission = "android.permission.POST_NOTIFICATIONS",
                    ),
                    criticality = Criticality.Optional,
                ),
            ),
        )

        val UI_CUSTOMIZATION = Pool(
            id = "ui-customization",
            schemaVersion = POOL_VERSION,
            entries = listOf(
                PoolEntry(
                    id = "ui.font.large",
                    title = "settings_font_large_title",
                    description = "settings_font_large_description",
                    check = CheckSpec.UIFont(minScale = 1.3f),
                    apply = ApplySpec.InAppOnly,
                    criticality = Criticality.Optional,
                    defaultValue = "1.3",
                ),
            ),
        )

        val POOLS: Map<String, Pool> = mapOf(
            SYSTEM_SETTINGS.id to SYSTEM_SETTINGS,
            UI_CUSTOMIZATION.id to UI_CUSTOMIZATION,
        )
    }
}
