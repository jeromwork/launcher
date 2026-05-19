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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.launcher.api.gate.Challenge

/**
 * Spec 010 T097 — numeric-entry challenge (FR-023a, FR-026, A-13).
 *
 * Display rules:
 *  - Challenge answer shown в **small font (≤ 14 sp)** per FR-026a — намеренно
 *    мелко, чтобы elderly с macular degeneration не различили.
 *  - Keypad buttons baseline 56 dp (FR-026c) — admin can hit them quickly.
 *  - **No** «show answer» helper, no «I forgot» link — anti-discoverability.
 *
 * Compose state:
 *  - Typed prefix is `rememberSaveable` — survives rotation (C-1).
 *  - On every digit append, check prefix == challenge.answer → fire [onSuccess].
 *  - Backspace removes the last digit; reset clears the prefix.
 *
 * A11y (FR-027):
 *  - The challenge answer Text has `contentDescription = challenge.answer`
 *    so TalkBack reads it (accepted edge per US-7 Acceptance #7 — the gate
 *    leaks under accessibility services; real accessibility-aware admin
 *    entry is a future-spec).
 */
@Composable
fun NumericEntryChallenge(
    challenge: Challenge.NumericEntry,
    onSuccess: () -> Unit,
    onWrongAnswer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var typed by rememberSaveable { mutableStateOf("") }
    val keypadOrder = remember { listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "←", "0", "OK") }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("numeric_entry_challenge"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = challenge.answer,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .testTag("challenge_text")
                .semantics { contentDescription = challenge.answer },
        )
        Text(
            text = typed,
            fontSize = 28.sp,
            modifier = Modifier
                .testTag("challenge_typed")
                .semantics { contentDescription = "введено: $typed" },
        )

        for (row in keypadOrder.chunked(3)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                for (label in row) {
                    Button(
                        onClick = {
                            when (label) {
                                "←" -> if (typed.isNotEmpty()) typed = typed.dropLast(1)
                                "OK" -> if (typed == challenge.answer) {
                                    onSuccess()
                                } else {
                                    onWrongAnswer()
                                }
                                else -> typed += label
                            }
                        },
                        modifier = Modifier
                            .size(56.dp)
                            .testTag("keypad_$label"),
                    ) {
                        Text(text = label)
                    }
                }
            }
        }
    }
}
