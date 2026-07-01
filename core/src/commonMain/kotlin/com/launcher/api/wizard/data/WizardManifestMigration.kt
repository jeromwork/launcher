package com.launcher.api.wizard.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Migrates a wizard manifest from `schemaVersion=1` (pre-TASK-65) to v2.
 *
 * v1 → v2 changes (R6, FR-002):
 *   - removes `body.appFamilyId` (preset identity is no longer tied to a
 *     single app-family).
 *   - bumps top-level `schemaVersion` 1 → 2.
 *
 * Idempotent: calling on an already-v2 input returns input unchanged.
 *
 * Forward-compat: unknown additive fields preserved (per ConfigParser policy).
 */
fun migrateLegacyWizardManifest(root: JsonObject): JsonObject {
    val schemaVersion = root["schemaVersion"]?.jsonPrimitive?.intOrNull
    // Only act on v1 — v2+ pass through unchanged.
    if (schemaVersion != 1) return root

    return buildJsonObject {
        for ((key, value) in root) {
            when (key) {
                "schemaVersion" -> put("schemaVersion", JsonPrimitive(2))
                "body" -> put("body", stripAppFamilyId(value.jsonObject))
                else -> put(key, value)
            }
        }
    }
}

private fun stripAppFamilyId(body: JsonObject): JsonObject = buildJsonObject {
    for ((key, value) in body) {
        if (key == "appFamilyId") continue
        put(key, value)
    }
}

