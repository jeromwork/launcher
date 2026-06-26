package family.keys.android

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import cryptokit.crypto.api.AsymmetricCrypto
import cryptokit.crypto.api.SecureKeyStore
import cryptokit.crypto.api.values.KeyId
import family.keys.api.DeviceId
import family.keys.api.internal.DeviceIdentity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.SecureRandom

/**
 * Android implementation of [DeviceIdentity] — F-5b Batch 3.
 *
 * **First-launch flow** (lazy on first call):
 *  1. Generate a stable random [DeviceId] (16 random bytes → base32-ish ASCII).
 *  2. Generate an X25519 keypair via [AsymmetricCrypto].
 *  3. Store private key into [SecureKeyStore] under
 *     `KeyId("config-device-priv-x25519-{deviceId}")` (Android Keystore TEE wrap).
 *  4. Persist `DeviceId` and the public key (raw 32 bytes, Base64) into DataStore
 *     so subsequent launches reload without regeneration.
 *  5. Publication of public key to [PublicKeyDirectory] is **not** done here —
 *     that is the bootstrap responsibility of `EnvelopeBootstrap` (Batch 4),
 *     because DataStore is local-only and publication requires identity + network.
 *
 * **Subsequent-launch flow**:
 *  - Load `DeviceId` and `pubKey` from DataStore.
 *  - On `myPrivKey()` call: unwrap via [SecureKeyStore.load].
 *
 * **Reinstall = new identity**: DataStore + Keystore aliases are wiped together
 * with the app, so the next launch regenerates. Old keypair becomes orphaned in
 * any envelopes pointed at the dead `DeviceId` until those envelopes are
 * re-encrypted to include the new identity. Accepted residual — pair re-bootstrap
 * is the recovery path here.
 *
 * **Storage**:
 *  - DataStore file: `datastore/device_identity_v1.preferences_pb` (excluded from
 *    cloud-backup in [data_extraction_rules.xml]).
 *  - Keystore alias: `config-device-priv-x25519-{deviceId}` (TEE-wrapped).
 *
 * **Memory hygiene** (G-1): every `myPrivKey()` returns a fresh copy that the
 * caller must `.fill(0)` after use. Internal load buffer is freed by Kotlin GC.
 *
 * TODO(server-roadmap SRV-DEVICEID-001): when migrating to own server, DeviceId
 * allocation may move server-side for collision-resistance guarantees beyond
 * local CSPRNG. The port shape is unchanged.
 */
class AndroidDeviceIdentity(
    private val context: Context,
    private val secureKeyStore: SecureKeyStore,
    private val asymmetric: AsymmetricCrypto,
    private val random: SecureRandom = SecureRandom()
) : DeviceIdentity {

    private val Context.dataStore by preferencesDataStore(name = DATASTORE_NAME)
    private val initMutex = Mutex()

    @Volatile private var cachedDeviceId: DeviceId? = null
    @Volatile private var cachedPubKey: ByteArray? = null

    override suspend fun thisDeviceId(): DeviceId {
        cachedDeviceId?.let { return it }
        return ensureInitialized().deviceId
    }

    override suspend fun myPubKey(): ByteArray {
        cachedPubKey?.let { return it.copyOf() }
        return ensureInitialized().pubKey.copyOf()
    }

    override suspend fun myPrivKey(): ByteArray {
        val state = ensureInitialized()
        val keyId = KeyId(privKeyAlias(state.deviceId))
        val priv = secureKeyStore.load(keyId)
            ?: throw IllegalStateException(
                "Keystore entry for DeviceId=${state.deviceId.value} is missing. " +
                    "Keystore wipe / Xiaomi MIUI cleanup? Re-bootstrap pair required."
            )
        return priv
    }

    private data class State(val deviceId: DeviceId, val pubKey: ByteArray)

    private suspend fun ensureInitialized(): State = initMutex.withLock {
        cachedDeviceId?.let { id ->
            cachedPubKey?.let { pub -> return State(id, pub) }
        }
        val prefs = context.dataStore.data.first()
        val storedId = prefs[KEY_DEVICE_ID]
        val storedPub = prefs[KEY_PUB_KEY]?.let { decodeBase64(it) }
        if (storedId != null && storedPub != null && storedPub.size == 32) {
            val id = DeviceId(storedId)
            cachedDeviceId = id
            cachedPubKey = storedPub
            return State(id, storedPub)
        }
        return bootstrapNewIdentity()
    }

    private suspend fun bootstrapNewIdentity(): State {
        val deviceIdStr = generateDeviceIdString()
        val deviceId = DeviceId(deviceIdStr)
        val keyPair = asymmetric.generateX25519KeyPair()
        val keyId = KeyId(privKeyAlias(deviceId))
        try {
            secureKeyStore.store(keyId, keyPair.privateKey)
        } finally {
            keyPair.privateKey.fill(0)
        }
        val pubKey = keyPair.publicKey.copyOf()
        context.dataStore.edit { prefs ->
            prefs[KEY_DEVICE_ID] = deviceIdStr
            prefs[KEY_PUB_KEY] = encodeBase64(pubKey)
        }
        cachedDeviceId = deviceId
        cachedPubKey = pubKey
        return State(deviceId, pubKey)
    }

    private fun generateDeviceIdString(): String {
        val bytes = ByteArray(DEVICE_ID_BYTE_LEN)
        random.nextBytes(bytes)
        // Lowercase hex — 16 bytes → 32 chars. Fits Firestore document-id
        // constraints, fits DeviceId limit (max 128), AND matches the kebab-case
        // pattern required by family.crypto.KeyId for the Keystore alias derived
        // from this DeviceId.
        return buildString(bytes.size * 2) {
            for (b in bytes) {
                val v = b.toInt() and 0xff
                append(HEX[v ushr 4])
                append(HEX[v and 0x0f])
            }
        }
    }

    private fun encodeBase64(bytes: ByteArray): String =
        android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)

    private fun decodeBase64(str: String): ByteArray =
        android.util.Base64.decode(str, android.util.Base64.NO_WRAP)

    companion object {
        const val DATASTORE_NAME: String = "device_identity_v1"
        const val DEVICE_ID_BYTE_LEN: Int = 16

        val KEY_DEVICE_ID: Preferences.Key<String> = stringPreferencesKey("device_id")
        val KEY_PUB_KEY: Preferences.Key<String> = stringPreferencesKey("pub_key_x25519")

        /** Keystore alias under family.crypto KeyNamespace.Config. */
        fun privKeyAlias(deviceId: DeviceId): String =
            "config-device-priv-x25519-${deviceId.value}"

        private val HEX: CharArray = "0123456789abcdef".toCharArray()
    }
}
