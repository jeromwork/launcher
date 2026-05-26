package com.launcher.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.launcher.api.media.DocumentLabel
import com.launcher.api.media.DocumentLabelError
import com.launcher.api.result.Outcome

/**
 * Spec 012 — admin-side flow «+ Документ» (US-2, FR-015, FR-016).
 *
 * UX flow:
 *  1. Initial state: user taps "Выбрать фото" → host activity launches
 *     [MediaPicker] (system Photo Picker on API 33+, SAF on older).
 *  2. After pick: bytes are placed into [state] (PreviewLoaded); user edits
 *     label and confirms.
 *  3. Submit → host triggers `PrivateMediaUploader.upload` + insert
 *     `Slot(kind=Document, args={documentRef, label})` в /config.
 *
 * Compose-state-only — no I/O here. Host (ViewModel / Decompose component)
 * owns:
 *  - launching the picker,
 *  - encoding bytes through [PrivateMediaUploader],
 *  - dispatching success / error к [AddDocumentActions.onUploadResult].
 *
 * Localisation: all strings от `private_media_admin_*` keys. Senior-safe:
 * tap targets ≥ 56dp (Button default), labels ≥ 18sp.
 *
 * Task: T1237, T1238, T1239 (Phase 5).
 */
@Composable
fun AddDocumentScreen(
    state: AddDocumentState,
    actions: AddDocumentActions,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Добавить документ",  // TODO(i18n): private_media_admin_add_document_title
                fontSize = 24.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )

            when (state) {
                AddDocumentState.AwaitingPick -> AwaitingPickContent(
                    onPickPhoto = actions.onPickPhoto,
                    onCancel = actions.onCancel,
                )

                is AddDocumentState.PreviewLoaded -> PreviewLoadedContent(
                    state = state,
                    actions = actions,
                )

                is AddDocumentState.Uploading -> UploadingContent(
                    label = state.label,
                )

                is AddDocumentState.UploadError -> UploadErrorContent(
                    state = state,
                    actions = actions,
                )
            }
        }
    }
}

@Composable
private fun AwaitingPickContent(
    onPickPhoto: () -> Unit,
    onCancel: () -> Unit,
) {
    Spacer(modifier = Modifier.height(32.dp))
    Text(
        text = "Выберите фото документа из галереи (паспорт, СНИЛС, медкарта).",
        fontSize = 18.sp,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
    Spacer(modifier = Modifier.height(32.dp))
    Button(
        onClick = onPickPhoto,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text = "Выбрать фото", fontSize = 20.sp)
    }
    OutlinedButton(
        onClick = onCancel,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text = "Отмена", fontSize = 18.sp)
    }
}

@Composable
private fun PreviewLoadedContent(
    state: AddDocumentState.PreviewLoaded,
    actions: AddDocumentActions,
) {
    var label by rememberSaveable(state) { mutableStateOf(state.suggestedLabel ?: "") }

    // Thumbnail preview (real bitmap rendering — host adapter renders byte[].
    // Here we show только marker that bytes loaded.)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Фото выбрано (${state.bytesSizeKb} КБ)",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    OutlinedTextField(
        value = label,
        onValueChange = { label = it },
        label = { Text("Подпись (например, Паспорт)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    // Validation hint (если label превышает лимит).
    when (val validation = DocumentLabel.sanitiseAndValidate(label)) {
        is Outcome.Failure -> {
            val msg = when (val err = validation.error) {
                DocumentLabelError.Empty -> "Введите подпись"
                DocumentLabelError.OnlyControlChars -> "Подпись пуста после очистки"
                is DocumentLabelError.TooLong -> "Подпись слишком длинная (${err.actual}/${err.max})"
            }
            Text(
                text = msg,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.error,
            )
        }
        is Outcome.Success -> Unit  // no message — valid.
    }

    val isValid = DocumentLabel.sanitiseAndValidate(label) is Outcome.Success

    Spacer(modifier = Modifier.height(16.dp))
    Button(
        onClick = {
            val v = DocumentLabel.sanitiseAndValidate(label)
            if (v is Outcome.Success) {
                actions.onConfirm(v.value)
            }
        },
        enabled = isValid,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text = "Сохранить", fontSize = 20.sp)
    }
    OutlinedButton(
        onClick = actions.onCancel,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text = "Отмена", fontSize = 18.sp)
    }
}

@Composable
private fun UploadingContent(label: String) {
    Spacer(modifier = Modifier.height(48.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(modifier = Modifier.size(64.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "Загружаю «$label»…", fontSize = 18.sp)
        }
    }
}

@Composable
private fun UploadErrorContent(
    state: AddDocumentState.UploadError,
    actions: AddDocumentActions,
) {
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = state.message,
        fontSize = 18.sp,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
    Spacer(modifier = Modifier.height(16.dp))
    if (state.retryable) {
        Button(
            onClick = actions.onRetry,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = "Попробовать снова", fontSize = 20.sp)
        }
    }
    OutlinedButton(
        onClick = actions.onCancel,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text = "Отмена", fontSize = 18.sp)
    }
}

// ─── State & Actions ────────────────────────────────────────────────────

/**
 * Pure state held by host (ViewModel). Compose just renders.
 */
sealed class AddDocumentState {
    /** User еще не выбрал фото. Show "Pick photo" CTA. */
    data object AwaitingPick : AddDocumentState()

    /** Photo bytes loaded; user редактирует label. */
    data class PreviewLoaded(
        val bytesSizeKb: Int,
        val suggestedLabel: String? = null,
    ) : AddDocumentState()

    /** Upload in progress — show spinner. */
    data class Uploading(val label: String) : AddDocumentState()

    /** Upload failed — show error + retry если applicable. */
    data class UploadError(
        val message: String,
        val retryable: Boolean,
    ) : AddDocumentState()
}

data class AddDocumentActions(
    /** User tapped "Pick photo" → host launches MediaPicker. */
    val onPickPhoto: () -> Unit = {},
    /** User confirmed (clicked "Сохранить") with [label]. Host triggers upload. */
    val onConfirm: (label: String) -> Unit = {},
    /** Retry tapped after upload failure. */
    val onRetry: () -> Unit = {},
    /** User cancelled — host pops navigation. */
    val onCancel: () -> Unit = {},
)
