package com.launcher.core.actions

import android.content.Context
import com.launcher.api.ControlMode

class ControlModeStore(
    context: Context,
    private val defaultMode: ControlMode,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun get(): ControlMode {
        val raw = prefs.getString(KEY_MODE, defaultMode.name) ?: defaultMode.name
        return runCatching { ControlMode.valueOf(raw) }.getOrDefault(defaultMode)
    }

    fun set(mode: ControlMode) {
        prefs.edit().putString(KEY_MODE, mode.name).apply()
    }

    companion object {
        private const val PREFS_NAME = "safe_control_mode"
        private const val KEY_MODE = "mode"
    }
}

