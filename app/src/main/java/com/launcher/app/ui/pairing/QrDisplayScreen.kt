package com.launcher.app.ui.pairing

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.launcher.api.pairing.PairingToken
import com.launcher.api.qr.QrDeepLinkParser
import com.launcher.app.R
import kotlinx.coroutines.delay

/**
 * QR display screen (T086, FR-004). Shown after the Settings toggle is
 * enabled and the [com.launcher.api.pairing.PairingService] has produced
 * a token.
 *
 * Senior-safe (Article VIII §7): font ≥ 18sp on body text, tap targets
 * ≥ 56dp on the Cancel button, high-contrast black-on-white QR.
 */
@Composable
fun QrDisplayScreen(
    token: PairingToken,
    expiresAtMillis: Long,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val qrBitmap = remember(token) {
        // The URI grammar (scheme, host, version field) lives in one place — the parser that
        // also decodes it — so encode and decode can never drift apart.
        val deepLink = QrDeepLinkParser.buildPairingDeepLink(token)
        QrBitmapGenerator.generate(deepLink).asImageBitmap()
    }

    // Re-renders every second to drive the countdown without churning the
    // QR bitmap (which is keyed on `token`).
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(token) {
        while (true) {
            delay(1_000)
            nowMillis = System.currentTimeMillis()
            if (nowMillis >= expiresAtMillis) break
        }
    }

    val remainingMs = (expiresAtMillis - nowMillis).coerceAtLeast(0L)
    val totalMs = 5 * 60 * 1_000L // matches PairingService.TOKEN_TTL_MS
    val remainingMinutes = (remainingMs / 60_000L).toInt()
    val remainingSeconds = ((remainingMs % 60_000L) / 1_000L).toInt()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = stringResource(R.string.pairing_qr_title),
            style = MaterialTheme.typography.titleLarge,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )

        Text(
            text = stringResource(R.string.pairing_qr_instructions),
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 360.dp),
        )

        Box(
            modifier = Modifier
                .size(280.dp)
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = qrBitmap,
                contentDescription = context.getString(R.string.pairing_qr_image_description),
                modifier = Modifier.size(264.dp),
            )
        }

        Text(
            text = stringResource(
                R.string.pairing_qr_token_label,
                token.raw,
            ),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(
                    R.string.pairing_qr_countdown,
                    remainingMinutes,
                    remainingSeconds,
                ),
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
            )
            LinearProgressIndicator(
                progress = { (remainingMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onCancel,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 360.dp)
                .height(56.dp), // senior-safe tap target per Article VIII §7
            contentPadding = PaddingValues(16.dp),
        ) {
            Text(
                text = stringResource(R.string.pairing_qr_cancel_button),
                fontSize = 18.sp,
            )
        }
    }
}
