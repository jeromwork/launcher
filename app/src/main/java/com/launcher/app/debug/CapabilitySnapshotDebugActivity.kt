package com.launcher.app.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.launcher.api.capability.Capability
import com.launcher.api.capability.CapabilityRepository
import com.launcher.ui.theme.LauncherTheme
import org.koin.android.ext.android.inject

/**
 * Debug-only screen для US-1 Independent Test (spec 006).
 *
 * Показывает текущий [CapabilityRepository.snapshot] tabular: providerId,
 * displayName, available, versionCode, iconId. Используется на эмуляторе для
 * проверки snapshot rebuild при `pm install/uninstall` (SC-002).
 *
 * Запуск через `adb shell am start -n com.launcher.app/.debug.CapabilitySnapshotDebugActivity`.
 * NOT exported — internal только.
 */
class CapabilitySnapshotDebugActivity : ComponentActivity() {
    private val repository: CapabilityRepository by inject()

    @OptIn(ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LauncherTheme(preset = null) {
                CapabilityDebugScreen(repository)
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun CapabilityDebugScreen(repository: CapabilityRepository) {
    val snapshot by repository.observe().collectAsState(initial = repository.snapshot())
    Scaffold(
        topBar = { TopAppBar(title = { Text("Capability Snapshot (debug)") }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(snapshot) { cap ->
                CapabilityRow(cap)
                HorizontalDivider()
            }
            if (snapshot.isEmpty()) {
                item {
                    Text(
                        "Snapshot пуст — ProviderRegistry ещё не отдал данные или нет известных провайдеров.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun CapabilityRow(cap: Capability) {
    Column {
        Text("${cap.providerId.value} — ${cap.displayName}", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("available=${cap.available}", style = MaterialTheme.typography.bodySmall)
            Text("versionCode=${cap.versionCode ?: "—"}", style = MaterialTheme.typography.bodySmall)
        }
        Text("iconId=${cap.iconId}", style = MaterialTheme.typography.bodySmall)
    }
}
