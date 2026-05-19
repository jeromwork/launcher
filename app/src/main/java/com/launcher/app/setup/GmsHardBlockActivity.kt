package com.launcher.app.setup

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.launcher.app.R
import com.launcher.ui.setup.GmsHardBlockScreen
import com.launcher.ui.theme.LauncherTheme

/**
 * Spec 010 T046 — Activity hosting the GMS-missing hard-block screen
 * (FR-042 / FR-043 / FR-044).
 *
 * Launched by `FirstLaunchActivity` when [com.launcher.api.setup.GmsAvailabilityPort.status]
 * returns [com.launcher.api.setup.GmsStatus.MissingFatal]. Reachable only via
 * explicit Intent from inside the app — declared `android:exported="false"`
 * (plan §11 C-9 + Spec010IsolationTest T010).
 *
 * On «Понятно»: calls [finishAffinity] to close the entire task — there is no
 * way forward without GMS.
 *
 * On the «Подробнее» link: launches `ACTION_VIEW` for the Google Play Help
 * article. Fail-safe: if no browser is installed, [startActivity] throws
 * `ActivityNotFoundException` — we catch it (the failure is visually
 * indistinguishable to the user and there's nothing else to do).
 */
class GmsHardBlockActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LauncherTheme(preset = null) {
                GmsHardBlockScreen(
                    title = getString(R.string.setup_gms_hard_block_title),
                    body = getString(R.string.setup_gms_hard_block_body),
                    learnMoreLabel = getString(R.string.setup_gms_hard_block_learn_more),
                    okLabel = getString(R.string.setup_gms_hard_block_ok),
                    onLearnMore = ::openHelpArticle,
                    onOk = ::finishAffinity,
                )
            }
        }
    }

    private fun openHelpArticle() {
        try {
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(GMS_HELP_URL))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (_: android.content.ActivityNotFoundException) {
            // No browser installed — nothing to do; UI stays на той же странице
            // (user can still tap «Понятно» to close).
        }
    }

    companion object {
        private const val GMS_HELP_URL =
            "https://support.google.com/googleplay/answer/9037938"
    }
}
