package com.launcher.api.wizard

import com.launcher.api.wizard.data.StepEntry

@Deprecated(
    "Superseded by TASK-120 ProfileComponent + ComponentStatus — see com.launcher.preset.model.ProfileComponent. Removal scheduled for the draft-1 wizard refactor.",
)
data class PendingStep(
    val stepEntry: StepEntry,
    val criticality: Criticality,
)

enum class Criticality { Required, Optional }
