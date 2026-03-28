package com.launcher.core.profile

import android.content.Context
import com.launcher.api.DegradationReason
import com.launcher.api.EffectiveProfile
import com.launcher.api.ProfileSnapshot
import com.launcher.core.events.EventRouter
import com.launcher.core.modules.ModuleRegistry
import com.launcher.api.ProjectEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.BufferedReader
import java.nio.charset.StandardCharsets

private const val ASSET_DEFAULT_PROFILE = "default_profile.json"
private const val SUPPORTED_SCHEMA = 1

/**
 * Loads bundled profile, validates structure, applies safe fallback (contracts/profile-bootstrap.md).
 */
class ProfileEngine(
    private val context: Context,
    private val moduleRegistry: ModuleRegistry,
    private val eventRouter: EventRouter,
    private val assetPath: String = ASSET_DEFAULT_PROFILE,
) {
    private var generationCounter = 0
    private val _effectiveProfile = MutableStateFlow(fallbackEffective(ProfileSnapshot(
        schemaVersion = SUPPORTED_SCHEMA,
        id = "builtin-fallback",
        moduleFlags = emptyMap(),
        accessibilityPreset = null,
        layoutHints = emptyMap(),
    ), gen = 0))
    val effectiveProfile: StateFlow<EffectiveProfile> = _effectiveProfile.asStateFlow()

    fun loadFromAssets() {
        val rawJson = readAsset(assetPath)
        val reasons = mutableListOf<DegradationReason>()
        val snapshot = if (rawJson == null) {
            reasons.add(DegradationReason.INVALID_PROFILE_FALLBACK)
            loadBundledDefaultSnapshot()
        } else {
            parseProfile(rawJson) ?: run {
                reasons.add(DegradationReason.INVALID_PROFILE_FALLBACK)
                loadBundledDefaultSnapshot()
            }
        }
        applySnapshot(snapshot, reasons)
    }

    private fun applySnapshot(snapshot: ProfileSnapshot, extraReasons: List<DegradationReason>) {
        val gen = generationCounter + 1
        generationCounter = gen
        var effective = CompositionResolver.resolve(snapshot, gen, moduleRegistry.resolutionStates())
        if (extraReasons.isNotEmpty()) {
            effective = effective.copy(
                degradation = effective.degradation.copy(
                    reasonCodes = (effective.degradation.reasonCodes + extraReasons).distinct(),
                ),
            )
        }
        _effectiveProfile.value = effective
        eventRouter.emit(ProjectEvent.ProfileChanged(profileGeneration = gen))
    }

    private fun fallbackEffective(snapshot: ProfileSnapshot, gen: Int): EffectiveProfile =
        CompositionResolver.resolve(snapshot, gen, moduleRegistry.resolutionStates())

    private fun readAsset(path: String): String? =
        runCatching {
            context.assets.open(path).use { input ->
                BufferedReader(input.reader(StandardCharsets.UTF_8)).readText()
            }
        }.getOrNull()

    private fun loadBundledDefaultSnapshot(): ProfileSnapshot {
        val json = readAsset(ASSET_DEFAULT_PROFILE)
            ?: return ProfileSnapshot(
                schemaVersion = SUPPORTED_SCHEMA,
                id = "default",
                moduleFlags = emptyMap(),
                accessibilityPreset = null,
                layoutHints = emptyMap(),
            )
        return parseProfile(json) ?: ProfileSnapshot(
            schemaVersion = SUPPORTED_SCHEMA,
            id = "default",
            moduleFlags = emptyMap(),
            accessibilityPreset = null,
            layoutHints = emptyMap(),
        )
    }

    private fun parseProfile(jsonText: String): ProfileSnapshot? =
        runCatching {
            val o = JSONObject(jsonText)
            if (!o.has("schemaVersion")) return null
            val schema = o.getInt("schemaVersion")
            if (schema != SUPPORTED_SCHEMA) return null
            val id = if (o.has("id")) o.getString("id") else "unknown"
            val flags = mutableMapOf<String, Boolean>()
            if (o.has("moduleFlags")) {
                val mo = o.getJSONObject("moduleFlags")
                val it = mo.keys()
                while (it.hasNext()) {
                    val k = it.next()
                    flags[k] = mo.getBoolean(k)
                }
            }
            val a11y = if (o.has("accessibilityPreset") && !o.isNull("accessibilityPreset")) {
                o.getString("accessibilityPreset")
            } else {
                null
            }
            val hints = mutableMapOf<String, String>()
            if (o.has("layoutHints")) {
                val lo = o.getJSONObject("layoutHints")
                val it = lo.keys()
                while (it.hasNext()) {
                    val k = it.next()
                    hints[k] = lo.getString(k)
                }
            }
            ProfileSnapshot(
                schemaVersion = schema,
                id = id,
                moduleFlags = flags,
                accessibilityPreset = a11y,
                layoutHints = hints,
            )
        }.getOrNull()
}
