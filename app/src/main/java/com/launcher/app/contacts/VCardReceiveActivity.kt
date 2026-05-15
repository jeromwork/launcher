package com.launcher.app.contacts

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.launcher.adapters.contacts.VCardImporterAdapter
import com.launcher.api.contacts.ImportError
import com.launcher.api.contacts.RawVCard
import com.launcher.api.result.Outcome
import com.launcher.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Spec 009 FR-027/FR-027a: receives `ACTION_SEND` + `text/x-vcard` shares
 * (WhatsApp / Telegram / system Contacts "Share contact"). Declared with
 * `launchMode="singleTask"` in the manifest so a second share while the
 * activity is already running is delivered via [onNewIntent] rather than
 * creating a duplicate task entry.
 *
 * Spec 009 Phase 6 ships a minimal viable shell: read the vCard payload,
 * parse via [VCardImporterAdapter], display the parsed display name +
 * phone numbers, and stop. Phase 10 wires the result into the editor
 * "add tile" flow (preselect Managed → prefill EditorScreen).
 *
 * TODO(physical-device): manual OEM matrix smoke — Samsung One UI,
 * Xiaomi MIUI, Huawei AppGallery — verify "Share contact" в WhatsApp /
 * Telegram routes here. Локально на ноутбуке не проверить (требует
 * реальное устройство).
 */
class VCardReceiveActivity : ComponentActivity() {

    private var parsed: ParseResult by mutableStateOf(ParseResult.Pending)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    VCardReceiveContent(parsed)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null || intent.action != Intent.ACTION_SEND) {
            parsed = ParseResult.NotShare
            return
        }
        val streamUri: Uri? = @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
        if (streamUri == null) {
            parsed = ParseResult.Error(ImportError.MalformedVCard("no EXTRA_STREAM"))
            return
        }
        parsed = ParseResult.Pending
        lifecycleScope.launch {
            val bytes: ByteArray? = withContext(Dispatchers.IO) {
                try {
                    contentResolver.openInputStream(streamUri)?.use { it.readBytes() }
                } catch (_: Throwable) {
                    null
                }
            }
            parsed = if (bytes == null) {
                ParseResult.Error(ImportError.MalformedVCard("read failed"))
            } else {
                when (val r = VCardImporterAdapter().parse(bytes)) {
                    is Outcome.Success -> ParseResult.Parsed(r.value)
                    is Outcome.Failure -> ParseResult.Error(r.error)
                }
            }
        }
    }
}

private sealed interface ParseResult {
    data object Pending : ParseResult
    data object NotShare : ParseResult
    data class Parsed(val vcard: RawVCard) : ParseResult
    data class Error(val error: ImportError) : ParseResult
}

@Composable
private fun VCardReceiveContent(state: ParseResult) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        when (state) {
            ParseResult.Pending -> Text(stringResource(R.string.vcard_receive_processing))
            ParseResult.NotShare -> Text(stringResource(R.string.vcard_receive_error_invalid))
            is ParseResult.Parsed -> Text(
                text = "${state.vcard.displayName}\n" +
                    state.vcard.phoneNumbers.joinToString("\n"),
            )
            is ParseResult.Error -> Text(
                text = when (state.error) {
                    is ImportError.PayloadTooLarge -> stringResource(R.string.vcard_receive_error_too_large)
                    ImportError.NonUtf8 -> stringResource(R.string.vcard_receive_error_invalid)
                    ImportError.MissingFn -> stringResource(R.string.vcard_receive_error_invalid)
                    ImportError.MissingTel -> stringResource(R.string.vcard_receive_error_no_phone)
                    is ImportError.MalformedVCard -> stringResource(R.string.vcard_receive_error_invalid)
                },
            )
        }
    }
}
