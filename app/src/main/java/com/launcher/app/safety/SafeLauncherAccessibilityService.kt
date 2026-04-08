package com.launcher.app.safety

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.launcher.api.ControlMode
import com.launcher.api.EscapeVector
import com.launcher.app.LauncherApplication

class SafeLauncherAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString() ?: return
        val app = application as? LauncherApplication ?: return
        val mode = app.core.controlModeStore.get()
        val allowedMessengers = app.core.safeControlConfigStore.load().allowedMessengerPackages
        val isAllowed = packageName == this.packageName || packageName in allowedMessengers
        if (isAllowed) {
            return
        }
        val decision = app.core.escapeProtectionPolicyEngine.decisionFor(EscapeVector.HOME)
        app.core.safetyDiagnostics.escapeAttempt(EscapeVector.HOME, mode, "accessibility_foreground_change")
        if (decision.shouldAttemptRecovery) {
            app.core.safetyDiagnostics.escapeRecoveredToHome(EscapeVector.HOME, mode, decision.reasonCode)
            bringLauncherToFront()
        } else {
            app.core.safetyDiagnostics.escapeBlocked(
                EscapeVector.HOME,
                mode,
                if (mode == ControlMode.STRICT) "strict_recover_unavailable" else "standard_limited",
            )
        }
    }

    override fun onInterrupt() = Unit

    private fun bringLauncherToFront() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { startActivity(homeIntent) }
    }
}
