package family.keys.android

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import family.keys.api.DeviceKeyNamespaceProvider
import family.keys.api.StableId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.SecureRandom

/**
 * Android implementation of [DeviceKeyNamespaceProvider] (T634, FR-002 device-key
 * namespace path, US-4 local-only mode).
 *
 * **First-launch flow** (lazy on first [namespace] call):
 *  1. Generate a UUID v4 via [SecureRandom] (RFC 4122 §4.4 — version bit set to
 *     0100, variant bits set to 10).
 *  2. Persist into DataStore (`datastore/device_key_namespace_v1.preferences_pb`).
 *  3. Return the value; subsequent calls return the cached/persisted UUID.
 *
 * **Why DataStore, not Keystore**: the namespace string itself is not a secret —
 * an attacker who reads it cannot derive any key without the device's root key
 * (which IS in Keystore). DataStore is sufficient and avoids an extra Keystore
 * alias for a non-sensitive value.
 *
 * **Reinstall / data clear**: regenerates a fresh UUID, which orphans all data
 * encrypted under the prior namespace. This is the accepted residual for
 * local-only mode (no recovery surface — spec.md US-4, table row F).
 *
 * **Excluded from auto-backup**: `data_extraction_rules.xml` (T677) lists this
 * DataStore so that ADB backup / cloud restore cannot leak the namespace into a
 * different physical device. (Without this guard, two devices sharing the same
 * Google account backup would end up with the same namespace and the second
 * device's root key would be derived against a foreign salt → user data lost.)
 *
 * **Thread safety**: [mutex] guards bootstrap; [cachedNamespace] is `@Volatile`
 * for the hot-path fast read.
 */
class AndroidDeviceKeyNamespaceProvider(
    private val context: Context,
    private val random: SecureRandom = SecureRandom()
) : DeviceKeyNamespaceProvider {

    private val Context.dataStore by preferencesDataStore(name = DATASTORE_NAME)
    private val initMutex = Mutex()

    @Volatile private var cachedNamespace: StableId? = null

    override suspend fun namespace(): StableId {
        cachedNamespace?.let { return it }
        return initMutex.withLock {
            cachedNamespace?.let { return@withLock it }

            val prefs = context.dataStore.data.first()
            val stored = prefs[KEY_NAMESPACE]
            if (stored != null && stored.isNotEmpty()) {
                cachedNamespace = stored
                return@withLock stored
            }
            val fresh = generateUuidV4()
            context.dataStore.edit { it[KEY_NAMESPACE] = fresh }
            cachedNamespace = fresh
            fresh
        }
    }

    private fun generateUuidV4(): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        // RFC 4122 §4.4: version (4 high bits of byte 6) = 0100; variant (2
        // high bits of byte 8) = 10. We do not call java.util.UUID.randomUUID()
        // directly because we already have a SecureRandom dependency for the
        // class (testability — injection lets tests pin a deterministic stream).
        bytes[6] = (bytes[6].toInt() and 0x0f or 0x40).toByte()
        bytes[8] = (bytes[8].toInt() and 0x3f or 0x80).toByte()
        return buildString(36) {
            for (i in bytes.indices) {
                val v = bytes[i].toInt() and 0xff
                append(HEX[v ushr 4])
                append(HEX[v and 0x0f])
                if (i == 3 || i == 5 || i == 7 || i == 9) append('-')
            }
        }
    }

    companion object {
        const val DATASTORE_NAME: String = "device_key_namespace_v1"
        val KEY_NAMESPACE: Preferences.Key<String> = stringPreferencesKey("namespace_uuid")
        private val HEX: CharArray = "0123456789abcdef".toCharArray()
    }
}
