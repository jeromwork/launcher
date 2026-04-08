package com.launcher.app.safety

import android.content.Context

class CaregiverSessionStore(
    context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isSessionActive(nowMs: Long = System.currentTimeMillis()): Boolean {
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)
        return expiresAt > nowMs
    }

    fun grantSession(minutes: Int, nowMs: Long = System.currentTimeMillis()) {
        val expiresAt = nowMs + minutes * 60_000L
        prefs.edit().putLong(KEY_EXPIRES_AT, expiresAt).apply()
    }

    companion object {
        private const val PREFS = "caregiver_session"
        private const val KEY_EXPIRES_AT = "expires_at"
    }
}

