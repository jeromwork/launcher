package com.launcher.cloud.impl

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.launcher.cloud.api.ActionContext
import com.launcher.cloud.api.ActionResult
import com.launcher.cloud.api.EmergencyNumberResolver
import com.launcher.cloud.api.LocalAlternative

/**
 * Локальный fallback для SOS. Не зависит от [com.launcher.cloud.api.CloudAvailability]
 * (INV-1). Использует `ACTION_DIAL` (не `ACTION_CALL`) — runtime permission не
 * нужен, user сам жмёт кнопку вызова в dialer'е.
 */
class SOSDialerAlternative(
    private val emergencyResolver: EmergencyNumberResolver,
    private val context: Context,
) : LocalAlternative {

    override suspend fun executeLocally(context: ActionContext): ActionResult {
        val number = emergencyResolver.getEmergencyNumber()
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        this.context.startActivity(intent)
        return ActionResult.Success()
    }
}
