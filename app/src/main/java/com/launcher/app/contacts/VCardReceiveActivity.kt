package com.launcher.app.contacts

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.launcher.adapters.contacts.VCardImporterAdapter
import com.launcher.api.config.Contact
import com.launcher.api.config.ConfigEditor
import com.launcher.api.config.ElementId
import com.launcher.api.config.ValidationError
import com.launcher.api.contacts.ImportError
import com.launcher.api.contacts.RawVCard
import com.launcher.api.link.LinkRegistry
import com.launcher.api.result.Outcome
import com.launcher.app.HomeActivity
import com.launcher.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

/**
 * Spec 009 FR-027/FR-027a: receives `ACTION_SEND` + `text/x-vcard` shares
 * (WhatsApp / Telegram / system Contacts "Share contact"). Declared with
 * `launchMode="singleTask"` in the manifest so a second share while the
 * activity is already running is delivered via [onNewIntent] rather than
 * creating a duplicate task entry.
 *
 * Spec 009 Phase E flow:
 *   1. Read `EXTRA_STREAM` via [contentResolver].
 *   2. Parse via [VCardImporterAdapter].
 *   3. Validate via [Contact.fromRaw] (single entry point for all 3
 *      contact channels — picker / vCard / manual).
 *   4. Resolve target managed link via [LinkRegistry.currentLink].
 *      Spec 007 currently models one link per admin device, so we use
 *      that one. Multi-link support: add a picker step before step 5.
 *   5. Apply to [ConfigEditor.updateDraft] — adds contact to draft.
 *   6. Launch [HomeActivity] → editor screen so admin can review +
 *      publish (FR-015).
 *
 * TODO(physical-device): manual OEM matrix smoke — Samsung One UI,
 * Xiaomi MIUI, Huawei AppGallery — verify "Share contact" в WhatsApp /
 * Telegram routes here. Локально на ноутбуке не проверить (требует
 * реальное устройство).
 */
class VCardReceiveActivity : ComponentActivity() {

    private val vcardImporter: VCardImporterAdapter by lazy { VCardImporterAdapter() }
    private val configEditor: ConfigEditor by inject()
    private val linkRegistry: LinkRegistry by inject()

    private var state: UiState by mutableStateOf(UiState.Pending)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    VCardReceiveContent(
                        state = state,
                        onOpenEditor = ::launchEditor,
                        onClose = ::finish,
                    )
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
            state = UiState.NotShare
            return
        }
        val streamUri: Uri? = @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
        if (streamUri == null) {
            state = UiState.ImportFailed(ImportError.MalformedVCard("no EXTRA_STREAM"))
            return
        }
        state = UiState.Pending
        lifecycleScope.launch {
            val bytes = readBytes(streamUri)
            if (bytes == null) {
                state = UiState.ImportFailed(ImportError.MalformedVCard("read failed"))
                return@launch
            }
            when (val parse = vcardImporter.parse(bytes)) {
                is Outcome.Failure -> state = UiState.ImportFailed(parse.error)
                is Outcome.Success -> handleParsed(parse.value)
            }
        }
    }

    private suspend fun handleParsed(vcard: RawVCard) {
        // Use the first phone number from the vCard — UI for choosing
        // between multiple TEL fields is a Phase E follow-up.
        val rawPhone = vcard.phoneNumbers.firstOrNull()
        if (rawPhone == null) {
            state = UiState.ImportFailed(ImportError.MissingTel)
            return
        }
        when (val built = Contact.fromRaw(vcard.displayName, rawPhone, ElementId.random())) {
            is Outcome.Failure -> state = UiState.ValidationFailed(built.error)
            is Outcome.Success -> {
                val link = linkRegistry.currentLink().first()
                if (link == null) {
                    state = UiState.NoLink
                    return
                }
                configEditor.updateDraft(link.linkId) { current ->
                    current.copy(contacts = current.contacts + built.value)
                }
                state = UiState.AddedToDraft(linkId = link.linkId, contact = built.value)
            }
        }
    }

    private suspend fun readBytes(uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
        try {
            contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (_: Throwable) {
            null
        }
    }

    private fun launchEditor() {
        val target = (state as? UiState.AddedToDraft)?.linkId ?: return
        val intent = Intent(this, HomeActivity::class.java).apply {
            putExtra(EXTRA_OPEN_EDITOR_LINK_ID, target)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(intent)
        finish()
    }

    companion object {
        const val EXTRA_OPEN_EDITOR_LINK_ID = "spec009_open_editor_link_id"
    }
}

private sealed interface UiState {
    data object Pending : UiState
    data object NotShare : UiState
    data object NoLink : UiState
    data class ImportFailed(val error: ImportError) : UiState
    data class ValidationFailed(val error: ValidationError) : UiState
    data class AddedToDraft(val linkId: String, val contact: Contact) : UiState
}

@Composable
private fun VCardReceiveContent(
    state: UiState,
    onOpenEditor: () -> Unit,
    onClose: () -> Unit,
) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        when (state) {
            UiState.Pending -> Text(
                text = stringResource(R.string.vcard_receive_processing),
                fontSize = 18.sp,
            )
            UiState.NotShare -> Text(
                text = stringResource(R.string.vcard_receive_error_invalid),
                fontSize = 18.sp,
            )
            UiState.NoLink -> Text(
                text = "Сначала привяжите устройство пожилого пользователя в Настройках.",
                fontSize = 18.sp,
            )
            is UiState.ImportFailed -> Text(
                text = when (state.error) {
                    is ImportError.PayloadTooLarge -> stringResource(R.string.vcard_receive_error_too_large)
                    ImportError.NonUtf8 -> stringResource(R.string.vcard_receive_error_invalid)
                    ImportError.MissingFn -> stringResource(R.string.vcard_receive_error_invalid)
                    ImportError.MissingTel -> stringResource(R.string.vcard_receive_error_no_phone)
                    is ImportError.MalformedVCard -> stringResource(R.string.vcard_receive_error_invalid)
                },
                fontSize = 18.sp,
            )
            is UiState.ValidationFailed -> Text(
                text = when (state.error) {
                    ValidationError.NameEmpty -> stringResource(R.string.contact_error_name_empty)
                    is ValidationError.NameTooLong -> stringResource(R.string.contact_error_name_too_long)
                    is ValidationError.NameInvalid -> stringResource(R.string.contact_error_name_invalid)
                    ValidationError.PhoneEmpty -> stringResource(R.string.contact_error_phone_empty)
                    is ValidationError.PhoneInvalid -> stringResource(R.string.contact_error_phone_invalid)
                },
                fontSize = 18.sp,
            )
            is UiState.AddedToDraft -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Контакт добавлен в черновик:",
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${state.contact.displayName}\n${state.contact.phoneNumber}",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = onOpenEditor,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Открыть редактор", fontSize = 18.sp)
                }
                Button(
                    onClick = onClose,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Закрыть", fontSize = 18.sp)
                }
            }
        }
    }
}
