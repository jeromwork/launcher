package com.launcher.ui.gate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.launcher.api.gate.Challenge

/**
 * Spec 010 T098 — sequence-tap challenge (FR-023b, FR-024).
 *
 * UX:
 *  - 6 numbered buttons в порядке `challenge.buttonIds` — random visual layout.
 *  - Instruction text «нажмите кнопки X, Y, Z по порядку» (host substitutes
 *    expectedOrder через `instructionTemplate` + comma-separated values).
 *  - Каждый tap проверяется vs. `expectedOrder[progress]`:
 *    - **Match** → progress += 1; if completed → [onSuccess].
 *    - **Mismatch** → calls [onWrongAnswer] which regenerates the challenge
 *      upstream (FR-024). Local progress resets.
 *
 * A11y: instruction text is content-described так что TalkBack читает
 * последовательность (accepted edge per US-7 #7).
 */
@Composable
fun SequenceTapChallenge(
    challenge: Challenge.SequenceTap,
    instructionTemplate: (sequence: String) -> String,
    onSuccess: () -> Unit,
    onWrongAnswer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var progress by rememberSaveable(challenge) { mutableStateOf(0) }
    val instruction = instructionTemplate(challenge.expectedOrder.joinToString(", "))

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("sequence_tap_challenge"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = instruction,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .testTag("sequence_instruction")
                .semantics { contentDescription = instruction },
        )

        for (row in challenge.buttonIds.chunked(3)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                for (id in row) {
                    Button(
                        onClick = {
                            val expected = challenge.expectedOrder.getOrNull(progress)
                            if (expected == id) {
                                val next = progress + 1
                                if (next >= challenge.expectedOrder.size) {
                                    progress = 0
                                    onSuccess()
                                } else {
                                    progress = next
                                }
                            } else {
                                progress = 0
                                onWrongAnswer()
                            }
                        },
                        modifier = Modifier
                            .size(56.dp)
                            .testTag("seq_button_$id"),
                    ) {
                        Text(text = id.toString())
                    }
                }
            }
        }
    }
}
