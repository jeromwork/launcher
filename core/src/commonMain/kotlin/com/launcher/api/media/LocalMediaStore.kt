package com.launcher.api.media

/**
 * Spec 012 — persistent app-private storage для **расшифрованных** media файлов.
 *
 * Location (Android adapter `FileLocalMediaStore`):
 *   `Context.filesDir/private-media/<uuid>`
 *
 * **⚠️ Files contain plaintext PII** (фото лиц, документов). MUST быть excluded из:
 *   - cloud-backup (Google Drive Auto Backup) — через `data_extraction_rules.xml`;
 *   - device-transfer (Samsung Smart Switch) — через `data_extraction_rules.xml`.
 *
 * Без этого exclude — расшифрованные фото паспортов автоматически уходят в Google
 * account бабушки. См. T1201/T1202 + contracts/local-media-store-layout.md.
 *
 * Persistence policy (per Clarification Q5):
 *  - **NOT LRU**, **NOT in-memory only**. Files persistent — выживают process death,
 *    Activity recreation, device reboot.
 *  - **NOT cleared под memory pressure** (это files на disk, не RAM).
 *  - **NOT wiped on revoke** ("stop future, not wipe past" — согласуется с WhatsApp/Telegram).
 *  - **NOT size-capped в spec 012** — защита от переполнения отслеживается TODO-ARCH-019.
 *
 * Task: T1212 (Phase 2). FR-003.
 */
interface LocalMediaStore {
    /** @return [LocalMediaFile] handle если файл есть, null если нет. */
    suspend fun read(uuid: String): LocalMediaFile?

    /**
     * Persist расшифрованные bytes под uuid. Overwrites если файл уже существует
     * (one-way idempotent — same uuid + same bytes = identity).
     * @return file handle указывающий на записанный файл.
     */
    suspend fun write(uuid: String, bytes: ByteArray): LocalMediaFile

    /**
     * Idempotent: delete non-existing = no error.
     * Used by housekeeping reconciler when BlobReferenceLedger.refCount drops to 0.
     */
    suspend fun delete(uuid: String)

    /** Quick check без bytes load. */
    suspend fun exists(uuid: String): Boolean

    /** Сумма размеров всех файлов в store. Для observability / future quota (TODO-ARCH-019). */
    suspend fun totalSizeBytes(): Long
}

/**
 * Handle на recorded media file. Не выставляет `java.io.File` или Android `Uri`
 * в domain слой — adapter возвращает bytes по запросу.
 *
 * Caller использует [readBytes] чтобы получить plaintext (для Bitmap decode и т.д.).
 *
 * Spec 012 не использует `expect class` — bytes read через метод (CLAUDE.md rule 4 —
 * нет необходимости в platform-specific seam если pure interface достаточен).
 */
interface LocalMediaFile {
    val sizeBytes: Long
    val lastAccessedAtEpochMillis: Long

    /** Lazy-read расшифрованных bytes из persistent storage. */
    suspend fun readBytes(): ByteArray
}
