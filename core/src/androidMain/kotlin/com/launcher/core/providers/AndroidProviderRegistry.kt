package com.launcher.core.providers

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Telephony
import com.launcher.api.action.InstallHint
import com.launcher.api.action.NotApplicableReason
import com.launcher.api.action.ProviderAvailability
import com.launcher.api.action.ProviderId
import com.launcher.api.action.ProviderRegistry
import com.launcher.api.action.ProviderState
import com.launcher.core.catalog.AppIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn

/**
 * Android implementation of [ProviderRegistry] (spec 005 §4.1, US-507).
 *
 * Source of truth for *availability* combines:
 *  - [AppIndex.snapshot] (driven by `PackageManager` + `PackageSetChanged` events)
 *    for app-presence checks: WhatsApp, Telegram, YouTube, Play Store.
 *  - One-shot device-feature probes for `phone`, `sms`, `browser`:
 *    `FEATURE_TELEPHONY` for phone, default-SMS-app for sms,
 *    a chooser-resolution for `https:` for browser. These are evaluated
 *    once at construction; they don't change at runtime within a session.
 *  - `system_settings` is always [ProviderAvailability.Available] on Android.
 *
 * Per Clarification C3 the [updates] stream is debounced 1s and de-duplicated
 * to avoid emitting on every transient PackageManager flap.
 *
 * Threading: [updates] runs on the supplied [scope]; consumers (e.g. wizard
 * UI) collect on Main and don't see PackageManager work directly.
 */
@OptIn(FlowPreview::class)
class AndroidProviderRegistry(
    context: Context,
    private val appIndex: AppIndex,
    scope: CoroutineScope,
    private val pm: PackageManager = context.packageManager,
    private val appContext: Context = context.applicationContext,
) : ProviderRegistry {

    private val phoneApplicability: NotApplicableReason? = run {
        val telephony = pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
        if (telephony) null else NotApplicableReason.NoTelephony
    }
    private val smsApplicability: NotApplicableReason? = run {
        if (Telephony.Sms.getDefaultSmsPackage(appContext) != null) null
        else NotApplicableReason.NoDefaultSmsApp
    }
    private val browserApplicability: NotApplicableReason? = run {
        val httpProbe = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
        if (pm.resolveActivity(httpProbe, 0) != null) null
        else NotApplicableReason.NoBrowser
    }

    /** Triggered manually for test / debug; production also re-derives via [appIndex.snapshot]. */
    private val refreshSignal = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 8)

    private val statesFlow: Flow<List<ProviderState>> =
        combine(appIndex.snapshot, refreshSignal.onStart { emit(Unit) }) { snapshot, _ ->
            buildStates(snapshot.entries.map { it.stableKey }.toSet())
        }
            .debounce(DEBOUNCE_MS)
            .distinctUntilChanged()

    private val sharedStates = statesFlow.shareIn(scope, SharingStarted.Eagerly, replay = 1)

    override val updates: Flow<List<ProviderState>> = sharedStates

    override fun availability(providerId: ProviderId): ProviderAvailability {
        val installedPackages = appIndex.snapshot.value.entries.map { it.stableKey }.toSet()
        return availabilityFor(providerId, installedPackages)
    }

    override fun snapshot(): List<ProviderState> {
        val installedPackages = appIndex.snapshot.value.entries.map { it.stableKey }.toSet()
        return buildStates(installedPackages)
    }

    private fun buildStates(installed: Set<String>): List<ProviderState> = KNOWN_PROVIDERS.map { id ->
        ProviderState(
            providerId = id,
            availability = availabilityFor(id, installed),
            installedPackage = firstInstalledPackage(id, installed),
        )
    }

    private fun availabilityFor(providerId: ProviderId, installed: Set<String>): ProviderAvailability =
        when (providerId) {
            ProviderId.PHONE   -> phoneApplicability?.let(ProviderAvailability::NotApplicable)
                ?: ProviderAvailability.Available
            ProviderId.SMS     -> smsApplicability?.let(ProviderAvailability::NotApplicable)
                ?: ProviderAvailability.Available
            ProviderId.BROWSER -> browserApplicability?.let(ProviderAvailability::NotApplicable)
                ?: ProviderAvailability.Available
            ProviderId.SYSTEM_SETTINGS -> ProviderAvailability.Available
            ProviderId.APP -> ProviderAvailability.Available
            ProviderId.WHATSAPP -> packageAvailability(providerId, WHATSAPP_PACKAGES, installed)
            ProviderId.TELEGRAM -> packageAvailability(providerId, TELEGRAM_PACKAGES, installed)
            ProviderId.YOUTUBE  -> packageAvailability(providerId, YOUTUBE_PACKAGES, installed)
            else -> ProviderAvailability.Missing(installHint = null)
        }

    private fun packageAvailability(
        id: ProviderId,
        candidates: List<String>,
        installed: Set<String>,
    ): ProviderAvailability {
        return if (candidates.any { it in installed }) {
            ProviderAvailability.Available
        } else {
            ProviderAvailability.Missing(installHint = installHintFor(id))
        }
    }

    private fun firstInstalledPackage(id: ProviderId, installed: Set<String>): String? = when (id) {
        ProviderId.WHATSAPP -> WHATSAPP_PACKAGES.firstOrNull { it in installed }
        ProviderId.TELEGRAM -> TELEGRAM_PACKAGES.firstOrNull { it in installed }
        ProviderId.YOUTUBE  -> YOUTUBE_PACKAGES.firstOrNull  { it in installed }
        else -> null
    }

    private fun installHintFor(id: ProviderId): InstallHint? = when (id) {
        ProviderId.WHATSAPP -> InstallHint(
            storeUrl = MARKET_DETAILS + "com.whatsapp",
            webStoreUrl = WEB_DETAILS + "com.whatsapp",
            recommendedPackage = "com.whatsapp",
        )
        ProviderId.TELEGRAM -> InstallHint(
            storeUrl = MARKET_DETAILS + "org.telegram.messenger",
            webStoreUrl = WEB_DETAILS + "org.telegram.messenger",
            recommendedPackage = "org.telegram.messenger",
        )
        ProviderId.YOUTUBE -> InstallHint(
            storeUrl = MARKET_DETAILS + "com.google.android.youtube",
            webStoreUrl = WEB_DETAILS + "com.google.android.youtube",
            recommendedPackage = "com.google.android.youtube",
        )
        else -> null
    }

    /** Test/debug hook to recompute states without waiting for an AppIndex update. */
    fun refresh() {
        refreshSignal.tryEmit(Unit)
    }

    companion object {
        private const val DEBOUNCE_MS = 1_000L

        // Aligned with manifest <queries> in app/src/main/AndroidManifest.xml.
        private val WHATSAPP_PACKAGES = listOf("com.whatsapp", "com.whatsapp.w4b")
        private val TELEGRAM_PACKAGES = listOf("org.telegram.messenger", "org.telegram.plus")
        private val YOUTUBE_PACKAGES  = listOf("com.google.android.youtube")

        private const val MARKET_DETAILS = "market://details?id="
        private const val WEB_DETAILS = "https://play.google.com/store/apps/details?id="

        val KNOWN_PROVIDERS: List<ProviderId> = listOf(
            ProviderId.APP,
            ProviderId.WHATSAPP,
            ProviderId.TELEGRAM,
            ProviderId.PHONE,
            ProviderId.SMS,
            ProviderId.BROWSER,
            ProviderId.YOUTUBE,
            ProviderId.SYSTEM_SETTINGS,
        )
    }
}
