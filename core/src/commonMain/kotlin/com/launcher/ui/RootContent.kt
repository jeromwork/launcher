package com.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.launcher.ui.navigation.RootChild
import com.launcher.ui.navigation.RootComponent
import com.launcher.ui.screens.FirstLaunchScreen
import com.launcher.ui.screens.HomeScreen
import com.launcher.ui.screens.PresetUiModel
import com.launcher.ui.theme.Spacing

/**
 * Renders the root [RootComponent]'s child stack. Each [RootChild] picks its
 * Composable. Screens not yet migrated render a placeholder card so debug
 * navigation still works during T409.
 *
 * @param presetUiModels already-localized UI models for the FirstLaunch picker.
 *   Caller (Activity / iOS app entry) resolves localized strings; this avoids
 *   pulling Android resource lookup into commonMain.
 */
@Composable
fun RootContent(
    component: RootComponent,
    presetUiModels: List<PresetUiModel>,
    modifier: Modifier = Modifier,
) {
    Children(stack = component.stack, modifier = modifier) { childCreated ->
        when (val child = childCreated.instance) {
            is RootChild.FirstLaunch -> FirstLaunchScreen(
                presets = presetUiModels,
                onPresetSelected = child.component.onPresetSelected,
            )
            is RootChild.Home -> HomeScreen(component = child.component)
            is RootChild.Placeholder -> PlaceholderScreen(label = child.config.toString())
        }
    }
}

@Composable
private fun PlaceholderScreen(label: String) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(Spacing.xl),
            contentAlignment = Alignment.Center,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    "Screen not migrated yet",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
