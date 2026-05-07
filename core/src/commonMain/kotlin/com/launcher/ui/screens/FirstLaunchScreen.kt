package com.launcher.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.launcher.api.FlowPreset
import com.launcher.ui.components.PresetCard
import com.launcher.ui.theme.Spacing

/**
 * First-launch preset picker (US-306). Three large cards; tap saves preset
 * and advances to Home.
 *
 * Localization model: caller passes already-localized [presets] entries. Until
 * compose-resources string lookups land in :core, this keeps the screen reusable
 * on Android and iOS without dragging platform resource readers into commonMain.
 */
@Composable
fun FirstLaunchScreen(
    presets: List<PresetUiModel>,
    onPresetSelected: (FlowPreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.xl, vertical = Spacing.lg)
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                Text(
                    text = "How do you want to use the app?",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(bottom = Spacing.sm),
                )
                presets.forEach { item ->
                    PresetCard(
                        title = item.title,
                        description = item.description,
                        onClick = { onPresetSelected(item.preset) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("preset_card_${item.preset.slug}"),
                    )
                }
            }
        }
    }
}

/**
 * View-model entry for one preset row on the picker. The caller resolves the
 * platform-specific localized strings before passing this to the screen.
 */
data class PresetUiModel(
    val preset: FlowPreset,
    val title: String,
    val description: String,
)

