package com.launcher.adapters.setup

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.launcher.api.setup.CheckStatus
import com.launcher.api.setup.Criticality
import com.launcher.api.setup.IntentSpec
import com.launcher.api.setup.SetupCheck
import com.launcher.api.setup.Surface

/**
 * Spec 010 T060 — CALL_PHONE runtime-permission check (FR-018).
 *
 * Criticality is [Criticality.Required]: without CALL_PHONE the launcher's
 * call-tile two-tap promise (SC-003) degrades to dial-tile three-taps. We
 * surface that as a Required `!N` badge because the call-tile UX is
 * core to спека 010 (Article XIV senior-safe).
 */
class CallPhoneCheckAdapter(
    private val context: Context,
) : SetupCheck {

    override val id: String = "call_phone"
    override val criticality: Criticality = Criticality.Required
    override val surfaces: Set<Surface> = setOf(Surface.Settings)

    override suspend fun check(): CheckStatus {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE,
        ) == PackageManager.PERMISSION_GRANTED
        return if (granted) CheckStatus.Ok
        else CheckStatus.NotConfigured(reason = "call_phone_not_granted")
    }

    override fun resolveIntent(): IntentSpec =
        IntentSpec(
            category = "permission.call_phone",
            action = "request",
        )
}
