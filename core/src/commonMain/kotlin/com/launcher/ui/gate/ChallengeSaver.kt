package com.launcher.ui.gate

import androidx.compose.runtime.saveable.Saver
import com.launcher.api.gate.Challenge

/**
 * Spec 010 C-1 / T096 — Compose [Saver] для [Challenge] чтобы pluggable
 * variant пережил configuration change (rotation) внутри
 * [androidx.compose.runtime.saveable.rememberSaveable].
 *
 * Без этого rememberSaveable не может сериализовать sealed class — Bundle
 * не понимает Kotlin sealed hierarchy.
 *
 * Encoding (private — non-wire-format, not persisted across reinstalls):
 *  - `["N", "1234"]` → NumericEntry("1234")
 *  - `["S", "1,2,3,4,5,6", "3,1,5"]` → SequenceTap(buttonIds, expectedOrder)
 *
 * On decode failure (corrupted/foreign Bundle), [Saver.restore] returns null —
 * the Composable then re-rolls a fresh challenge via
 * [com.launcher.api.gate.generateRandomChallenge].
 */
val ChallengeSaver: Saver<Challenge, List<String>> = Saver(
    save = { challenge ->
        when (challenge) {
            is Challenge.NumericEntry -> listOf("N", challenge.answer)
            is Challenge.SequenceTap -> listOf(
                "S",
                challenge.buttonIds.joinToString(","),
                challenge.expectedOrder.joinToString(","),
            )
        }
    },
    restore = { encoded ->
        runCatching {
            when (encoded.firstOrNull()) {
                "N" -> Challenge.NumericEntry(answer = encoded[1])
                "S" -> Challenge.SequenceTap(
                    buttonIds = encoded[1].split(',').map { it.toInt() },
                    expectedOrder = encoded[2].split(',').map { it.toInt() },
                )
                else -> null
            }
        }.getOrNull()
    },
)
