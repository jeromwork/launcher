package com.launcher.core.capability

import com.launcher.api.capability.IconRef
import com.launcher.api.capability.IconResolution
import com.launcher.api.capability.IconStorage
import com.launcher.core.R
import com.launcher.core.diagnostics.RecoveryEventLogger

/**
 * Sole [IconStorage] implementation в спеке 006: maps `bundled:<name>` to
 * APK drawable resources per FR-010 + [`contracts/icon-id-namespace.md`](specs/006-provider-capabilities-and-health/contracts/icon-id-namespace.md).
 *
 * Behaviour:
 *  - `bundled:whatsapp` (and 7 other known providers) → [IconResolution.Drawable].
 *  - `bundled:<unknown_name>` → [IconResolution.Placeholder] + log
 *    `missing_resource` event.
 *  - `custom:<...>` / `private:<...>` → [IconResolution.Placeholder] (known
 *    namespaces, but no resolver в спеке 006 — спек 007 добавит RemoteIconStorage
 *    для `custom:`, спек 011 для `private:`).
 *  - Unknown namespace OR invalid format → [IconResolution.NotFound] + log
 *    `unknown_namespace` event.
 *
 * Pure function — никакого I/O для bundled namespace, можно вызвать из
 * Composable без main-thread-blocking концерна.
 */
class BundledIconStorage(
    private val logger: RecoveryEventLogger? = null,
) : IconStorage {

    override fun resolve(iconId: String): IconResolution {
        val namespace = IconRef.namespaceOf(iconId)
        val name = IconRef.nameOf(iconId)

        if (namespace == null || name == null || !IconRef.isValid(iconId)) {
            logger?.log(RecoveryEventLogger.Category.UnknownNamespace, "invalid_format")
            return IconResolution.NotFound
        }

        return when (namespace) {
            IconRef.NAMESPACE_BUNDLED -> resolveBundled(name)
            IconRef.NAMESPACE_CUSTOM, IconRef.NAMESPACE_PRIVATE -> {
                // Known namespace, no resolver в спеке 006. UI shows placeholder.
                IconResolution.Placeholder
            }
            else -> {
                logger?.log(
                    RecoveryEventLogger.Category.UnknownNamespace,
                    "unknown_ns",
                    mapOf("ns" to namespace),
                )
                IconResolution.NotFound
            }
        }
    }

    private fun resolveBundled(name: String): IconResolution {
        val resourceId = BUNDLED_DRAWABLES[name]
        return if (resourceId != null) {
            IconResolution.Drawable(resourceId)
        } else {
            logger?.log(
                RecoveryEventLogger.Category.MissingResource,
                "bundled_drawable_missing",
                mapOf("name" to name),
            )
            IconResolution.Placeholder
        }
    }

    companion object {
        /**
         * Source of truth for `bundled:<name>` → drawable resource mapping.
         * Names align with [com.launcher.api.action.ProviderId] constants.
         * Drawables themselves live в `core/src/androidMain/res/drawable/`
         * (added in spec 006 Phase 8 — placeholder Material 3 vector style;
         * real brand assets TBD before public release).
         */
        private val BUNDLED_DRAWABLES: Map<String, Int> = mapOf(
            "app"             to R.drawable.provider_app,
            "whatsapp"        to R.drawable.provider_whatsapp,
            "telegram"        to R.drawable.provider_telegram,
            "phone"           to R.drawable.provider_phone,
            "sms"             to R.drawable.provider_sms,
            "browser"         to R.drawable.provider_browser,
            "youtube"         to R.drawable.provider_youtube,
            "system_settings" to R.drawable.provider_system_settings,
        )
    }
}
