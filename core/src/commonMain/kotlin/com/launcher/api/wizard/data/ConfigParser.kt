package com.launcher.api.wizard.data

import com.launcher.api.wizard.ConfigKind
import com.launcher.api.wizard.ConfigSourceResult
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Parser for the 5 bundled wire formats. Enforces:
 *
 *  - Forward-compat (FR-015): unknown additive fields silently ignored
 *    (via `ignoreUnknownKeys = true`).
 *  - Hard-fail (FR-016): `schemaVersion > KNOWN_VERSION` → `IncompatibleVersion`.
 *  - Header validation: missing/invalid 6-field header → `ParseError`.
 */
object ConfigParser {

    const val KNOWN_VERSION: Int = 2

    @Suppress("OPT_IN_USAGE")
    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = false
        explicitNulls = false
        encodeDefaults = true
        prettyPrint = false
    }

    fun parse(kind: ConfigKind, raw: String): ConfigSourceResult {
        return try {
            val root = json.parseToJsonElement(raw).jsonObject
            // Preset wire format is self-contained (FR-001) — no 6-field header
            // wrapper. Decode directly and synthesize header from preset fields.
            if (kind == ConfigKind.Preset) {
                return parsePreset(root)
            }
            // T644 — migrate legacy v1 wizard manifest in-place before header decode.
            val maybeMigrated = if (kind == ConfigKind.WizardManifest) {
                migrateLegacyWizardManifest(root)
            } else {
                root
            }
            val header = decodeHeader(maybeMigrated)
                ?: return ConfigSourceResult.ParseError("missing or invalid 6-field header")
            if (header.schemaVersion > KNOWN_VERSION) {
                return ConfigSourceResult.IncompatibleVersion(
                    found = header.schemaVersion,
                    known = KNOWN_VERSION,
                )
            }
            val body = maybeMigrated["body"]?.jsonObject
                ?: return ConfigSourceResult.ParseError("missing body")

            val document: ConfigDocument = when (kind) {
                ConfigKind.WizardManifest -> ConfigDocument.Manifest(
                    header,
                    json.decodeFromJsonElement(WizardManifestBody.serializer(), body),
                )
                ConfigKind.ScreenLayout -> ConfigDocument.Layout(
                    header,
                    json.decodeFromJsonElement(ScreenLayoutBody.serializer(), body),
                )
                ConfigKind.TileSet -> ConfigDocument.TileSetDoc(
                    header,
                    json.decodeFromJsonElement(TileSetBody.serializer(), body),
                )
                ConfigKind.SystemSettingsPool -> ConfigDocument.SystemSettingsPoolDoc(
                    header,
                    json.decodeFromJsonElement(SystemSettingsPoolBody.serializer(), body),
                )
                ConfigKind.UICustomizationPool -> ConfigDocument.UICustomizationPoolDoc(
                    header,
                    json.decodeFromJsonElement(UICustomizationPoolBody.serializer(), body),
                )
                ConfigKind.Preset -> error("unreachable: Preset handled above")
            }
            ConfigSourceResult.Success(document)
        } catch (e: SerializationException) {
            ConfigSourceResult.ParseError(e.message ?: "serialization failure")
        } catch (e: IllegalArgumentException) {
            ConfigSourceResult.ParseError(e.message ?: "invalid format")
        }
    }

    fun encode(document: ConfigDocument): String {
        val body: JsonObject = when (document) {
            is ConfigDocument.Manifest ->
                json.encodeToJsonElement(WizardManifestBody.serializer(), document.body).jsonObject
            is ConfigDocument.Layout ->
                json.encodeToJsonElement(ScreenLayoutBody.serializer(), document.body).jsonObject
            is ConfigDocument.TileSetDoc ->
                json.encodeToJsonElement(TileSetBody.serializer(), document.body).jsonObject
            is ConfigDocument.SystemSettingsPoolDoc ->
                json.encodeToJsonElement(SystemSettingsPoolBody.serializer(), document.body).jsonObject
            is ConfigDocument.UICustomizationPoolDoc ->
                json.encodeToJsonElement(UICustomizationPoolBody.serializer(), document.body).jsonObject
            is ConfigDocument.PresetDoc ->
                json.encodeToJsonElement(com.launcher.api.preset.Preset.serializer(), document.preset).jsonObject
        }
        if (document is ConfigDocument.PresetDoc) {
            // Preset is its own wire format — no 6-field wrapper.
            return json.encodeToString(JsonObject.serializer(), body)
        }
        val header = document.header
        val obj = buildMap<String, kotlinx.serialization.json.JsonElement> {
            put("schemaVersion", kotlinx.serialization.json.JsonPrimitive(header.schemaVersion))
            put("id", kotlinx.serialization.json.JsonPrimitive(header.id))
            put("name", kotlinx.serialization.json.JsonPrimitive(header.name))
            put("description", kotlinx.serialization.json.JsonPrimitive(header.description))
            put("deviceClass", kotlinx.serialization.json.JsonArray(
                header.deviceClass.map { kotlinx.serialization.json.JsonPrimitive(it) },
            ))
            put("body", body)
        }
        return json.encodeToString(
            JsonObject.serializer(),
            JsonObject(obj),
        )
    }

    private fun parsePreset(root: JsonObject): ConfigSourceResult {
        val schemaVersion = root["schemaVersion"]?.jsonPrimitive?.intOrNull
            ?: return ConfigSourceResult.ParseError("preset missing schemaVersion")
        if (schemaVersion > com.launcher.api.preset.PRESET_SCHEMA_VERSION) {
            return ConfigSourceResult.IncompatibleVersion(
                found = schemaVersion,
                known = com.launcher.api.preset.PRESET_SCHEMA_VERSION,
            )
        }
        val preset = json.decodeFromJsonElement(
            com.launcher.api.preset.Preset.serializer(),
            root,
        )
        val header = ConfigDocumentHeader(
            schemaVersion = preset.schemaVersion,
            id = preset.slug,
            name = preset.label,
            description = preset.description,
            deviceClass = emptyList(),
        )
        return ConfigSourceResult.Success(ConfigDocument.PresetDoc(header, preset))
    }

    private fun decodeHeader(root: JsonObject): ConfigDocumentHeader? {
        val schemaVersion = root["schemaVersion"]?.jsonPrimitive?.intOrNull ?: return null
        val id = root["id"]?.jsonPrimitive?.contentOrNull ?: return null
        val name = root["name"]?.jsonPrimitive?.contentOrNull ?: return null
        val description = root["description"]?.jsonPrimitive?.contentOrNull ?: return null
        val deviceClass = root["deviceClass"]?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            ?: return null
        return ConfigDocumentHeader(schemaVersion, id, name, description, deviceClass)
    }
}
