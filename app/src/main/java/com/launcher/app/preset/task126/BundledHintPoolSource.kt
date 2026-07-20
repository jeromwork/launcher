package com.launcher.app.preset.task126

import android.content.res.AssetManager
import com.launcher.preset.model.HintFlowEntry
import com.launcher.preset.port.HintPoolSource
import com.launcher.wire.WireVersion
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Version this build reads for the hint-pool document (`wire-format.md` §11). */
private val SCHEMA_VERSION: WireVersion = WireVersion(1, 0)

/**
 * T035 — BundledHintPoolSource (FR-007, CL-7).
 *
 * Reads `assets/hint-pool.json` (`hint-pool-schema-v1`) once and caches the
 * parsed list for subsequent calls. Missing / malformed source → empty list
 * (never throws across the domain boundary).
 *
 * // TODO(shareability): future ConfigSource adapters — file import, share intent, marketplace
 *
 * Per CLAUDE.md rule 9 — the bundled adapter is one of several planned
 * [HintPoolSource] implementations. Additional sources (file import,
 * share intent, marketplace) plug in additively without changing the wire
 * format (see `contracts/hint-pool-schema-v1.md`).
 */
class BundledHintPoolSource(
    private val assets: AssetManager,
    private val assetPath: String = "hint-pool.json",
) : HintPoolSource {

    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var cached: List<HintFlowEntry>? = null

    override suspend fun load(): List<HintFlowEntry> {
        cached?.let { return it }
        val loaded = readAndParse()
        cached = loaded
        return loaded
    }

    /**
     * Applies the reader gate of `docs/architecture/wire-format.md` §3 to the pool root.
     * False when the document needs a newer reader, or its version is absent or unparseable
     * (§4 — fail closed rather than guess the shape).
     */
    private fun isReadable(root: JsonObject): Boolean {
        val minReader = root["minReaderVersion"]?.jsonPrimitive?.content
            ?.let { WireVersion.parseOrNull(it) } ?: return false
        return SCHEMA_VERSION >= minReader
    }

    private fun readAndParse(): List<HintFlowEntry> {
        val text = try {
            assets.open(assetPath).bufferedReader().use { it.readText() }
        } catch (_: Throwable) {
            // Missing file → empty pool (hints are optional UX per CL-7).
            return emptyList()
        }
        return try {
            val root = json.parseToJsonElement(text).jsonObject
            // Version header gate (wire-format.md §3, §4). The KDoc above claimed
            // `hint-pool-schema-v1` while nothing in this class ever read the version — the
            // guarantee lived in a comment. It is now enforced: a pool declaring a reader we
            // cannot satisfy, or carrying no readable version, yields an empty list, the same
            // safe outcome already used for a missing or malformed file.
            if (!isReadable(root)) return emptyList()
            val hintsArr = root["hints"]?.jsonArray ?: return emptyList()
            hintsArr.mapNotNull { element ->
                val obj = element.jsonObject
                val hintId = obj["hintId"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val target = obj["targetComponentId"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val textKey = obj["textKey"]?.jsonPrimitive?.content ?: return@mapNotNull null
                HintFlowEntry(
                    hintId = hintId,
                    targetComponentId = target,
                    textKey = textKey,
                )
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }
}
