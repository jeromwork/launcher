package com.launcher.api.wizard

import com.launcher.api.wizard.data.StepEntry

data class PendingStep(
    val stepEntry: StepEntry,
    val criticality: Criticality,
)

enum class Criticality { Required, Optional }
