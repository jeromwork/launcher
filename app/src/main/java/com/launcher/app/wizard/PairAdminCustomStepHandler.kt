package com.launcher.app.wizard

import android.content.Context
import android.content.Intent
import com.launcher.api.wizard.StepParams
import com.launcher.api.wizard.StepResult
import com.launcher.app.ui.pairing.PairingActivity
import com.launcher.ui.wizard.steps.CustomStepHandler

/**
 * Wizard "pair-admin" Custom step (TASK-7 Phase 5 / FR-027 / FR-028).
 * Launches the existing spec 007 [PairingActivity] and returns
 * [StepResult.AnswerCaptured("launched")] so the wizard advances.
 *
 * Pairing itself completes asynchronously inside PairingActivity (its
 * UI flow runs independently; user can cancel or finish at their own
 * pace). The wizard never *waits* for the pairing handshake — it would
 * dead-end the senior-safe flow if the admin device weren't ready.
 *
 * TODO(TASK-8): when admin config push lands, optionally surface a
 * confirmation in Settings if pairing completed; for now the wizard
 * treats the launch as success regardless of outcome (graceful).
 *
 * TODO(activity-result): if Сценарий 4 ever requires the wizard to
 * *await* the paired outcome before continuing, switch this to an
 * ActivityResultRegistry-backed flow. That requires PairingActivity
 * to call setResult(); spec 007 currently uses plain finish().
 */
class PairAdminCustomStepHandler(
    private val context: Context,
) : CustomStepHandler {
    override suspend fun execute(params: StepParams): StepResult {
        return try {
            val intent = Intent(context, PairingActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            // Skipped (not AnswerCaptured) because the actual pairing
            // completes asynchronously inside PairingActivity. The
            // wizard's job is just to surface the option; recording
            // `paired` here would be a lie if the user backs out.
            StepResult.Skipped
        } catch (e: Exception) {
            // Don't kill the wizard if pairing intent dispatch fails
            // (uninstalled admin app, locked context, etc.).
            StepResult.Skipped
        }
    }
}
