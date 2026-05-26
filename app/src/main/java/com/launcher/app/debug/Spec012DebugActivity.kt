package com.launcher.app.debug

import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.launcher.api.crypto.AeadCipher
import com.launcher.api.media.MediaPicker
import com.launcher.api.media.MediaPickerError
import com.launcher.api.result.Outcome
import com.launcher.ui.theme.LauncherTheme
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * Spec 012 — debug-only Activity for isolated pipeline verification.
 *
 * Что проверяет:
 *  1. MediaPicker (FR-007) — open system Photo Picker, выбрать image.
 *  2. AeadCipher.encrypt → decrypt (FR-001, FR-004) — XChaCha20-Poly1305 roundtrip
 *     над выбранным файлом.
 *  3. Bitmap render (FR-019) — отобразить расшифрованные байты как Image.
 *
 * Не покрывает:
 *  - PrivateMediaUploader / Resolver facades (требуют DeviceIdentity + linkId).
 *  - EncryptedMediaStorage upload/download (требует Firebase / B2 wiring).
 *  - BlobReferenceLedger refCount (требует SqlDelight).
 *  - Document slot integration (требует ConfigEditor + admin flow).
 *
 * Запуск:
 *   adb shell am start -n com.launcher.app.mock/com.launcher.app.debug.Spec012DebugActivity
 *
 * NOT for production. Manifest exported=true ради adb-only debugging.
 */
class Spec012DebugActivity : ComponentActivity() {

    private val aeadCipher: AeadCipher by inject()
    private val mediaPicker: MediaPicker by inject()

    private var pickedBytes by mutableStateOf<ByteArray?>(null)
    private var decryptedBytes by mutableStateOf<ByteArray?>(null)
    private var statusMessage by mutableStateOf<String>("Готов к тесту.")

    private val pickerLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) {
            statusMessage = "Выбор отменён."
            return@registerForActivityResult
        }
        // SystemPhotoPickerAdapter ожидает Uri через resolveUri, но у нас direct path.
        // Читаем bytes напрямую через ContentResolver для debug-простоты.
        lifecycleScope.launch {
            try {
                val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes == null) {
                    statusMessage = "Не удалось прочитать файл."
                    return@launch
                }
                // Debug-only cap (10 MB) — production cap (MediaPicker.SIZE_CAP_BYTES = 500 KB)
                // умышленно строже ради bandwidth admin→managed sync.
                val debugCap = 10L * 1024L * 1024L
                if (bytes.size > debugCap) {
                    statusMessage = "Файл слишком большой: ${bytes.size} байт (макс $debugCap для debug)."
                    return@launch
                }
                pickedBytes = bytes
                statusMessage = "Выбрано: ${bytes.size} байт. Жми «Зашифровать и расшифровать»."
            } catch (e: Throwable) {
                statusMessage = "Ошибка чтения: ${e.message}"
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LauncherTheme(preset = null) {
                Scaffold(
                    topBar = { TopAppBar(title = { Text("Spec 012 Debug") }) },
                ) { padding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(statusMessage, style = MaterialTheme.typography.bodyMedium)

                        Button(
                            onClick = {
                                pickerLauncher.launch(
                                    androidx.activity.result.PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly,
                                    ),
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("1. Выбрать фото") }

                        Button(
                            onClick = { runEncryptDecrypt() },
                            enabled = pickedBytes != null,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("2. Зашифровать и расшифровать") }

                        val decrypted = decryptedBytes
                        if (decrypted != null) {
                            Text(
                                "Расшифровано ${decrypted.size} байт. Render Bitmap ниже:",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(Modifier.height(8.dp))
                            val bitmap = remember(decrypted) {
                                BitmapFactory.decodeByteArray(decrypted, 0, decrypted.size)
                            }
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Decrypted image",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(400.dp),
                                )
                            } else {
                                Text("BitmapFactory вернул null — bytes повреждены?")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun runEncryptDecrypt() {
        val plaintext = pickedBytes ?: return
        try {
            val cek = aeadCipher.generateCEK()
            val nonce = aeadCipher.randomNonce()
            val aad = "spec012-debug".encodeToByteArray()

            val ciphertext = aeadCipher.encrypt(plaintext, cek, nonce, aad)
            val cipherDeltaSize = ciphertext.size - plaintext.size

            when (val result = aeadCipher.decrypt(ciphertext, cek, nonce, aad)) {
                is Outcome.Success -> {
                    val roundtripOk = result.value.contentEquals(plaintext)
                    decryptedBytes = result.value
                    statusMessage = buildString {
                        append("Encrypt OK: ${ciphertext.size} байт (+$cipherDeltaSize MAC/tag).\n")
                        append("Decrypt OK: roundtrip = $roundtripOk.\n")
                        append("Если roundtrip=true и картинка ниже видна — FR-001/004/019 проходят.")
                    }
                }
                is Outcome.Failure -> {
                    statusMessage = "Decrypt FAILED: ${result.error}"
                    decryptedBytes = null
                }
            }
        } catch (e: Throwable) {
            statusMessage = "Crypto error: ${e.message}"
        }
    }
}
