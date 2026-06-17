package com.launcher.app.wizard

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.launcher.api.localization.StringResolver
import com.launcher.ui.senior.primitives.SeniorBodyText
import com.launcher.ui.senior.primitives.SeniorButton
import com.launcher.ui.senior.primitives.SeniorTitleText
import com.launcher.ui.senior.theme.SeniorWarmTheme
import org.koin.android.ext.android.inject

/**
 * Shown when the bundled config has `schemaVersion > known` (FR-016
 * hard-fail), or when a critical config asset can't be parsed at all.
 *
 * Senior-safe — large title, plain explanation, single Play Store button.
 * Per Q-6 (b).
 */
class PlayStoreFallbackActivity : ComponentActivity() {

    private val strings: StringResolver by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SeniorWarmTheme.Light {
                val title = remember { strings.resolve("play_store_fallback_title") }
                val body = remember { strings.resolve("play_store_fallback_body") }
                val cta = remember { strings.resolve("play_store_fallback_open_store") }
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        SeniorTitleText(title)
                        Spacer(Modifier.height(16.dp))
                        SeniorBodyText(body)
                        Spacer(Modifier.height(32.dp))
                        SeniorButton(text = cta, onClick = ::openPlayStore)
                    }
                }
            }
        }
    }

    private fun openPlayStore() {
        val uri = Uri.parse("market://details?id=$packageName")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            // Fallback to web Play Store.
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$packageName"),
                ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
            )
        }
    }
}
