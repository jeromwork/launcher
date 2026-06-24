package com.launcher.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Spec 010 T041 — senior-safe «Шаг N из M» wizard progress indicator
 * (FR-008a, CHK-elderly-007).
 *
 * Senior-safe constraints:
 *  - text «Шаг N из M» — larger than body (titleMedium ≈ 16sp on Material 3)
 *    so 60+ users actually read it.
 *  - dot row underneath shows total steps + current progress in colour.
 *
 * Step count [totalSteps] varies:
 *  - 3 on Android < 13 (POST_NOTIFICATIONS step skipped — see [PostNotificationsStep]).
 *  - 4 on Android 13+ (POST_NOTIFICATIONS step appears).
 *
 * [currentStep] is 1-based.
 *
 * @param progressLabelTemplate template like «Шаг %1$d из %2$d» — supplied by
 *   the host Activity via `stringResource`; Composable stays platform-agnostic.
 */
@Composable
fun WizardProgressIndicator(
    currentStep: Int,
    totalSteps: Int,
    progressLabelTemplate: String,
    modifier: Modifier = Modifier,
) {
    require(currentStep in 1..totalSteps) {
        "currentStep must be in 1..totalSteps; got $currentStep / $totalSteps"
    }
    androidx.compose.foundation.layout.Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = progressLabelTemplate
                .replace("%1\$d", currentStep.toString())
                .replace("%2\$d", totalSteps.toString()),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            for (i in 1..totalSteps) {
                Dot(filled = i <= currentStep)
                if (i != totalSteps) {
                    Spacer(modifier = Modifier.size(8.dp))
                }
            }
        }
    }
}

@Composable
private fun Dot(filled: Boolean) {
    Surface(
        modifier = Modifier.size(12.dp),
        shape = CircleShape,
        color = if (filled) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surfaceVariant,
    ) {}
}
