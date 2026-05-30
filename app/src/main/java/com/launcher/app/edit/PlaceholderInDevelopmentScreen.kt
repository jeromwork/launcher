package com.launcher.app.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.launcher.app.R

/**
 * "В разработке" placeholder screen used for:
 *  - Widget tile-type picker tab (FR-018, FR-018a, TODO-UX-027).
 *  - Action tile-type picker tab (FR-018, FR-018a, TODO-UX-028).
 *  - Custom user-created preset (FR-008b — refuses pour preserve exit ramp).
 *
 * Honest UX rationale (FR-018a): visible но "В разработке" — admin понимает
 * что эта feature на roadmap'е, не "почему-то нету".
 */
enum class PlaceholderKind { Widget, Action, CustomPreset }

@Composable
fun PlaceholderInDevelopmentScreen(
    kind: PlaceholderKind,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val titleRes = when (kind) {
        PlaceholderKind.Widget -> R.string.f014_placeholder_widget_title
        PlaceholderKind.Action -> R.string.f014_placeholder_action_title
        PlaceholderKind.CustomPreset -> R.string.f014_placeholder_custom_preset_title
    }
    val bodyRes = when (kind) {
        PlaceholderKind.Widget -> R.string.f014_placeholder_widget_body
        PlaceholderKind.Action -> R.string.f014_placeholder_action_body
        PlaceholderKind.CustomPreset -> R.string.f014_placeholder_custom_preset_body
    }
    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("f014_placeholder_${kind.name.lowercase()}"),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(bodyRes),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = onBack,
                modifier = Modifier.testTag("f014_placeholder_back"),
            ) {
                Text(stringResource(R.string.f014_placeholder_back))
            }
        }
    }
}
