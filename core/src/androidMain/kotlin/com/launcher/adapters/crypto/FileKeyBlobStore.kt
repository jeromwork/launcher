package com.launcher.adapters.crypto

import android.content.Context
import family.crypto.api.KeyBlobStore
import family.crypto.api.values.KeyId
import family.crypto.api.values.WrappedKeyMaterial
import family.crypto.exception.CryptoException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * File-backed [KeyBlobStore] — persists wrapped key material as a [KeyBlob] JSON document
 * under `<filesDir>/keys/<keyId>.blob`.
 *
 * TASK-141: this adapter owns the on-disk wire format (schema version + @Serializable +
 * the reader gate) that `:core:crypto`'s `SecureKeyStore` must not (rule 1 crypto
 * exception). It receives already-wrapped opaque bytes from crypto and never sees the
 * plaintext key.
 */
class FileKeyBlobStore(context: Context) : KeyBlobStore {

    private val keysDir: File by lazy { File(context.applicationContext.filesDir, KEYS_DIR).apply { mkdirs() } }
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun write(keyId: KeyId, material: WrappedKeyMaterial) = withContext(Dispatchers.IO) {
        val blob = KeyBlob(
            algorithm = material.algorithm,
            createdAt = material.createdAt,
            wrappedKey = material.wrappedKey,
            iv = material.iv,
            wrapKeyAlias = material.wrapKeyAlias,
        )
        val text = json.encodeToString(KeyBlob.serializer(), blob)
        keyFile(keyId).writeBytes(text.encodeToByteArray())
    }

    override suspend fun read(keyId: KeyId): WrappedKeyMaterial? = withContext(Dispatchers.IO) {
        val file = keyFile(keyId)
        if (!file.exists()) return@withContext null
        val text = file.readBytes().decodeToString()
        val blob = try {
            json.decodeFromString(KeyBlob.serializer(), text)
        } catch (e: Exception) {
            throw CryptoException.KeyBlobDeserializationFailed(
                "Cannot parse KeyBlob for ${keyId.raw}", e
            )
        }
        // Reader gate — the version decision lives here (above crypto), TASK-141.
        if (blob.schemaVersion > KeyBlob.CURRENT_SCHEMA_VERSION) {
            throw CryptoException.UnsupportedSchemaVersion(
                found = blob.schemaVersion,
                known = KeyBlob.CURRENT_SCHEMA_VERSION,
            )
        }
        WrappedKeyMaterial(
            algorithm = blob.algorithm,
            createdAt = blob.createdAt,
            wrappedKey = blob.wrappedKey,
            iv = blob.iv,
            wrapKeyAlias = blob.wrapKeyAlias,
        )
    }

    override suspend fun delete(keyId: KeyId): Unit = withContext(Dispatchers.IO) {
        keyFile(keyId).delete()
        Unit
    }

    private fun keyFile(keyId: KeyId): File = File(keysDir, "${keyId.raw}.blob")

    companion object {
        const val KEYS_DIR = "keys"
    }
}
