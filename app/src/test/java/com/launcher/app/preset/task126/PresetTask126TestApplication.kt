package com.launcher.app.preset.task126

import android.app.Application

/**
 * Minimal Application for TASK-126 Phase 1.7 Robolectric provider tests.
 * Mirrors [com.launcher.app.preset.task120.Task120TestApplication] — avoids
 * starting Koin globally so tests remain isolated across Robolectric runs.
 */
class PresetTask126TestApplication : Application()
