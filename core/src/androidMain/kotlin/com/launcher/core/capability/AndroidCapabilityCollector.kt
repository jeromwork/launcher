package com.launcher.core.capability

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.pm.PackageInfoCompat
import com.launcher.api.action.ProviderAvailability
import com.launcher.api.action.ProviderId
import com.launcher.api.action.ProviderRegistry
import com.launcher.api.action.ProviderState
import com.launcher.api.capability.Capability
import com.launcher.api.capability.IconRef
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Translates [ProviderRegistry] snapshots (spec 005 source-of-truth for package
 * detection) into spec 006 [Capability] snapshots.
 *
 * Why we wrap [ProviderRegistry] вместо own PackageManager wiring:
 *  - Single source of truth — [com.launcher.core.providers.AndroidProviderRegistry]
 *    already wires `AppIndex` (which observes `PACKAGE_ADDED/REMOVED/REPLACED`
 *    broadcasts) и применяет debounce 1s + distinctUntilChanged per Clarification C3.
 *  - Spec 006 FR-007 requires "ProviderState полностью заменить на Capability"
 *    в новых call sites; existing spec 005 wiring остаётся (deferred wizard
 *    integration спека 010 still reads `ProviderState` directly until then).
 *  - Adding `versionCode` (FR-001) — additive enrichment поверх ProviderState
 *    via `PackageManager.getPackageInfo(name, 0).longVersionCode`.
 *
 * Threading: upstream flow (from [ProviderRegistry.updates]) runs on its own
 * coroutine scope; our `map` block does PackageManager `getPackageInfo` which
 * is blocking-ish (file I/O). Caller (`AndroidCapabilityRepository`) collects
 * on a background dispatcher.
 *
 * On unknown providerId-to-package mapping (e.g. PHONE / SMS / BROWSER which
 * are device-feature based, not package-based): `versionCode = null` (already
 * the field default — these are "always available" platform capabilities).
 */
class AndroidCapabilityCollector(
    private val context: Context,
    private val providerRegistry: ProviderRegistry,
) {
    private val pm: PackageManager = context.packageManager

    /**
     * Hot flow of capability snapshots. Re-emits whenever upstream
     * [ProviderRegistry.updates] emits, after enriching each entry with
     * `versionCode` from PackageManager.
     */
    val snapshots: Flow<List<Capability>> = providerRegistry.updates
        .map { providerStates -> providerStates.map { it.toCapability() } }
        .distinctUntilChanged()

    /** One-shot read используется на cold-start до first upstream emission. */
    fun snapshot(): List<Capability> =
        providerRegistry.snapshot().map { it.toCapability() }

    private fun ProviderState.toCapability(): Capability = Capability(
        providerId = providerId,
        displayName = displayNameFor(providerId),
        iconId = IconRef.bundled(providerId.value),
        available = availability is ProviderAvailability.Available,
        versionCode = installedPackage?.let { versionCodeOf(it) },
    )

    private fun versionCodeOf(packageName: String): Long? = try {
        @Suppress("DEPRECATION")
        val info = pm.getPackageInfo(packageName, 0)
        PackageInfoCompat.getLongVersionCode(info)
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    private fun displayNameFor(providerId: ProviderId): String = DISPLAY_NAMES[providerId.value]
        ?: providerId.value.replaceFirstChar(Char::titlecase)

    companion object {
        /**
         * Static display names mapped to known provider ids. Localisation: these
         * are brand names (WhatsApp, Telegram) или product-neutral words (Phone,
         * SMS) — see спец 010 Setup Assistant copy review для localisation
         * decisions if переводим product nouns.
         */
        private val DISPLAY_NAMES: Map<String, String> = mapOf(
            "app"             to "Apps",
            "whatsapp"        to "WhatsApp",
            "telegram"        to "Telegram",
            "phone"           to "Phone",
            "sms"             to "SMS",
            "browser"         to "Browser",
            "youtube"         to "YouTube",
            "system_settings" to "Settings",
        )
    }
}
