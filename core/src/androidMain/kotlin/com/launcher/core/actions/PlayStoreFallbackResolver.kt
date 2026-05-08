package com.launcher.core.actions

import com.launcher.api.action.Action
import com.launcher.api.action.ActionPayload
import com.launcher.api.action.ProviderId

/**
 * Builds the canonical "open Play Store for `<package>`" fallback [Action].
 *
 * Used by:
 *  - `AppLaunchHandler` (T545): when `getLaunchIntentForPackage` returns null,
 *    the handler asks this resolver for a Play-Store-bound fallback action.
 *  - DI wiring of WhatsApp / Telegram fallback chains in mock data: their
 *    second fallback is "open store entry for the missing app".
 *
 * Two-step strategy per spec 005 §7.3 (atomic dispatch — one Intent per call):
 *  - Primary: `market://details?id=<pkg>` — opens Play Store app if installed.
 *  - Secondary fallback (Action.fallback): `https://play.google.com/store/apps/details?id=<pkg>`
 *    — opens browser when Play Store app is missing (e.g. AOSP, Huawei without GMS).
 *
 * Rationale for class form (not top-level function): meta-min CHK-002 ruled
 * for class to allow DI / test substitution. A unit test pins a specific
 * package and asserts both URI variants in one place.
 */
class PlayStoreFallbackResolver {

    /**
     * Build a Play-Store-targeted [Action] for [packageName].
     *
     * The returned action carries `providerId = APP` (handled by `AppLaunchHandler`)
     * and a `OpenApp` payload pointing at the Play Store deep-link. The web URL
     * is wired as a `Url`/`browser` fallback so dispatch fails over cleanly.
     */
    fun resolve(packageName: String): Action {
        require(packageName.isNotBlank()) { "packageName must be non-blank" }
        return Action(
            providerId = ProviderId.APP,
            payload = ActionPayload.OpenApp(
                packageHint = PLAY_STORE_PACKAGE,
                storeUrlHint = "$MARKET_SCHEME$packageName",
            ),
            fallback = Action(
                providerId = ProviderId.BROWSER,
                payload = ActionPayload.Url("$WEB_PLAY_STORE_URL$packageName"),
            ),
        )
    }

    companion object {
        const val PLAY_STORE_PACKAGE = "com.android.vending"
        const val MARKET_SCHEME = "market://details?id="
        const val WEB_PLAY_STORE_URL = "https://play.google.com/store/apps/details?id="
    }
}
