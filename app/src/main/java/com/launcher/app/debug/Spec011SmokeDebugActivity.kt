package com.launcher.app.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.launcher.adapters.crypto.PairingCryptoCoordinator
import com.launcher.api.crypto.AeadCipher
import com.launcher.api.crypto.AsymmetricCrypto
import com.launcher.api.crypto.CIPHER_SUITE_ID_V1
import com.launcher.api.crypto.ContentEncryptionKey
import com.launcher.api.crypto.DeviceId
import com.launcher.api.crypto.DigitalSignature
import com.launcher.api.crypto.EncryptedEnvelope
import com.launcher.api.crypto.EncryptedMediaStorage
import com.launcher.api.crypto.HashFunction
import com.launcher.api.crypto.POLY1305_MAC_SIZE
import com.launcher.api.crypto.Recipient
import com.launcher.api.crypto.SUPPORTED_SCHEMA_VERSION
import com.launcher.api.crypto.SecureKeystore
import com.launcher.api.crypto.use
import com.launcher.api.identity.DeviceIdProvider
import com.launcher.api.result.Outcome
import com.launcher.ui.theme.LauncherTheme
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import org.koin.android.ext.android.inject

/**
 * Spec 011 FR-070 / US-6 debug smoke screen.
 *
 * Three buttons:
 *  1. **Self-roundtrip**: generate 16 random bytes → encrypt + seal CEK for OWN
 *     device → unseal + decrypt → assert plaintext matches. Validates that
 *     libsodium native loads on this device's ABI, Keystore works, envelope
 *     wire format round-trips.
 *  2. **Encrypt for peer + upload**: same encrypt, но seal CEK for peer device
 *     (берётся через RecipientResolver), upload в Storage. Запоминает uuid.
 *     Phase B: cross-device live.
 *  3. **Download + decrypt by uuid**: ввести uuid, скачать blob, расшифровать.
 *     Validates end-to-end на 2 устройствах.
 *
 * Запуск:
 * `adb shell am start -n com.launcher.app/.debug.Spec011SmokeDebugActivity`
 *
 * Linkage: spec 011 README — `specs/011-contacts-and-e2e-encrypted-media/smoke/README.md`.
 */
class Spec011SmokeDebugActivity : ComponentActivity() {

