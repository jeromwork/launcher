package com.launcher.preset.roundtrip

import com.launcher.preset.model.ActiveComponentEntry
import com.launcher.preset.model.Component
import com.launcher.preset.model.ComponentDeclaration
import com.launcher.preset.model.Pool
import com.launcher.preset.model.Preset
import com.launcher.preset.model.Sensitivity
import com.launcher.preset.model.SettingsMapEntry
import com.launcher.preset.model.WizardBehavior
import com.launcher.preset.model.WizardFlowEntry
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal fun mvpPool(): Pool = Pool(
    schemaVersion = 1,
    declarations = listOf(
        ComponentDeclaration(
            id = "font-tile",
            component = Component.FontSize(scale = 1.6f),
            wizardBehavior = WizardBehavior.Interactive,
            critical = false,
            descriptionKey = "pool.font.description",
        ),
        ComponentDeclaration(
            id = "tile-whatsapp",
            component = Component.AppTile(
                packageName = "com.whatsapp",
                labelKey = "pool.tile.whatsapp.label",
                iconKey = "pool.tile.whatsapp.icon",
                pinProtected = false,
            ),
            wizardBehavior = WizardBehavior.AutoApply,
            critical = false,
            descriptionKey = "pool.tile.whatsapp.description",
        ),
        ComponentDeclaration(
            id = "sos-main",
            component = Component.Sos(shareLocation = true, autoAnswer = true),
            wizardBehavior = WizardBehavior.Interactive,
            critical = true,
            descriptionKey = "pool.sos.description",
        ),
        ComponentDeclaration(
            id = "toolbar-minimal",
            component = Component.Toolbar(
                items = listOf("call", "sos", "clock"),
                layoutKey = "layout.toolbar.minimal",
            ),
            wizardBehavior = WizardBehavior.InitialDefault,
            critical = false,
            descriptionKey = "pool.toolbar.description",
        ),
    ),
)

internal fun simpleLauncherPreset(): Preset = Preset(
    schemaVersion = 2,
    presetId = "simple-launcher",
    version = 1,
    layoutKey = "layout.grid.2x3",
    wizardFlow = listOf(
        WizardFlowEntry(
            poolRef = "font-tile", order = 1,
            wizardTitleKey = "wizard.font.title",
            wizardIntroKey = "wizard.font.intro",
            behavior = WizardBehavior.Interactive,
            paramsOverride = JsonObject(mapOf("scale" to JsonPrimitive(1.6f))),
        ),
        WizardFlowEntry(
            poolRef = "sos-main", order = 2,
            wizardTitleKey = "wizard.sos.title",
            behavior = WizardBehavior.Interactive,
        ),
        WizardFlowEntry(
            poolRef = "tile-whatsapp", order = 3,
            wizardTitleKey = "wizard.apptile.title",
            behavior = WizardBehavior.AutoApply,
        ),
        WizardFlowEntry(
            poolRef = "toolbar-minimal", order = 4,
            wizardTitleKey = "wizard.toolbar.title",
            behavior = WizardBehavior.InitialDefault,
        ),
    ),
    settingsMap = listOf(
        SettingsMapEntry("font-tile", "settings.category.vision", sensitivity = Sensitivity.Normal),
        SettingsMapEntry("sos-main", "settings.category.safety", sensitivity = Sensitivity.High),
        SettingsMapEntry("tile-whatsapp", "settings.category.apps", sensitivity = Sensitivity.Normal),
        SettingsMapEntry("toolbar-minimal", "settings.category.layout", sensitivity = Sensitivity.Admin),
    ),
    activeComponents = listOf(
        ActiveComponentEntry("font-tile", JsonObject(mapOf("scale" to JsonPrimitive(1.6f)))),
        ActiveComponentEntry("sos-main"),
        ActiveComponentEntry("tile-whatsapp"),
        ActiveComponentEntry("toolbar-minimal"),
    ),
)

internal fun launcherPreset(): Preset = simpleLauncherPreset().copy(
    presetId = "launcher",
    wizardFlow = simpleLauncherPreset().wizardFlow.map {
        if (it.poolRef == "font-tile") it.copy(paramsOverride = JsonObject(mapOf("scale" to JsonPrimitive(1.2f))))
        else it
    },
    activeComponents = simpleLauncherPreset().activeComponents.map {
        if (it.poolRef == "font-tile") it.copy(paramsOverride = JsonObject(mapOf("scale" to JsonPrimitive(1.2f))))
        else it
    },
)

internal fun workspacePreset(): Preset = Preset(
    schemaVersion = 2,
    presetId = "workspace",
    version = 1,
    layoutKey = "layout.grid.2x3",
    wizardFlow = listOf(
        WizardFlowEntry(
            poolRef = "toolbar-minimal", order = 1,
            wizardTitleKey = "wizard.toolbar.title",
            behavior = WizardBehavior.AutoApply,
        ),
        WizardFlowEntry(
            poolRef = "sos-main", order = 2,
            wizardTitleKey = "wizard.sos.title",
            behavior = WizardBehavior.AutoApply,
        ),
    ),
    settingsMap = listOf(
        SettingsMapEntry("toolbar-minimal", "settings.category.layout", sensitivity = Sensitivity.Admin),
        SettingsMapEntry("sos-main", "settings.category.safety", sensitivity = Sensitivity.High),
    ),
    activeComponents = listOf(
        ActiveComponentEntry("toolbar-minimal"),
        ActiveComponentEntry("sos-main"),
    ),
)
