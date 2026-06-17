package com.launcher.api.wizard

/**
 * Wizard-domain Clock port (per A-18).
 *
 * Returns epoch-millis to match the existing project convention (DocSnapshot,
 * config sync, etc.). kotlinx-datetime is not in the project today.
 *
 * Real adapter: [com.launcher.adapters.wizard.SystemClock] in androidMain
 * (wraps `System.currentTimeMillis()`).
 * Fake adapter: [com.launcher.api.wizard.fakes.FakeClock] in commonTest.
 */
interface Clock {
    fun nowEpochMillis(): Long
}
