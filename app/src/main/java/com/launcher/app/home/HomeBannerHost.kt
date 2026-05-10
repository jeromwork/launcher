package com.launcher.app.home

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.launcher.api.alerts.AlertBanner
import com.launcher.api.alerts.AlertBannerStateProvider
import com.launcher.app.R
import com.launcher.core.diagnostics.RecoveryEventLogger
import com.launcher.ui.components.AlertBannerData
import com.launcher.ui.components.AlertBannerStack
import org.koin.compose.koinInject

/**
 * Android-side glue between [AlertBannerStateProvider] (domain logic) and
 * [AlertBannerStack] (commonMain UI). Resolves icons + strings + system intents
 * here потому что они Android-specific (FR-047 — keep Android types out of
 * commonMain).
 *
 * Wired into HomeScreen layout at top, above flow grid (FR-026/027).
 *
 * On action button click:
 *  - Airplane → open `Settings.ACTION_AIRPLANE_MODE_SETTINGS`, fallback to
 *    `Settings.ACTION_WIRELESS_SETTINGS`, fallback to toast «функция
 *    недоступна» (FR-026 + FR-050).
 *  - Mute → `AudioManager.setStreamVolume(STREAM_RING, max/2)`. On
 *    SecurityException (DND restricting) → toast (FR-027 + FR-050).
 *
 * Failures всегда логируются через [RecoveryEventLogger] (FR-052), button
 * stays enabled for retry, banner stays visible (state не изменилось).
 */
@Composable
fun HomeBannerHost(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val provider = koinInject<AlertBannerStateProvider>()
    val logger = koinInject<RecoveryEventLogger>()
    val banners by provider.observe().collectAsState(initial = emptySet())

    val airplanePainter = painterResource(R.drawable.ic_airplane)
    val mutePainter = painterResource(R.drawable.ic_volume_off)
    val airplaneText = stringResource(R.string.banner_airplane_text)
    val airplaneAction = stringResource(R.string.banner_airplane_action)
    val airplaneIconDesc = stringResource(R.string.banner_airplane_icon_desc)
    val muteText = stringResource(R.string.banner_mute_text)
    val muteAction = stringResource(R.string.banner_mute_action)
    val muteIconDesc = stringResource(R.string.banner_mute_icon_desc)

    val data: List<AlertBannerData> = banners.mapNotNull { banner ->
        when (banner) {
            AlertBanner.Airplane -> AlertBannerData(
                icon = airplanePainter,
                iconContentDescription = airplaneIconDesc,
                text = airplaneText,
                actionLabel = airplaneAction,
                onAction = { handleAirplaneAction(context, logger) },
            )
            AlertBanner.Mute -> AlertBannerData(
                icon = mutePainter,
                iconContentDescription = muteIconDesc,
                text = muteText,
                actionLabel = muteAction,
                onAction = { handleMuteAction(context, logger) },
            )
        }
    }

    // AlertBannerStack itself fills max width via inner Card composables.
    AlertBannerStack(banners = data, modifier = modifier)
}

/**
 * Open airplane mode settings. FR-026 primary + fallback chain per FR-050.
 */
private fun handleAirplaneAction(context: Context, logger: RecoveryEventLogger) {
    val primary = Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val fallback = Intent(Settings.ACTION_WIRELESS_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(primary)
    } catch (_: ActivityNotFoundException) {
        try {
            context.startActivity(fallback)
        } catch (_: ActivityNotFoundException) {
            logger.log(
                RecoveryEventLogger.Category.UserActionFailed,
                "airplane_settings_unavailable",
            )
            Toast.makeText(context, R.string.toast_feature_unavailable, Toast.LENGTH_SHORT).show()
        }
    } catch (t: Throwable) {
        logger.log(
            RecoveryEventLogger.Category.SystemApiFailure,
            "airplane_settings_throw",
            mapOf("err" to (t.message ?: "unknown").take(40)),
        )
        Toast.makeText(context, R.string.toast_feature_unavailable, Toast.LENGTH_SHORT).show()
    }
}

/**
 * Raise ringer volume to 50%. FR-027 + FR-050 fallback.
 *
 * SecurityException возможен если system DND policy запрещает изменения
 * (требовался бы ACCESS_NOTIFICATION_POLICY, который мы намеренно
 * **не запрашиваем** per NFR-N02 — DND user choice respected).
 */
private fun handleMuteAction(context: Context, logger: RecoveryEventLogger) {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    if (audioManager == null) {
        logger.log(RecoveryEventLogger.Category.SystemApiFailure, "audio_service_null")
        Toast.makeText(context, R.string.toast_action_failed_audio, Toast.LENGTH_SHORT).show()
        return
    }
    try {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING)
        val target = (max / 2).coerceAtLeast(1)
        audioManager.setStreamVolume(AudioManager.STREAM_RING, target, 0)
    } catch (_: SecurityException) {
        // DND policy restricting ringer changes. Cannot be bypassed without
        // ACCESS_NOTIFICATION_POLICY (NFR-N02 forbids requesting it).
        logger.log(RecoveryEventLogger.Category.UserActionFailed, "stream_volume_dnd_blocked")
        Toast.makeText(context, R.string.toast_action_failed_audio, Toast.LENGTH_SHORT).show()
    } catch (t: Throwable) {
        logger.log(
            RecoveryEventLogger.Category.SystemApiFailure,
            "set_stream_volume_throw",
            mapOf("err" to (t.message ?: "unknown").take(40)),
        )
        Toast.makeText(context, R.string.toast_action_failed_audio, Toast.LENGTH_SHORT).show()
    }
}
