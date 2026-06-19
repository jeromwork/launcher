package com.launcher.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.launcher.api.auth.AuthError
import com.launcher.api.auth.AuthIdentity
import com.launcher.api.auth.AuthProvider
import com.launcher.api.result.Outcome
import kotlinx.coroutines.launch
import launcher.core.generated.resources.Res
import launcher.core.generated.resources.auth_a11y_sign_in_button
import launcher.core.generated.resources.auth_a11y_sign_out_button
import launcher.core.generated.resources.auth_a11y_signed_in_status
import launcher.core.generated.resources.auth_error_network
import launcher.core.generated.resources.auth_error_no_email
import launcher.core.generated.resources.auth_error_provider_unavailable
import launcher.core.generated.resources.auth_error_unknown
import launcher.core.generated.resources.auth_loading_signing_in
import launcher.core.generated.resources.auth_loading_signing_out
import launcher.core.generated.resources.auth_sign_in_button
import launcher.core.generated.resources.auth_sign_in_explanation
import launcher.core.generated.resources.auth_sign_out_button
import launcher.core.generated.resources.auth_signed_in_status
import org.jetbrains.compose.resources.stringResource

/**
 * Reusable composable для входа / выхода через [AuthProvider].
 *
 * 5 explicit состояний (см. [SignInTriggerState]):
 *  - NotSignedIn: «Войти в аккаунт» + объяснение.
 *  - SigningIn: кнопка disabled + spinner.
 *  - SignedIn: «Вошли как email» + «Выйти».
 *  - SigningOut: SignedIn UI + disabled + spinner.
 *  - Error: NotSignedIn UI + inline error message (live region для TalkBack).
 *
 * Senior-safe baseline (Article VIII §7 + spec 017 FR-033, FR-036):
 *  - Кнопки ≥56dp height.
 *  - Текст ≥18sp (Material body large).
 *  - TalkBack: расширенные content descriptions (FR-036).
 *  - Error: live region Polite → screen reader озвучивает изменение.
 *
 * State persistence (US 2 acceptance #1, state-management CHK):
 *  - `isSigningIn` через `rememberSaveable` — переживает Activity rotation.
 *  - `lastError` через `remember` (не Saveable — после rotation user видит
 *    NotSignedIn без stale error; повторное нажатие приведёт к свежей попытке).
 *
 * NB: caller scope (`rememberCoroutineScope`) запускает `signIn()`, но сам
 * [AuthProvider] держит свой adapter-scope для in-flight операций
 * (research.md §R2). Если coroutine caller'а отменится (например при
 * navigation), adapter всё равно завершит signIn и эмитнет `currentUser`.
 *
 * Per spec 017 FR-033, FR-036, research.md §R7.
 */
