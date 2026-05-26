package com.launcher.adapters.media

import android.content.Context
import com.launcher.api.media.LocalMediaFile
import com.launcher.api.media.LocalMediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Spec 012 — production [LocalMediaStore] implementation for Android.
 *
 * Layout: `Context.filesDir/private-media/<uuid>` (one file per blob, no extension).
 *
 * **⚠️ MUST be excluded from cloud-backup + device-transfer** via
 * `res/xml/data_extraction_rules.xml` (already done in T1201). See
 * [BackupRulesTest] (`app/src/test/`) для verification.
 *
 * All I/O dispatched в [Dispatchers.IO].
 *
 * Task: T1230 (Phase 4). FR-003.
 */
class FileLocalMediaStore(
    appContext: Context,
) : LocalMediaStore {

    private val baseDir: File = File(appContext.filesDir, "private-media").apply {
        if (!exists()) mkdirs()
    }

    override suspend fun read(uuid: String): LocalMediaFile? = withContext(Dispatchers.IO) {
        val file = File(baseDir, sanitiseFilename(uuid))
        if (!file.exists() || !file.isFile) return@withContext null
        FileBackedMediaFile(file)
    }

    override suspend fun write(uuid: String, bytes: ByteArray): LocalMediaFile = withContext(Dispatchers.IO) {
        val file = File(baseDir, sanitiseFilename(uuid))
        // Atomic-ish write: write to .tmp, rename.
        val tmp = File(baseDir, sanitiseFilename(uuid) + ".tmp")
        tmp.writeBytes(bytes)
        if (file.exists()) file.delete()
        tmp.renameTo(file)
        FileBackedMediaFile(file)
    }

    override suspend fun delete(uuid: String) {
        withContext(Dispatchers.IO) {
            val file = File(baseDir, sanitiseFilename(uuid))
            if (file.exists()) file.delete()
        }
    }

    override suspend fun exists(uuid: String): Boolean = withContext(Dispatchers.IO) {
        File(baseDir, sanitiseFilename(uuid)).exists()
    }

    override suspend fun totalSizeBytes(): Long = withContext(Dispatchers.IO) {
        baseDir.listFiles()
            ?.filter { it.isFile && !it.name.endsWith(".tmp") }
            ?.sumOf { it.length() }
            ?: 0L
    }

    /**
     * UUID validation. Reject path separators and `..` traversal attempts —
     * caller should never construct malicious uuids, but defence-in-depth.
     */
    private fun sanitiseFilename(uuid: String): String {
        require(uuid.matches(UUID_REGEX)) { "Invalid uuid format: $uuid" }
        return uuid
    }

    private companion object {
        val UUID_REGEX = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
    }
}

private class FileBackedMediaFile(private val file: File) : LocalMediaFile {
    override val sizeBytes: Long = file.length()
    override val lastAccessedAtEpochMillis: Long = file.lastModified()
    override suspend fun readBytes(): ByteArray = withContext(Dispatchers.IO) { file.readBytes() }
}
