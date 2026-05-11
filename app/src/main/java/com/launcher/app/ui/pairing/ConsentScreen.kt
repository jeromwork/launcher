package com.launcher.app.ui.pairing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.launcher.app.R

/**
 * Consent screen (T087, FR-007 + US-3 acceptance 1). Shown when the admin
 * has claimed the pairing token and the FSM is in
 * [com.launcher.api.pairing.PairingState.AwaitingConsent].
 *
 * **Fixed category list** (FR-007 + US-3.1): the categories shown here are
 * resource-driven (not free text), so the admin cannot subvert consent by
 * crafting a custom payload. Adding a new category requires a spec change
 * + a translation update.
 *
 * Senior-safe (Article VIII §7): font ≥ 18sp, tap targets ≥ 56dp.
 */
@Composable
fun ConsentScreen(
    adminId: String,
    onAllow: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.pairing_consent_title),
            style = MaterialTheme.typography.titleLarge,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )

        Text(
            text = stringResource(R.string.pairing_consent_admin_label, adminId),
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
        )

        Text(
            text = stringResource(R.string.pairing_consent_categories_intro),
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )

        // Fixed categories — order matters (UI test asserts on it). Resources,
        // not free text, so admin cannot inject untrusted strings.
        CategoryRow(stringResource(R.string.pairing_consent_category_config))
        CategoryRow(stringResource(R.string.pairing_consent_category_state))
        CategoryRow(stringResource(R.string.pairing_consent_category_capabilities))
        CategoryRow(stringResource(R.string.pairing_consent_category_health))
        CategoryRow(stringResource(R.string.pairing_consent_category_commands))

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.pairing_consent_revoke_note),
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onDecline,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                contentPadding = PaddingValues(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.pairing_consent_decline_button),
                    fontSize = 18.sp,
                )
            }
            Button(
                onClick = onAllow,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                contentPadding = PaddingValues(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.pairing_consent_allow_button),
                    fontSize = 18.sp,
                )
            }
        }
    }
}

@Composable
private fun CategoryRow(label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 480.dp)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "• ",
            fontSize = 18.sp,
        )
        Text(
            text = label,
            fontSize = 18.sp,
            modifier = Modifier.weight(1f),
        )
    }
}
