package com.launcher.app.preset.task126

import android.content.res.AssetManager
import com.launcher.preset.model.HintFlowEntry
import com.launcher.preset.port.HintPoolSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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

    private fun readAndParse(): List<HintFlowEntry> {
        val text = try {
            assets.open(assetPath).bufferedReader().use { it.readText() }
        } catch (_: Throwable) {
            // Missing file → empty pool (hints are optional UX per CL-7).
            return emptyList()
        }
        return try {
            val root = json.parseToJsonElement(text).jsonObject
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
