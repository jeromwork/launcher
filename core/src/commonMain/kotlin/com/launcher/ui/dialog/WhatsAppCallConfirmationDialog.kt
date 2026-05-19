package com.launcher.ui.dialog

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Spec 010 T056 / FR-014 — WhatsApp variant of [CallConfirmationDialog].
 *
 * Identical senior-safe layout (same ≥56 dp button shapes, same TalkBack
 * CANCEL-first focus order); difference is purely the action wired to
 * the CALL button — caller passes [onCall] that fires the wa.me deep-link
 * (`https://wa.me/<phone>`) instead of `Intent.ACTION_CALL`.
 *
 * No structural divergence yet, so this stays as a thin re-export. If
 * future copy requires WhatsApp branding (icon, accent colour), this
 * Composable is the seam to specialise without touching the canonical
 * phone-call dialog.
 */
@Composable
fun WhatsAppCallConfirmationDialog(
    displayName: String,
    formattedNumber: String,
    photoUrl: String?,
    cancelLabel: String,
    callLabel: String,
    invalidNumberMessage: String,
    onCancel: () -> Unit,
    onWhatsAppCall: () -> Unit,
    numberIsValid: Boolean = true,
    modifier: Modifier = Modifier,
) {
    CallConfirmationDialog(
        displayName = displayName,
        formattedNumber = formattedNumber,
        photoUrl = photoUrl,
        cancelLabel = cancelLabel,
        callLabel = callLabel,
        invalidNumberMessage = invalidNumberMessage,
        onCancel = onCancel,
        onCall = onWhatsAppCall,
        numberIsValid = numberIsValid,
        modifier = modifier,
    )
}
