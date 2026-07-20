package com.launcher.preset.wire

import family.wire.WireVersion

import com.launcher.preset.model.Component
import com.launcher.preset.model.Blueprint
import com.launcher.preset.model.Pool
import com.launcher.preset.model.ShapeStyle
import com.launcher.preset.model.TypographyScale
import com.launcher.preset.model.WizardBehavior
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * T023 (FR-014, contracts/pool-schema-v2.md, CLAUDE.md rule 5).
 *
 * Roundtrip: encode Pool where declarations carry `requires` + `required` → decode
 * → assertEquals. Guards the v2 pool wire format.
 */
class PoolSchemaV2RoundtripTest {

    private val json = Json {
        classDiscriminator = "type"
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    @Test
    fun v2Pool_withRequiresAndRequired_roundtrips() {
        val pool = Pool(
            schemaVersion = WireVersion(2, 0),
            declarations = listOf(
                Blueprint(
                    id = "launcher-role",
                    components = listOf(Component.LauncherRole),
                    wizardBehavior = WizardBehavior.AutoApply,
                    critical = true,
                    descriptionKey = "pool_launcher_role_desc",
                    requires = null,
                    required = true,
                ),
                Blueprint(
                    id = "app-tile-whatsapp",
                    components = listOf(
                        Component.AppTile(
                            packageName = "com.whatsapp",
                            labelKey = "app_whatsapp_label",
                        ),
                    ),
                    wizardBehavior = WizardBehavior.AutoApply,
                    critical = false,
                    requires = listOf("launcher-role"),
                    required = false,
                ),
                Blueprint(
                    id = "theme-warm",
                    components = listOf(
                        Component.Theme(
                            paletteSeedHex = "#FF7043",
                            typographyScale = TypographyScale.Large,
                            shapeStyle = ShapeStyle.Rounded,
                            darkMode = false,
                        ),
                    ),
                    wizardBehavior = WizardBehavior.AutoApply,
                    requires = null,
                    required = false,
                ),
                Blueprint(
                    id = "language-system",
                    components = listOf(Component.Language(locale = "system")),
                    wizardBehavior = WizardBehavior.Interactive,
                ),
                Blueprint(
                    id = "status-bar-hidden",
                    components = listOf(Component.StatusBarPolicy),
                    wizardBehavior = WizardBehavior.AutoApply,
                    requires = listOf("launcher-role"),
                    required = false,
                ),
            ),
        )

        val encoded = json.encodeToString(Pool.serializer(), pool)
        val decoded = json.decodeFromString(Pool.serializer(), encoded)

        assertEquals(pool, decoded)
        assertEquals(WireVersion(2, 0), decoded.schemaVersion)

        val whatsapp = decoded.byId("app-tile-whatsapp")
        assertEquals(listOf("launcher-role"), whatsapp?.requires)
        assertEquals(false, whatsapp?.required)

        val role = decoded.byId("launcher-role")
        assertEquals(null, role?.requires)
        assertEquals(true, role?.required)

        // Bit-identical re-encode.
        val reencoded = json.encodeToString(Pool.serializer(), decoded)
        assertEquals(encoded, reencoded)
    }
}
