package com.launcher.ui.gate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.launcher.api.gate.Challenge
import com.launcher.api.gate.generateRandomChallenge
import kotlin.random.Random

/**
 * Spec 010 T096 — host Composable для 7-tap admin gate (FR-022).
 *
 *  - Holds the current [Challenge] in `rememberSaveable` per C-1 (rotation
 *    survives via [ChallengeSaver]).
 *  - На правильный ответ → [onSuccess] (host навигирует в admin-mode).
 *  - На неправильный → regenerate fresh challenge через [generateRandomChallenge]
 *    (FR-024). No lockout counter (FR-024).
 *  - CANCEL button — large senior-safe (≥ 56 dp), визуально доминирует над
 *    challenge UI (FR-026b). Host wires [onCancel] to return-to-home navigation.
 *
 * Pluggable variants:
 *  - NumericEntry — calls [NumericEntryChallenge].
 *  - SequenceTap — calls [SequenceTapChallenge].
 *
 * [randomSeed] is exposed for deterministic tests (StateRestorationTester
 * paths in [ChallengeGateScreenTest]); production callers leave it null →
 * `Random.Default`.
 */
@Composable
fun ChallengeGateScreen(
    cancelLabel: String,
    sequenceInstructionTemplate: (String) -> String,
    onSuccess: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    randomSeed: Long? = null,
) {
    val random = remember(randomSeed) {
        if (randomSeed == null) Random.Default else Random(randomSeed)
    }
    val challenge: MutableState<Challenge> = rememberSaveable(
        stateSaver = ChallengeSaver,
    ) { mutableStateOf(generateRandomChallenge(random)) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(PaddingValues(horizontal = 16.dp, vertical = 24.dp))
            .testTag("challenge_gate_screen"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // FR-026b: CANCEL is large + visually dominant — placed первым by
        // hand-reach pattern.
        Button(
            onClick = onCancel,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp)
                .testTag("challenge_cancel"),
        ) {
            Text(text = cancelLabel, style = MaterialTheme.typography.titleMedium)
        }

        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            when (val c = challenge.value) {
                is Challenge.NumericEntry -> NumericEntryChallenge(
                    challenge = c,
                    onSuccess = onSuccess,
                    onWrongAnswer = { challenge.value = generateRandomChallenge(random) },
                )
                is Challenge.SequenceTap -> SequenceTapChallenge(
                    challenge = c,
                    instructionTemplate = sequenceInstructionTemplate,
                    onSuccess = onSuccess,
                    onWrongAnswer = { challenge.value = generateRandomChallenge(random) },
                )
            }
        }

        Spacer(modifier = Modifier.size(8.dp))
    }
}
