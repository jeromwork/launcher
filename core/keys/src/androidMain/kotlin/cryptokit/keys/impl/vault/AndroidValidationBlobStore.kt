package cryptokit.keys.impl.vault

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android [ValidationBlobStore] — stores AEAD-sealed validation blobs in
 * `SharedPreferences`. The blob is already encrypted (XChaCha20-Poly1305 under the
 * candidate root); a plain SharedPreferences file is sufficient. Base64 encoding is used only
 * because SharedPreferences store strings.
 *
 * File name: `keyvault-validation-blobs.xml`. Not `EncryptedSharedPreferences` because that
 * would key-wrap ciphertext that's already keyed — pure overhead.
 */
class AndroidValidationBlobStore(context: Context) : ValidationBlobStore {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override suspend fun read(hintKey: String): ByteArray? = withContext(Dispatchers.IO) {
        val encoded = prefs.getString(hintKey, null) ?: return@withContext null
        Base64.decode(encoded, Base64.NO_WRAP)
    }

    override suspend fun write(hintKey: String, blob: ByteArray) = withContext(Dispatchers.IO) {
        val encoded = Base64.encodeToString(blob, Base64.NO_WRAP)
        prefs.edit().putString(hintKey, encoded).apply()
    }

    override suspend fun clear(hintKey: String) = withContext(Dispatchers.IO) {
        prefs.edit().remove(hintKey).apply()
    }

    companion object {
        const val PREFS_NAME = "keyvault-validation-blobs"
    }
}
