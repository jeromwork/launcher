package com.launcher.ui.gate

/**
 * Spec 010 FR-039 — localized labels passed from the platform host (`:app`)
 * to [ChallengeGateScreen] / [com.launcher.ui.RootContent].
 *
 * Why a data class (not just two function params): RootContent already
 * accepts presetUiModels list as an opaque pre-localized bag (см.
 * PresetUiModel) — same pattern keeps Android `stringResource` lookups in
 * the activity layer and commonMain UI platform-pure (CLAUDE.md rule 1).
 *
 *  - [cancelLabel]: resolves `R.string.challenge_gate_cancel`.
 *  - [sequenceInstructionTemplate]: takes the comma-separated expected
 *    button order, returns a localized sentence. Resolves
 *    `R.string.challenge_gate_sequence_instruction` (format arg %1$s).
 *    Format: `getString(R.string.challenge_gate_sequence_instruction, sequence)`.
 */
data class ChallengeGateLabels(
    val cancelLabel: String,
    val sequenceInstructionTemplate: (sequence: String) -> String,
) {
    companion object {
        /**
         * Hardcoded Russian fallback for tests + commonTest invocations
         * where no Android `stringResource` lookup is available. Production
         * host (HomeActivity) MUST pass real localized labels.
         */
        val DefaultRussianFallback: ChallengeGateLabels = ChallengeGateLabels(
            cancelLabel = "Отмена",
            sequenceInstructionTemplate = { sequence -> "Нажми кнопки $sequence по порядку." },
        )
    }
}
