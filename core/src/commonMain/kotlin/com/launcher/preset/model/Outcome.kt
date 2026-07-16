package com.launcher.preset.model

sealed class Outcome {
    object Ok : Outcome()
    object NeedsApply : Outcome()
    data class Failed(val reason: FailReason) : Outcome()
    object Unsupported : Outcome()

    /**
     * T127-005 (FR-014) — the provider fired its intent(s) but the OS exposes no
     * read-back, so it cannot report success or failure honestly (e.g. hiding the
     * system status bar). The engine records [ComponentStatus.Unverifiable]; the
     * interactive path asks the human («open settings, turn on X, come back, tap
     * "I did it"»).
     *
     * MUST NOT be turned into [Ok] — that would make `BootCheck` trust a fiction.
     */
    object NeedsUserConfirmation : Outcome()
}
