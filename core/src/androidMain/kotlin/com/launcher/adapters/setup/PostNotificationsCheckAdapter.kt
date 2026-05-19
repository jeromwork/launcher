package com.launcher.adapters.setup

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.launcher.api.setup.CheckStatus
import com.launcher.api.setup.Criticality
import com.launcher.api.setup.IntentSpec
import com.launcher.api.setup.SetupCheck
import com.launcher.api.setup.Surface

/**
 * Spec 010 T048 — POST_NOTIFICATIONS runtime-permission check (FR-018).
 *
 * Behaviour:
 *  - **API < 33** (Android 12 and below): permission did not exist as
 *    runtime-grantable; always returns [CheckStatus.Ok] so the badge does
 *    not yell at users who can never grant it anyway.
 *  - **API ≥ 33**: reads [ContextCompat.checkSelfPermission]; returns
 *    [CheckStatus.Ok] when granted, [CheckStatus.NotConfigured] otherwise.
 *
 * Criticality is [Criticality.Recommended] — declining notifications doesn't
 * break the launcher's core function; surfaces as `?M` not `!N` (FR-019).
 */
class PostNotificationsCheckAdapter(
    private val context: Context,
) : SetupCheck {

    override val id: String = "post_notifications"
    override val criticality: Criticality = Criticality.Recommended
    override val surfaces: Set<Surface> = setOf(Surface.Settings)

    override suspend fun check(): CheckStatus {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return CheckStatus.Ok
        }
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        return if (granted) CheckStatus.Ok
        else CheckStatus.NotConfigured(reason = "post_notifications_not_granted")
    }

    override fun resolveIntent(): IntentSpec =
        IntentSpec(
            category = "permission.post_notifications",
            action = "request",
        )
}
