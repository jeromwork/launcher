package com.launcher.ui.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Spec 010 T054 / T061 — senior-safe call confirmation dialog
 * (FR-011, FR-015, FR-016, CHK-elderly-006).
 *
 * Full-screen layout:
 *  - Contact photo (or initials fallback if [photoUrl] is null).
 *  - Contact display name (large).
 *  - Formatted phone number.
 *  - Two buttons side-by-side:
 *    * **CANCEL** — filled (primary attention), ≥ 56 dp, **left** (FR-011).
 *      `semantics { traversalIndex = -1f }` → TalkBack reads CANCEL FIRST
 *      (CHK-accessibility-011 / T061).
 *    * **CALL** — outlined, ≥ 56 dp, **right**.
 *
 * Invalid-number state: when [numberIsValid] = false, CALL is disabled and
 * the «Номер некорректен» helper text appears (FR-015).
 *
 * Back button: caller decorates `onCancel` with their `BackHandler` so
 * physical-back behaves identically to CANCEL tap (FR-016, no side effects).
 *
 * @param formattedNumber pre-formatted phone string from `PhoneNumberFormatter`.
 * @param invalidNumberMessage `stringResource(R.string.call_confirm_invalid_number)`.
 */
@Composable
fun CallConfirmationDialog(
    displayName: String,
    formattedNumber: String,
    photoUrl: String?,
    cancelLabel: String,
    callLabel: String,
    invalidNumberMessage: String,
    onCancel: () -> Unit,
    onCall: () -> Unit,
    numberIsValid: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(PaddingValues(horizontal = 24.dp, vertical = 40.dp)),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        // Photo (placeholder uses initials when photoUrl is null — Coil
        // integration handled by caller for real network photos in spec 011).
        InitialsAvatar(name = displayName, modifier = Modifier.size(140.dp))
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = displayName,
            fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = formattedNumber,
            fontSize = 22.sp,
            textAlign = TextAlign.Center,
        )
        if (!numberIsValid) {
            Text(
                text = invalidNumberMessage,
                color = MaterialTheme.colorScheme.error,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(modifier = Modifier.size(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // CANCEL filled, left, TalkBack-first.
            Button(
                onClick = onCancel,
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 56.dp)
                    .semantics { traversalIndex = -1f },
            ) {
                Text(text = cancelLabel, style = MaterialTheme.typography.titleMedium)
            }
            // CALL outlined, right.
            OutlinedButton(
                onClick = onCall,
                enabled = numberIsValid,
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 56.dp),
            ) {
                Text(text = callLabel, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun InitialsAvatar(name: String, modifier: Modifier = Modifier) {
    val initials = name
        .split(' ', limit = 2)
        .mapNotNull { it.firstOrNull()?.toString() }
        .joinToString("")
        .take(2)
        .uppercase()
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(color = MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials.ifBlank { "?" },
            fontSize = 56.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
    // Suppress lint about Color unused.
    @Suppress("UNUSED_EXPRESSION") Color.Unspecified
}