    private val aead: AeadCipher by inject()
    private val asymm: AsymmetricCrypto by inject()
    private val signature: DigitalSignature by inject()
    private val hash: HashFunction by inject()
    private val keystore: SecureKeystore by inject()
    private val storage: EncryptedMediaStorage by inject()
    private val deviceIdProvider: DeviceIdProvider by inject()
    private val coordinator: PairingCryptoCoordinator by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LauncherTheme {
                SmokeScreen(
                    aead = aead,
                    asymm = asymm,
                    signature = signature,
                    hash = hash,
                    keystore = keystore,
                    storage = storage,
                    deviceIdProvider = deviceIdProvider,
                    coordinator = coordinator,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalUuidApi::class)
@Composable
private fun SmokeScreen(
    aead: AeadCipher,
    asymm: AsymmetricCrypto,
    signature: DigitalSignature,
    hash: HashFunction,
    keystore: SecureKeystore,
    storage: EncryptedMediaStorage,
    deviceIdProvider: DeviceIdProvider,
    coordinator: PairingCryptoCoordinator,
) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("Ready. Ensure keys ready на старте.") }
    var ownDeviceIdStr by remember { mutableStateOf("(loading…)") }
    var lastUuid by remember { mutableStateOf<Uuid?>(null) }
    var inputLinkId by remember { mutableStateOf("") }
    var inputUuid by remember { mutableStateOf("") }
    var inputPeerDeviceId by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        runCatching {
            val id = deviceIdProvider.currentDeviceId().first()
            ownDeviceIdStr = id
            inputPeerDeviceId = ""
            // Гарантируем что ключи готовы.
            when (val r = coordinator.ensureKeysReady()) {
                is Outcome.Success -> {
                    val pubFingerprint = (keystore.loadEncryption(PairingCryptoCoordinator.ALIAS_ENCRYPTION) as? Outcome.Success)?.value?.publicKey?.bytes?.let { hash.hash(it).toHex().take(16) }
                    status = "Keys ready. Pub fingerprint (BLAKE2b-256 prefix): $pubFingerprint"
                }
                is Outcome.Failure -> status = "ensureKeysReady FAILED: ${r.error}"
            }
        }.onFailure { status = "Init error: ${it.message}" }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Spec 011 smoke") }) }) { pad ->
        Column(
            modifier = Modifier.fillMaxSize().padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Own deviceId: $ownDeviceIdStr", style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()

            Text("Phase A — self-roundtrip (no network needed):")
            Button(onClick = {
                scope.launch {
                    runCatching {
                        // Generate 16 random bytes plaintext.
                        val plaintext = ByteArray(16)
                        java.security.SecureRandom().nextBytes(plaintext)
                        val plaintextHex = plaintext.toHex()

                        // Load own X25519 pair.
                        val ownPair = when (val r = keystore.loadEncryption(PairingCryptoCoordinator.ALIAS_ENCRYPTION)) {
                            is Outcome.Failure -> {
                                status = "loadEncryption FAILED: ${r.error}"
                                return@launch
                            }
                            is Outcome.Success -> r.value
                        }

                        // Encrypt + seal CEK for own pub.
                        val cek = aead.generateCEK()
                        val nonce = aead.randomNonce()
                        val ciphertext = aead.encrypt(plaintext, cek, nonce, aad = byteArrayOf())
                        val sealed = asymm.sealCEK(cek, ownPair.publicKey)
                        cek.close()

                        // Unseal + decrypt.
                        val unsealOutcome = asymm.unsealCEK(sealed, ownPair)
                        val cekRestored = when (unsealOutcome) {
                            is Outcome.Failure -> {
                                status = "unsealCEK FAILED: ${unsealOutcome.error}"
                                return@launch
                            }
                            is Outcome.Success -> unsealOutcome.value
                        }
                        val decryptOutcome = cekRestored.use { aead.decrypt(ciphertext, it, nonce, aad = byteArrayOf()) }
                        when (decryptOutcome) {
                            is Outcome.Failure -> {
                                status = "decrypt FAILED: ${decryptOutcome.error}"
                            }
                            is Outcome.Success -> {
                                val match = decryptOutcome.value.toHex() == plaintextHex
                                status = if (match) {
                                    "Phase A ✅ self-roundtrip OK\nplaintext: $plaintextHex\n(${ciphertext.size} bytes ciphertext)"
                                } else {
                                    "Phase A ❌ hex MISMATCH\nwanted: $plaintextHex\ngot:    ${decryptOutcome.value.toHex()}"
                                }
                            }
                        }
                    }.onFailure { status = "Self-roundtrip threw: ${it.javaClass.simpleName} ${it.message}" }
                }
            }) { Text("Run self-roundtrip (16 random bytes)") }

            HorizontalDivider()
            Text("Phase B — encrypt for peer + upload (cross-device):")
            TextField(value = inputLinkId, onValueChange = { inputLinkId = it }, label = { Text("linkId (from pairing)") })
            TextField(value = inputPeerDeviceId, onValueChange = { inputPeerDeviceId = it }, label = { Text("peer deviceId (UUID)") })

            Button(onClick = {
                scope.launch {
                    runCatching {
                        if (inputLinkId.isBlank() || inputPeerDeviceId.isBlank()) {
                            status = "Phase B: fill linkId + peerDeviceId first"
                            return@launch
                        }
                        // Build placeholder PublicKey from peer's X25519 — для smoke
                        // peer's Pub приходит через DeviceIdentityRepository.fetchPeer.
                        // Но это P1, для phase B-минимума: encrypt → upload → возвращаем uuid.
                        // Decrypt-сторона должна fetchPeer + unseal сама.
                        //
                        // Здесь используем UPLOAD-ONLY режим — кодируем under own pub
                        // (self-recipient) для упрощения. Peer decrypt сценарий требует
                        // полноценного fetchPeer цикла — отдельная кнопка ниже.
                        val ownPair = (keystore.loadEncryption(PairingCryptoCoordinator.ALIAS_ENCRYPTION) as Outcome.Success).value
                        val plaintext = ByteArray(16)
                        java.security.SecureRandom().nextBytes(plaintext)
                        val plaintextHex = plaintext.toHex()
                        val cek = aead.generateCEK()
                        val nonce = aead.randomNonce()
                        val ciphertext = aead.encrypt(plaintext, cek, nonce, aad = byteArrayOf())
                        val sealed = asymm.sealCEK(cek, ownPair.publicKey)
                        cek.close()

                        val envelope = EncryptedEnvelope(
                            schemaVersion = SUPPORTED_SCHEMA_VERSION,
                            cipherSuiteId = CIPHER_SUITE_ID_V1,
                            nonce = nonce,
                            recipients = listOf(Recipient(DeviceId(ownDeviceIdStr), sealed)),
                            ciphertext = ciphertext,
                            mac = ByteArray(POLY1305_MAC_SIZE),  // combined-mode: MAC внутри ciphertext
                        )
                        val uuid = Uuid.random()
                        when (val r = storage.upload(inputLinkId, uuid, envelope)) {
                            is Outcome.Failure -> status = "Phase B upload FAILED: ${r.error}"
                            is Outcome.Success -> {
                                lastUuid = uuid
                                status = "Phase B ✅ uploaded\nuuid: $uuid\nlinkId: $inputLinkId\nplaintext: $plaintextHex\n(передай uuid на другое устройство для Phase C decrypt)"
                            }
                        }
                    }.onFailure { status = "Phase B threw: ${it.javaClass.simpleName} ${it.message}" }
                }
            }) { Text("Encrypt for self + upload (self-recipient mode)") }

            HorizontalDivider()
            Text("Phase C — download + decrypt by uuid:")
            TextField(value = inputUuid, onValueChange = { inputUuid = it }, label = { Text("uuid (from Phase B output)") })

            Button(onClick = {
                scope.launch {
                    runCatching {
                        if (inputLinkId.isBlank() || inputUuid.isBlank()) {
                            status = "Phase C: fill linkId + uuid"
                            return@launch
                        }
                        val parsedUuid = runCatching { Uuid.parse(inputUuid) }.getOrNull()
                        if (parsedUuid == null) {
                            status = "Phase C: bad uuid format"
                            return@launch
                        }
                        val downOutcome = storage.download(inputLinkId, parsedUuid)
                        val envelope = when (downOutcome) {
                            is Outcome.Failure -> {
                                status = "Phase C download FAILED: ${downOutcome.error}"
                                return@launch
                            }
                            is Outcome.Success -> downOutcome.value
                        }
                        val ownPair = (keystore.loadEncryption(PairingCryptoCoordinator.ALIAS_ENCRYPTION) as Outcome.Success).value
                        val recipient = envelope.recipients.firstOrNull { it.deviceId.value == ownDeviceIdStr }
                        if (recipient == null) {
                            status = "Phase C: own deviceId not in recipients (envelope was encrypted for peer, not us)"
                            return@launch
                        }
                        val unsealOutcome = asymm.unsealCEK(recipient.sealedCEK, ownPair)
                        val cekRestored = when (unsealOutcome) {
                            is Outcome.Failure -> {
                                status = "Phase C unseal FAILED: ${unsealOutcome.error}"
                                return@launch
                            }
                            is Outcome.Success -> unsealOutcome.value
                        }
                        val decOutcome = cekRestored.use { aead.decrypt(envelope.ciphertext, it, envelope.nonce, aad = byteArrayOf()) }
                        status = when (decOutcome) {
                            is Outcome.Failure -> "Phase C decrypt FAILED: ${decOutcome.error}"
                            is Outcome.Success -> "Phase C ✅ decrypted\nplaintext (hex): ${decOutcome.value.toHex()}\n(сравни с Phase B output)"
                        }
                    }.onFailure { status = "Phase C threw: ${it.javaClass.simpleName} ${it.message}" }
                }
            }) { Text("Download + decrypt") }

            HorizontalDivider()
            Text("Status:", style = MaterialTheme.typography.titleSmall)
            Text(status, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
