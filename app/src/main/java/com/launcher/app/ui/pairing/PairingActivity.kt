package com.launcher.app.ui.pairing

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.launcher.api.pairing.PairingState
import com.launcher.app.R
import org.koin.android.ext.android.inject

/**
 * Standalone host for the Managed-side pairing flow (T084, T086–T088).
 * Routes between idle / QR / consent / paired / expired / error screens
 * based on [PairingViewModel.state]. Launched from Settings via an
 * explicit intent; Activity stays thin and Android-specific.
 */
class PairingActivity : ComponentActivity() {

    private val viewModel: PairingViewModel by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    PairingRouter(
                        viewModel = viewModel,
                        onFlowEnded = { finish() },
                    )
                }
            }
        }
    }
}

@Composable
fun PairingRouter(
    viewModel: PairingViewModel,
    onFlowEnded: () -> Unit,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(viewModel) {
        // Reserved for future Snackbar wiring per project-backlog TODO-UX-001.
        viewModel.events.collect { /* no-op for MVP */ }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val s = state) {
            PairingState.Idle -> IdleEntry(
                onStart = { viewModel.startPairing() },
                onClose = onFlowEnded,
            )
            is PairingState.WaitingForClaim -> QrDisplayScreen(
                token = s.token,
                expiresAtMillis = s.expiresAt,
                onCancel = {
                    viewModel.cancel()
                    onFlowEnded()
                },
            )
            is PairingState.AwaitingConsent -> ConsentScreen(
                adminId = s.adminId,
                onAllow = { viewModel.confirmConsent() },
                onDecline = {
                    viewModel.decline()
                    onFlowEnded()
                },
            )
            is PairingState.Claimed -> PairedStatusSection(
                link = s.link,
                onUnbind = {
                    // TODO(follow-up): wire LinkRegistry.revoke() through the
                    // ViewModel. Held back so Phase 8 ships the Managed-side
                    // pairing surface end-to-end first.
                    onFlowEnded()
                },
                modifier = Modifier.padding(24.dp),
            )
            PairingState.Expired -> ExpiredScreen(
                onRetry = { viewModel.startPairing() },
                onClose = onFlowEnded,
            )
            PairingState.Revoked -> {
                LaunchedEffect(Unit) { onFlowEnded() }
            }
            is PairingState.Error -> ErrorScreen(
                message = "Ошибка: ${s.cause}",
                onRetry = { viewModel.startPairing() },
                onClose = onFlowEnded,
            )
        }
    }
}

@Composable
private fun IdleEntry(onStart: () -> Unit, onClose: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PairingToggleSection(
            isOn = false,
            onToggle = { isOn -> if (isOn) onStart() else onClose() },
        )
    }
}

@Composable
private fun ExpiredScreen(onRetry: () -> Unit, onClose: () -> Unit) {
    SimpleNoticeScreen(
        title = stringResource(R.string.pairing_expired_title),
        primaryLabel = stringResource(R.string.pairing_expired_retry),
        secondaryLabel = stringResource(R.string.pairing_close),
        onPrimary = onRetry,
        onSecondary = onClose,
    )
}

@Composable
private fun ErrorScreen(message: String, onRetry: () -> Unit, onClose: () -> Unit) {
    SimpleNoticeScreen(
        title = message,
        primaryLabel = stringResource(R.string.pairing_error_retry),
        secondaryLabel = stringResource(R.string.pairing_close),
        onPrimary = onRetry,
        onSecondary = onClose,
    )
}

@Composable
private fun SimpleNoticeScreen(
    title: String,
    primaryLabel: String,
    secondaryLabel: String,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Button(
            onClick = onPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 360.dp)
                .height(56.dp),
            contentPadding = PaddingValues(16.dp),
        ) {
            Text(text = primaryLabel, fontSize = 18.sp)
        }
        OutlinedButton(
            onClick = onSecondary,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 360.dp)
                .height(56.dp),
            contentPadding = PaddingValues(16.dp),
        ) {
            Text(text = secondaryLabel, fontSize = 18.sp)
        }
    }
}
