package com.launcher.app.ui.onboarding

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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.launcher.app.R

/**
 * TASK-49 FR-005, FR-006, FR-008, FR-008a — reusable explanation screen
 * shown before Sign-In flow. Re-used by wizard (TASK-7) and Settings
 * cloud-actions; presented once per cloud-action trigger.
 *
 * Senior-safe (Article VIII §7): title 24sp, body 18sp, buttons 56dp.
 * No call into [com.launcher.cloud.api.CloudAvailability] from this screen —
 * callers decide when to show it.
 */
@Composable
fun SignInExplanationScreen(
    onSignInClicked: () -> Unit,
    onCancelClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(R.string.task49_signin_explanation_title)
    val bullets = listOf(
        stringResource(R.string.task49_signin_explanation_bullet_1),
        stringResource(R.string.task49_signin_explanation_bullet_2),
        stringResource(R.string.task49_signin_explanation_bullet_3),
        stringResource(R.string.task49_signin_explanation_bullet_4),
    )
    val signInLabel = stringResource(R.string.task49_signin_explanation_button_signin)
    val cancelLabel = stringResource(R.string.task49_signin_explanation_button_cancel)

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
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { contentDescription = title },
        )

        bullets.forEach { line ->
            BulletPoint(line)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onCancelClicked,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .semantics { contentDescription = cancelLabel },
                contentPadding = PaddingValues(16.dp),
            ) {
                Text(text = cancelLabel, fontSize = 18.sp)
            }
            Button(
                onClick = onSignInClicked,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .semantics { contentDescription = signInLabel },
                contentPadding = PaddingValues(16.dp),
            ) {
                Text(text = signInLabel, fontSize = 18.sp)
            }
        }
    }
}

@Composable
private fun BulletPoint(label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 480.dp)
            .padding(vertical = 4.dp)
            .semantics { contentDescription = label },
        verticalAlignment = Alignment.Top,
    ) {
        Text(text = "• ", fontSize = 18.sp)
        Text(
            text = label,
            fontSize = 18.sp,
            modifier = Modifier.weight(1f),
        )
    }
}
