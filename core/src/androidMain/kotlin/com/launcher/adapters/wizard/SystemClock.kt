package com.launcher.adapters.wizard

import com.launcher.api.wizard.Clock

class SystemClock : Clock {
    override fun nowEpochMillis(): Long = System.currentTimeMillis()
}
