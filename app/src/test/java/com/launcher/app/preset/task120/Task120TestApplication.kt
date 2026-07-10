package com.launcher.app.preset.task120

import android.app.Application

/**
 * Minimal Application for TASK-120 Robolectric tests — avoids starting Koin
 * (the production LauncherApplication starts Koin globally, which conflicts
 * with per-test isolation across Robolectric runs).
 */
class Task120TestApplication : Application()
