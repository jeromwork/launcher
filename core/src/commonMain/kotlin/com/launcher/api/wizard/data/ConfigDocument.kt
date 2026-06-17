package com.launcher.api.wizard.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Common 6-field header for every bundled F-3 wire format (per glossary §4.1).
 *
 * Forward-compat policy (per FR-015): unknown additive fields silently
 * ignored. Hard-fail policy (per FR-016): `schemaVersion > known` returns
 * `IncompatibleVersion`.
 */
@Serializable
data class ConfigDocumentHeader(
    val schemaVersion: Int,
    val id: String,
    val name: String,
    val description: String,
    val deviceClass: List<String>,
)

/** Sealed family of decoded wire-format payloads. */
sealed class ConfigDocument {
    abstract val header: ConfigDocumentHeader

    data class Manifest(
        override val header: ConfigDocumentHeader,
        val body: WizardManifestBody,
    ) : ConfigDocument()

    data class Layout(
        override val header: ConfigDocumentHeader,
        val body: ScreenLayoutBody,
    ) : ConfigDocument()

    data class TileSetDoc(
        override val header: ConfigDocumentHeader,
        val body: TileSetBody,
    ) : ConfigDocument()

    data class SystemSettingsPoolDoc(
        override val header: ConfigDocumentHeader,
        val body: SystemSettingsPoolBody,
    ) : ConfigDocument()

    data class UICustomizationPoolDoc(
        override val header: ConfigDocumentHeader,
        val body: UICustomizationPoolBody,
    ) : ConfigDocument()
}

/**
 * Thin reference passed back in WizardOutcome.Completed. Carries the
 * captured answers map keyed by the wizard step refId (e.g. "tileSet",
 * "language"). Consumer (S-1+) translates to a spec-008 ConfigDocument.
 *
 * Kept out of the spec-008 ConfigDocument type to avoid pulling its wire
 * format into the domain.
 */
data class ConfigDocumentRef(
    val tileSetId: String?,
    val screenLayoutId: String?,
    val answers: Map<String, JsonElement>,
) {
    companion object {
        val EMPTY = ConfigDocumentRef(
            tileSetId = null,
            screenLayoutId = null,
            answers = emptyMap(),
        )
    }
}

/** Wrap raw JsonObject body when downstream needs unparsed access. */
internal fun JsonObject.asBody(): JsonObject = this
