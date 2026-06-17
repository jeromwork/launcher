package com.launcher.app.wizard

import com.launcher.api.wizard.DiagnosticEmitter
import com.launcher.api.wizard.DiagnosticEvent
import com.launcher.api.wizard.PermissionRequestPort
import com.launcher.api.wizard.PermissionResult

/**
 * No-op emitter used in F-3 (per A-17 — analytics ships in S-1+).
 *
 * Logs to system Log for visibility; replaced by a real emitter in S-1.
 */
class NoopDiagnosticEmitter : DiagnosticEmitter {
    override fun emit(event: DiagnosticEvent) {
        android.util.Log.d("WizardDiagnostic", event::class.simpleName ?: event.toString())
    }
}

/**
 * Stub PermissionRequestPort wired into the Koin graph. WizardActivity
 * overrides this binding at runtime with a real ActivityResultLauncher-backed
 * implementation registered in onCreate.
 *
 * Returns Denied unconditionally — never invoked from a real wizard flow.
 */
class NoopPermissionRequestPort : PermissionRequestPort {
    override suspend fun request(permission: String): PermissionResult = PermissionResult.Denied
    override fun isGranted(permission: String): Boolean = false
    override fun isPermanentlyDenied(permission: String): Boolean = false
}
