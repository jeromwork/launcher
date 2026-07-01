package com.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.launcher.api.preset.PresetRef
import com.launcher.api.wizard.ConfigKind
import com.launcher.api.wizard.ConfigSource
import com.launcher.api.wizard.ConfigSourceResult
import com.launcher.api.wizard.data.ConfigDocument

/**
 * First-launch + Settings → "change preset" picker (FR-012, US-1).
 *
 * Loads `ConfigKind.Preset` from [ConfigSource]; renders Material3 cards.
 * Tap target ≥ 56dp (senior-safe), contrast ≥ 4.5:1 via M3 default scheme,
 * TalkBack description per card (accessibility partial, Gate 5).
 *
 * Safety net (FR-012): if list is empty (parse failure / no assets), the
 * caller logs warning and proceeds with hardcoded `simple-launcher` slug.
 */
@Composable
fun PresetPickerScreen(
    configSource: ConfigSource,
    onPick: (slug: String, ref: PresetRef) -> Unit,
    modifier: Modifier = Modifier,
) {
    var presets by remember { mutableStateOf<List<PresetCardData>>(emptyList()) }

    LaunchedEffect(configSource) {
        presets = loadPresets(configSource)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        items(presets, key = { it.ref.toCompositeKey() }) { p ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 56.dp)
                    .semantics { contentDescription = "${p.label}. ${p.description}" }
                    .padding(0.dp),
                onClick = { onPick(p.slug, p.ref) },
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(p.label, style = MaterialTheme.typography.titleMedium)
                    Text(p.description, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

internal data class PresetCardData(
    val slug: String,
    val ref: PresetRef,
    val label: String,
    val description: String,
)

private suspend fun loadPresets(source: ConfigSource): List<PresetCardData> {
    val summaries = source.list(ConfigKind.Preset)
    return summaries.mapNotNull { summary ->
        val loaded = source.load(ConfigKind.Preset, summary.id)
        val doc = (loaded as? ConfigSourceResult.Success)?.document as? ConfigDocument.PresetDoc
            ?: return@mapNotNull null
        PresetCardData(
            slug = doc.preset.slug,
            ref = PresetRef(uid = doc.preset.uid, version = doc.preset.version),
            label = doc.preset.label,
            description = doc.preset.description,
        )
    }
}
