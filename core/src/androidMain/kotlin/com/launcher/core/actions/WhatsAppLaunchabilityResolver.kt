package com.launcher.core.actions

import android.content.Context
import com.launcher.api.CommunicationActionType

enum class WhatsAppLaunchability {
    AVAILABLE,
    WHATSAPP_UNAVAILABLE,
    ACTION_NOT_SUPPORTED,
}

class WhatsAppLaunchabilityResolver(
    private val context: Context,
    private val configValidator: CommunicationConfigValidator,
) {
    fun canLaunch(contactRef: String, actionType: CommunicationActionType): WhatsAppLaunchability {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(WHATSAPP_PACKAGE)
        if (launchIntent == null) {
            return WhatsAppLaunchability.WHATSAPP_UNAVAILABLE
        }
        if (!configValidator.isActionSupported(contactRef, actionType)) {
            return WhatsAppLaunchability.ACTION_NOT_SUPPORTED
        }
        return WhatsAppLaunchability.AVAILABLE
    }

    companion object {
        const val WHATSAPP_PACKAGE: String = "com.whatsapp"
    }
}

