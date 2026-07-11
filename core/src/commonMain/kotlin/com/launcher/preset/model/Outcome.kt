package com.launcher.preset.model

sealed class Outcome {
    object Ok : Outcome()
    object NeedsApply : Outcome()
    data class Failed(val reason: FailReason) : Outcome()
    object Unsupported : Outcome()
}
