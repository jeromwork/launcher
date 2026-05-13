package com.launcher.app.ui.pairing

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.launcher.api.pairing.PairingToken
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
import com.launcher.ui.theme.LauncherTheme
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
        handleIncomingDeepLink(intent)
        setContent {
            LauncherTheme(preset = null) {
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingDeepLink(intent)
    }

    private var adminClaimFlow: Boolean = false

    private fun handleIncomingDeepLink(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val raw = intent.data?.getQueryParameter("token") ?: return
        // Strict regex per spec 007 FR-003 token alphabet (Crockford base32 minus
        // ambiguous I/L/O/0/1). Invalid tokens stay on the Idle screen.
        if (!raw.matches(Regex("^[A-HJ-NP-Z2-9]{6}$"))) return
        adminClaimFlow = true
        viewModel.claimAsAdmin(PairingToken(raw))
        // Watch for the claim to land and close ourselves so the user returns
        // to the "Управление телефонами" list where the new device is now
        // visible. Managed side does NOT auto-finish — it needs the consent step.
        lifecycleScope.launchWhenStarted {
            viewModel.state.collect { st ->
                if (adminClaimFlow && st is PairingState.Claimed) {
                    finish()
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
    val isProcessing by viewModel.isProcessing.collectAsState()

    LaunchedEffect(viewModel) {
        // Reserved for future Snackbar wiring per project-backlog TODO-UX-001.
        viewModel.events.collect { /* no-op for MVP */ }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isProcessing) {
            ProcessingScreen()
            return@Box
        }
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
                    viewModel.unbind()
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
private fun ProcessingScreen() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        androidx.compose.material3.CircularProgressIndicator()
        Text(
            text = "Привязываем устройства…",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
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
