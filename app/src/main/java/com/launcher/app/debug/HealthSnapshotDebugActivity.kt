package com.launcher.app.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.launcher.api.health.HealthRepository
import com.launcher.ui.theme.LauncherTheme
import org.koin.android.ext.android.inject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Debug-only screen для US-3 Independent Test (spec 006).
 *
 * Показывает текущий [HealthRepository.snapshot]: battery, charging,
 * connectivity, ringer volume %, mute, lastSeen, appVersion. Используется
 * на эмуляторе для проверки что collector реагирует на airplane toggle,
 * volume changes, battery plug events.
 *
 * Запуск через `adb shell am start -n com.launcher.app/.debug.HealthSnapshotDebugActivity`.
 */
class HealthSnapshotDebugActivity : ComponentActivity() {
    private val repository: HealthRepository by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LauncherTheme(preset = null) {
                HealthDebugScreen(repository)
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun HealthDebugScreen(repository: HealthRepository) {
    val health by repository.observe().collectAsState(initial = repository.snapshot())
    val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
    Scaffold(
        topBar = { TopAppBar(title = { Text("Health Snapshot (debug)") }) },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HealthRow("schemaVersion", health.schemaVersion.toString())
            HealthRow("batteryPercent", "${health.batteryPercent}%")
            HealthRow("charging", health.charging.toString())
            HealthRow("connectivity", health.connectivity.name)
            HealthRow("ringerVolumePercent", "${health.ringerVolumePercent}%")
            HealthRow("audioStreamMuted", health.audioStreamMuted.toString())
            HealthRow("lastSeen", if (health.lastSeen > 0) timeFormat.format(Date(health.lastSeen)) else "—")
            HealthRow("appVersion", health.appVersion)
            HorizontalDivider()
            Text(
                "Trigger обновления: переключи airplane mode / измени громкость / подключи зарядку.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun HealthRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}
