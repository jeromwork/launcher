package com.launcher.app.ui.pairing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.launcher.app.R

/**
 * Settings entry — toggle "Разрешить удалённое управление" (T084, FR-003).
 *
 * Senior-safe per Article VIII §7: tap target ≥ 56dp, body text ≥ 18sp.
 * The host Activity routes to QR display on [onToggle] `true`.
 */
@Composable
fun PairingToggleSection(
    isOn: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.pairing_toggle_title),
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.pairing_toggle_description),
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        if (isOn) {
            OutlinedButton(
                onClick = { onToggle(false) },
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 360.dp)
                    .height(56.dp),
                contentPadding = PaddingValues(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.pairing_toggle_turn_off),
                    fontSize = 18.sp,
                )
            }
        } else {
            Button(
                onClick = { onToggle(true) },
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 360.dp)
                    .height(56.dp),
                contentPadding = PaddingValues(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.pairing_toggle_turn_on),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
