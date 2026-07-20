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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.launcher.adapters.crypto.PairingCryptoCoordinator
import com.launcher.api.identity.DeviceIdProvider
import com.launcher.ui.theme.LauncherTheme
import family.crypto.api.AeadCipher
import family.crypto.api.AsymmetricCrypto
import family.crypto.api.SecureKeyStore
import family.crypto.api.values.Ciphertext
import family.pairing.api.CIPHER_SUITE_ID_V1
import family.pairing.api.DeviceId
import family.pairing.api.EncryptedEnvelope
import family.pairing.api.EncryptedMediaStorage
import family.pairing.api.POLY1305_MAC_SIZE
import family.pairing.api.Recipient
import family.pairing.api.SUPPORTED_SCHEMA_VERSION
import family.pairing.api.XCHACHA20_NONCE_SIZE
import java.security.MessageDigest
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * Spec 011 FR-070 / US-6 debug smoke screen.
 *
 * TASK-51 Phase 6 — переписан на cryptokit (AeadCipher / AsymmetricCrypto /
 * SecureKeyStore / EncryptedMediaStorage), try/catch вместо Outcome, inline
 * SHA-256 для fingerprint вместо удалённого HashFunction port (R-004, FR-014).
 *
 * Three buttons:
 *  1. **Self-roundtrip**: generate 16 random bytes → encrypt for self →
 *     decrypt → assert plaintext matches. Validates что libsodium native
 *     loads, SecureKeyStore работает, ciphertext round-trips.
 *  2. **Encrypt for self + upload**: same encrypt, но sealed под own pub,
 *     upload в Storage. Запоминает uuid.
 *  3. **Download + decrypt by uuid**: ввести uuid, скачать blob, расшифровать.
 *
 * Запуск:
 * `adb shell am start -n com.launcher.app/.debug.Spec011SmokeDebugActivity`
 */
class Spec011SmokeDebugActivity : ComponentActivity() {

    private val aead: AeadCipher by inject()
    private val asymm: AsymmetricCrypto by inject()
    private val secureKeyStore: SecureKeyStore by inject()
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
                    secureKeyStore = secureKeyStore,
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
    secureKeyStore: SecureKeyStore,
    storage: EncryptedMediaStorage,
    deviceIdProvider: DeviceIdProvider,
    coordinator: PairingCryptoCoordinator,
) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("Ready. Ensure keys ready на старте.") }
    var ownDeviceIdStr by remember { mutableStateOf("(loading…)") }
    var inputLinkId by remember { mutableStateOf("") }
    var inputUuid by remember { mutableStateOf("") }
    var inputPeerDeviceId by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        runCatching {
            val id = deviceIdProvider.currentDeviceId().first()
            ownDeviceIdStr = id
            inputPeerDeviceId = ""
            coordinator.ensureKeysReady()
            // R-004 / FR-014: inline SHA-256 для public-key fingerprint
            // (HashFunction port removed in TASK-51 Phase 7).
            val encPub = secureKeyStore.load(PairingCryptoCoordinator.ENC_PUB_KEY_ID)
            val fingerprint = encPub?.let {
                MessageDigest.getInstance("SHA-256").digest(it)
                    .take(8)
                    .joinToString(" ") { b -> "%02X".format(b) }
            } ?: "(no pub key)"
            status = "Keys ready. Pub fingerprint (SHA-256 prefix): $fingerprint"
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

                        // Derive a fresh symmetric key для self-roundtrip
                        // (no need для CEK seal here — cryptokit AeadCipher
                        // expects raw 32-byte key + аутогенерируемый nonce).
                        val symKey = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
                        val ciphertext: Ciphertext = aead.encrypt(plaintext, symKey)
                        val decrypted = aead.decrypt(ciphertext, symKey)
                        val match = decrypted.toHex() == plaintextHex
                        status = if (match) {
                            "Phase A self-roundtrip OK\nplaintext: $plaintextHex\n(${ciphertext.bytes.size} bytes ciphertext)"
                        } else {
                            "Phase A hex MISMATCH\nwanted: $plaintextHex\ngot:    ${decrypted.toHex()}"
                        }
                    }.onFailure { status = "Self-roundtrip threw: ${it.javaClass.simpleName} ${it.message}" }
                }
            }) { Text("Run self-roundtrip (16 random bytes)") }

            HorizontalDivider()
            Text("Phase B — encrypt for self + upload (cross-device):")
            TextField(value = inputLinkId, onValueChange = { inputLinkId = it }, label = { Text("linkId (from pairing)") })
            TextField(value = inputPeerDeviceId, onValueChange = { inputPeerDeviceId = it }, label = { Text("peer deviceId (UUID)") })

            Button(onClick = {
                scope.launch {
                    runCatching {
                        if (inputLinkId.isBlank() || inputPeerDeviceId.isBlank()) {
                            status = "Phase B: fill linkId + peerDeviceId first"
                            return@launch
                        }
                        // Self-recipient mode: seal CEK под own pub, upload envelope.
                        val ownPub = secureKeyStore.load(PairingCryptoCoordinator.ENC_PUB_KEY_ID)
                            ?: run {
                                status = "Phase B: own encryption pub key not in keystore"
                                return@launch
                            }

                        val plaintext = ByteArray(16)
                        java.security.SecureRandom().nextBytes(plaintext)
                        val plaintextHex = plaintext.toHex()

                        // CEK для XChaCha20-Poly1305 — 32 bytes.
                        val cek = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
                        try {
                            val ct = aead.encrypt(plaintext, cek)
                            val sealed = asymm.sealForRecipient(cek, ownPub).bytes

                            val envelope = EncryptedEnvelope(
                                schemaVersion = SUPPORTED_SCHEMA_VERSION,
                                cipherSuiteId = CIPHER_SUITE_ID_V1,
                                nonce = ByteArray(XCHACHA20_NONCE_SIZE),
                                recipients = listOf(Recipient(DeviceId(ownDeviceIdStr), sealed)),
                                ciphertext = ct.bytes,
                                mac = ByteArray(POLY1305_MAC_SIZE),
                            )
                            val uuid = Uuid.random()
                            storage.upload(inputLinkId, uuid, envelope)
                            status = "Phase B uploaded\nuuid: $uuid\nlinkId: $inputLinkId\nplaintext: $plaintextHex"
                        } finally {
                            cek.fill(0)
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
                        val envelope = storage.download(inputLinkId, parsedUuid)
                        val recipient = envelope.recipients.firstOrNull { it.deviceId.value == ownDeviceIdStr }
                        if (recipient == null) {
                            status = "Phase C: own deviceId not in recipients (envelope was encrypted for peer, not us)"
                            return@launch
                        }
                        val ownPriv = secureKeyStore.load(PairingCryptoCoordinator.ENC_KEY_ID)
                            ?: run {
                                status = "Phase C: own encryption priv key not in keystore"
                                return@launch
                            }
                        try {
                            val sealedBlob = family.crypto.api.values.SealedBlob(recipient.sealedCEK)
                            val cek = asymm.openSealed(sealedBlob, ownPriv)
                            try {
                                val decoded = aead.decrypt(Ciphertext(envelope.ciphertext), cek)
                                status = "Phase C decrypted\nplaintext (hex): ${decoded.toHex()}"
                            } finally {
                                cek.fill(0)
                            }
                        } finally {
                            ownPriv.fill(0)
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
