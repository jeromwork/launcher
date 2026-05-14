package com.launcher.adapters.config

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.launcher.adapters.config.db.ConfigStore
import com.launcher.adapters.config.db.ConfigStoreQueries
import com.launcher.api.config.ConfigDocument
import com.launcher.api.config.ConfigDocumentWireFormat
import com.launcher.api.config.LocalConfigStore
import com.launcher.api.config.PendingLocalChanges
import com.launcher.api.config.ServerTimestamp
import com.launcher.api.result.Outcome
import com.launcher.api.wireformat.WireFormatJson
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

/**
 * Production [LocalConfigStore] implementation backed by SQLDelight (spec 008
 * Phase 3 T051, plan.md).
 *
 * Lives in commonMain — KMP-pure. Android driver injected via
 * `AndroidSqlDriverProvider` (androidMain), future iOS driver via
 * `NativeSqlDriverProvider` per ADR-001 parity gate.
 *
 * Serializes [ConfigDocument] / [PendingLocalChanges] to JSON через wire-format
 * (same writer used for Firestore) — единый JSON source of truth для wire +
 * local persistence, simplifies fakes/real parity tests.
 *
 * Reads are dispatched to [ioDispatcher] (default `Dispatchers.Default` —
 * caller wires `Dispatchers.IO` on Android per plan.md SC-004a budget).
 */
class SqlDelightLocalConfigStore(
    db: ConfigStore,
    private val ioDispatcher: CoroutineDispatcher,
) : LocalConfigStore {

    private val queries: ConfigStoreQueries = db.configStoreQueries
    private val json = WireFormatJson.json

    override suspend fun readAppliedConfig(linkId: String): ConfigDocument? =
        withContext(ioDispatcher) {
            val row = queries.readAppliedConfig(linkId).executeAsOneOrNull() ?: return@withContext null
            decodeConfig(row.configJson)
        }

    override suspend fun writeAppliedConfig(linkId: String, config: ConfigDocument) {
        val encoded = ConfigDocumentWireFormat.serialize(config).toString()
        withContext(ioDispatcher) {
            queries.upsertAppliedConfig(
                linkId = linkId,
                configJson = encoded,
                appliedAt = nowMillis(),
                schemaVersion = config.schemaVersion.toLong(),
            )
        }
    }

    override suspend fun readPending(linkId: String): PendingLocalChanges? =
        withContext(ioDispatcher) {
            val row = queries.readPending(linkId).executeAsOneOrNull() ?: return@withContext null
            val draft = decodeConfig(row.draftJson) ?: return@withContext null
            PendingLocalChanges(
                linkId = linkId,
                snapshotServerUpdatedAt = ServerTimestamp(
                    epochSeconds = row.snapshotEpochSeconds,
                    nanoseconds = row.snapshotNanoseconds.toInt(),
                ),
                draftConfig = draft,
            )
        }

    override suspend fun writePending(linkId: String, pending: PendingLocalChanges) {
        val encoded = ConfigDocumentWireFormat.serialize(pending.draftConfig).toString()
        withContext(ioDispatcher) {
            queries.upsertPending(
                linkId = linkId,
                snapshotEpochSeconds = pending.snapshotServerUpdatedAt.epochSeconds,
                snapshotNanoseconds = pending.snapshotServerUpdatedAt.nanoseconds.toLong(),
                draftJson = encoded,
                updatedAt = nowMillis(),
            )
        }
    }

    override suspend fun clearPending(linkId: String) {
        withContext(ioDispatcher) {
            queries.clearPending(linkId)
        }
    }

    override fun pendingLinks(): Flow<Set<String>> =
        queries.allPendingLinks()
            .asFlow()
            .mapToList(ioDispatcher)
            .map { it.toSet() }

    // ─── helpers ──────────────────────────────────────────────────────────

    private fun decodeConfig(jsonText: String): ConfigDocument? {
        val element = json.parseToJsonElement(jsonText) as? JsonObject ?: return null
        return when (val r = ConfigDocumentWireFormat.deserialize(element)) {
            is Outcome.Success -> r.value
            is Outcome.Failure -> null
        }
    }

    /**
     * Epoch millis для appliedAt / updatedAt timestamp columns. Uses platform
     * clock indirectly через `kotlinx.datetime` is overkill для simple millis —
     * we use a callable below to allow tests to control time if needed.
     */
    private fun nowMillis(): Long = currentTimeMillis()
}

/**
 * Platform clock — `expect/actual` would be acceptable, но проще inject (
 * adapter constructor) если когда-нибудь понадобится. For now: use
 * [System.currentTimeMillis] на JVM/Android; iOS adapter будет inject native time.
 *
 * Avoiding `kotlinx.datetime` dep here keeps SQLDelight adapter focused.
 */
internal expect fun currentTimeMillis(): Long
