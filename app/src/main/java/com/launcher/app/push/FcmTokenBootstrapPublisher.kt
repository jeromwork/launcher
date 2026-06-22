package com.launcher.app.push

/**
 * T131 (spec 019 F-5c FR-027) — bridge между Sign-In/EnvelopeBootstrap success
 * и публикацией текущего FCM token. Реализация различается по flavor:
 *  • realBackend: достаёт текущий token через FirebaseMessaging.getInstance().token,
 *    публикует через [family.push.api.FcmTokenPublisher].
 *  • mockBackend: no-op.
 *
 * Существует чтобы `:app/src/main/.../LauncherApplication.kt` мог дёрнуть его
 * без прямого импорта Firebase SDK (которого нет в mockBackend classpath).
 */
fun interface FcmTokenBootstrapPublisher {
    suspend fun publishCurrent()
}
