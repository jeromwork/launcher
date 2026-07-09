package com.launcher.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.launcher.api.auth.AuthProvider
import com.launcher.ui.auth.SignInTrigger
import launcher.core.generated.resources.Res
import launcher.core.generated.resources.auth_choice_continue
import launcher.core.generated.resources.auth_choice_setup_fresh_description
import launcher.core.generated.resources.auth_choice_setup_fresh_title
import launcher.core.generated.resources.auth_choice_sign_in_description
import launcher.core.generated.resources.auth_choice_sign_in_title
import launcher.core.generated.resources.auth_choice_skip
import launcher.core.generated.resources.auth_choice_title
import org.jetbrains.compose.resources.stringResource

/**
 * Spec 017 (F-4 AuthProvider) wizard step реализует US 2:
 * «Пользователь в wizard выбирает Войти в Google для восстановления настроек».
 *
 * Два режима:
 *  1. **Choice mode** (по умолчанию) — показывает «Настроить с нуля» и
 *     «Войти в Google для восстановления настроек». Tap первого → onSkip.
 *     Tap второго → переключение в Sign-In mode.
 *  2. **Sign-In mode** — показывает [SignInTrigger] composable; после успеха
 *     срабатывает onSignedIn → wizard advances. Кнопка «Пропустить вход»
 *     возвращает обратно к Choice mode (или сразу onSkip).
 *
 * Senior-safe baseline (Article VIII §7):
 *  - Кнопки ≥56dp.
 *  - Текст ≥18sp (titleMedium).
 *  - Один primary, один outlined per Material guideline.
 *
 * Per spec 017 US 2, FR-033.
 */
@Composable
fun AuthChoiceStep(
    authProvider: AuthProvider,
    onSkip: () -> Unit,
    onSignedIn: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    topContent: @Composable () -> Unit = {},
) {
    // Если AuthProvider уже эмитит non-null currentUser (например, после
    // device-restart с persisted session) — пропускаем step сразу.
    val currentUser by authProvider.currentUser.collectAsState(initial = null)
    LaunchedEffect(currentUser) {
        if (currentUser != null) onSignedIn()
    }

    var mode by remember { mutableStateOf(Mode.CHOICE) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(PaddingValues(horizontal = 24.dp, vertical = 32.dp)),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // In SIGN_IN mode Back returns to CHOICE mode locally; in CHOICE
        // mode Back invokes the wizard-level onBack (returns to preset picker).
        // Hidden entirely if neither is applicable.
        val backAction: (() -> Unit)? = when {
            mode == Mode.SIGN_IN -> { { mode = Mode.CHOICE } }
            onBack != null -> onBack
            else -> null
        }
        if (backAction != null) {
            OutlinedButton(
                onClick = backAction,
                modifier = Modifier.defaultMinSize(minHeight = 56.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                )
                Text(
                    text = "Назад",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }

        topContent()
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(Res.string.auth_choice_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))

        when (mode) {
            Mode.CHOICE -> ChoiceButtons(
                onSetupFresh = onSkip,
                onWantSignIn = { mode = Mode.SIGN_IN },
            )
            Mode.SIGN_IN -> {
                SignInTrigger(
                    authProvider = authProvider,
                    onSignedIn = { onSignedIn() },
                )
                OutlinedButton(
                    onClick = onSkip,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 56.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.auth_choice_skip),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

private enum class Mode { CHOICE, SIGN_IN }

@Composable
private fun ChoiceButtons(
    onSetupFresh: () -> Unit,
    onWantSignIn: () -> Unit,
) {
    // Primary: Sign-In (потому что user, у которого старый конфиг —
    // основная целевая аудитория этого экрана; «начать с нуля» — выбор по
    // умолчанию для тех, у кого никогда не было).
    Button(
        onClick = onWantSignIn,
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(Res.string.auth_choice_sign_in_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(Res.string.auth_choice_sign_in_description),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    OutlinedButton(
        onClick = onSetupFresh,
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(Res.string.auth_choice_setup_fresh_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(Res.string.auth_choice_setup_fresh_description),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