@Composable
fun SignInTrigger(
    authProvider: AuthProvider,
    modifier: Modifier = Modifier,
    onSignedIn: (AuthIdentity) -> Unit = {},
    onSignedOut: () -> Unit = {},
) {
    val currentUser by authProvider.currentUser.collectAsState(initial = null)
    var isSigningIn by rememberSaveable { mutableStateOf(false) }
    var isSigningOut by remember { mutableStateOf(false) }
    var lastError by remember { mutableStateOf<AuthError?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val state = deriveSignInTriggerState(
        currentUser = currentUser,
        isSigningIn = isSigningIn,
        isSigningOut = isSigningOut,
        lastError = lastError,
    )

    // Side-effects на state change.
    LaunchedEffect(currentUser) {
        if (currentUser != null) {
            isSigningIn = false  // success → выходим из loading.
            lastError = null
            onSignedIn(currentUser!!)
        } else if (state is SignInTriggerState.SigningOut) {
            // ничего — onSignedOut вызовется в effect ниже.
        }
    }

    val startSignIn: () -> Unit = {
        if (!isSigningIn && !isSigningOut) {
            isSigningIn = true
            lastError = null
            coroutineScope.launch {
                val result = authProvider.signIn()
                isSigningIn = false
                when (result) {
                    is Outcome.Success -> { /* currentUser update propagates via Flow */ }
                    is Outcome.Failure -> { lastError = result.error }
                }
            }
        }
    }

    val startSignOut: () -> Unit = {
        if (!isSigningIn && !isSigningOut) {
            isSigningOut = true
            coroutineScope.launch {
                authProvider.signOut()
                isSigningOut = false
                onSignedOut()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (state) {
            is SignInTriggerState.NotSignedIn,
            is SignInTriggerState.Error,
            is SignInTriggerState.SigningIn -> {
                SignInButton(
                    enabled = state !is SignInTriggerState.SigningIn,
                    showProgress = state is SignInTriggerState.SigningIn,
                    onClick = startSignIn,
                )
                Text(
                    text = stringResource(Res.string.auth_sign_in_explanation),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                if (state is SignInTriggerState.Error) {
                    InlineError(state.reason)
                }
            }
            is SignInTriggerState.SignedIn -> {
                SignedInRow(
                    identity = state.identity,
                    isSigningOut = false,
                    onSignOut = startSignOut,
                )
            }
            is SignInTriggerState.SigningOut -> {
                val captured = currentUser
                if (captured != null) {
                    SignedInRow(
                        identity = captured,
                        isSigningOut = true,
                        onSignOut = startSignOut,
                    )
                }
            }
        }
    }
}

@Composable
private fun SignInButton(
    enabled: Boolean,
    showProgress: Boolean,
    onClick: () -> Unit,
) {
    val a11yLabel = stringResource(Res.string.auth_a11y_sign_in_button)
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 56.dp)
            .semantics { contentDescription = a11yLabel },
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        if (showProgress) {
            CircularProgressIndicator(
                modifier = Modifier.height(20.dp),
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.height(0.dp))
            Text(
                text = stringResource(Res.string.auth_loading_signing_in),
                fontSize = 18.sp,
            )
        } else {
            Text(
                text = stringResource(Res.string.auth_sign_in_button),
                fontSize = 18.sp,
            )
        }
    }
}

@Composable
private fun SignedInRow(
    identity: AuthIdentity,
    isSigningOut: Boolean,
    onSignOut: () -> Unit,
) {
    val display = identity.email ?: identity.displayName ?: identity.stableId
    val a11yStatus = stringResource(Res.string.auth_a11y_signed_in_status, display)
    val a11yLogout = stringResource(Res.string.auth_a11y_sign_out_button, display)
    Text(
        text = stringResource(Res.string.auth_signed_in_status, display),
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.semantics { contentDescription = a11yStatus },
    )
    Button(
        onClick = onSignOut,
        enabled = !isSigningOut,
        colors = ButtonDefaults.outlinedButtonColors(),
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 56.dp)
            .semantics { contentDescription = a11yLogout },
    ) {
        if (isSigningOut) {
            CircularProgressIndicator(
                modifier = Modifier.height(20.dp),
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.height(0.dp))
            Text(
                text = stringResource(Res.string.auth_loading_signing_out),
                fontSize = 18.sp,
            )
        } else {
            Text(
                text = stringResource(Res.string.auth_sign_out_button),
                fontSize = 18.sp,
            )
        }
    }
}

@Composable
private fun InlineError(error: AuthError) {
    val message = when (error) {
        is AuthError.NetworkError -> stringResource(Res.string.auth_error_network)
        is AuthError.NoEmail -> stringResource(Res.string.auth_error_no_email)
        is AuthError.ProviderUnavailable -> stringResource(Res.string.auth_error_provider_unavailable)
        is AuthError.Cancelled,
        is AuthError.Unknown -> stringResource(Res.string.auth_error_unknown)
    }
    Text(
        text = message,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.semantics {
            liveRegion = LiveRegionMode.Polite
        },
    )
}
