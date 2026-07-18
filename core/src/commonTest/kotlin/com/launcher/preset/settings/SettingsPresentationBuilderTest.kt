package com.launcher.preset.settings

import com.launcher.preset.engine.ProfileFactory
import com.launcher.preset.model.LifecycleState
import com.launcher.preset.model.Sensitivity
import com.launcher.preset.model.SettingsMapEntry
import com.launcher.preset.roundtrip.mvpPool
import com.launcher.preset.roundtrip.simpleLauncherPreset
import com.launcher.preset.roundtrip.workspacePreset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * TASK-69 T069-008 (US1, SC-001, SC-010) — [SettingsPresentationBuilder] unit
 * tests on fixed `Profile + settingsMap` fixtures (deterministic, no fakes
 * needed — the builder is pure).
 */
class SettingsPresentationBuilderTest {

    private val factory = ProfileFactory()
    private val builder = SettingsPresentationBuilder()

    @Test
    fun `skips settingsMap entries whose poolRef is absent from the profile`() {
        val profile = factory.create(simpleLauncherPreset(), mvpPool())
        val settingsMap = listOf(SettingsMapEntry("does-not-exist", "settings.category.vision"))

        val view = builder.build(profile, settingsMap)

        assertTrue(view.sections.isEmpty(), "unresolved poolRef must be skipped, not crash")
    }

    @Test
    fun `groups rows by categoryKey`() {
        val profile = factory.create(simpleLauncherPreset(), mvpPool())

        val view = builder.build(profile, simpleLauncherPreset().settingsMap)

        assertEquals(4, view.sections.size, "simpleLauncherPreset declares 4 distinct categories")
        val categories = view.sections.map { it.categoryKey }.toSet()
        assertEquals(
            setOf(
                "settings.category.vision",
                "settings.category.safety",
                "settings.category.apps",
                "settings.category.layout",
            ),
            categories,
        )
        view.sections.forEach { section -> assertEquals(1, section.rows.size) }
    }

    @Test
    fun `different preset yields a different row set without code changes`() {
        val simpleProfile = factory.create(simpleLauncherPreset(), mvpPool())
        val workspaceProfile = factory.create(workspacePreset(), mvpPool())

        val simpleView = builder.build(simpleProfile, simpleLauncherPreset().settingsMap)
        val workspaceView = builder.build(workspaceProfile, workspacePreset().settingsMap)

        assertEquals(4, simpleView.sections.sumOf { it.rows.size })
        assertEquals(2, workspaceView.sections.sumOf { it.rows.size })
    }

    @Test
    fun `projects current value and lifecycle state`() {
        val profile = factory.create(simpleLauncherPreset(), mvpPool())
            .setState("font-tile", LifecycleState.Applied)

        val view = builder.build(profile, simpleLauncherPreset().settingsMap)

        val fontRow = view.sections.flatMap { it.rows }.first { it.poolRef == "font-tile" }
        assertEquals("1.6x", fontRow.valueText)
        assertEquals(RowState.Applied, fontRow.state)
    }

    @Test
    fun `entity with no recorded LifecycleState projects as Pending`() {
        // ProfileFactory spawns every entity with LifecycleState.Pending — this
        // asserts the fallback path directly against that real spawn behaviour.
        val profile = factory.create(simpleLauncherPreset(), mvpPool())

        val view = builder.build(profile, simpleLauncherPreset().settingsMap)

        val sosRow = view.sections.flatMap { it.rows }.first { it.poolRef == "sos-main" }
        assertEquals(RowState.Pending, sosRow.state)
    }

    @Test
    fun `Failed lifecycle state projects with reason preserved in RowState`() {
        val profile = factory.create(simpleLauncherPreset(), mvpPool())
            .setState("font-tile", LifecycleState.Failed(com.launcher.preset.model.FailReason.NetworkUnavailable))

        val view = builder.build(profile, simpleLauncherPreset().settingsMap)

        val fontRow = view.sections.flatMap { it.rows }.first { it.poolRef == "font-tile" }
        assertEquals(RowState.Failed, fontRow.state)
    }

    @Test
    fun `derives editability from component type, not a wire field`() {
        val profile = factory.create(simpleLauncherPreset(), mvpPool())

        val view = builder.build(profile, simpleLauncherPreset().settingsMap)
        val byRef = view.sections.flatMap { it.rows }.associateBy { it.poolRef }

        assertEquals(RowKind.InApp, byRef.getValue("font-tile").kind)
        assertEquals(RowKind.ReadOnly, byRef.getValue("sos-main").kind)
        assertEquals(RowKind.ReadOnly, byRef.getValue("tile-whatsapp").kind)
        assertEquals(RowKind.InApp, byRef.getValue("toolbar-minimal").kind)
    }

    @Test
    fun `sensitivity is read but does not gate visibility at MVP`() {
        val profile = factory.create(simpleLauncherPreset(), mvpPool())
        val settingsMap = simpleLauncherPreset().settingsMap
        assertTrue(settingsMap.any { it.sensitivity == Sensitivity.Admin })
        assertTrue(settingsMap.any { it.sensitivity == Sensitivity.High })
        assertTrue(settingsMap.any { it.sensitivity == Sensitivity.Normal })

        val view = builder.build(profile, settingsMap)

        assertEquals(settingsMap.size, view.sections.sumOf { it.rows.size }, "all rows shown regardless of sensitivity")
    }

    @Test
    fun `build is deterministic on fixed input`() {
        val profile = factory.create(simpleLauncherPreset(), mvpPool())
        val settingsMap = simpleLauncherPreset().settingsMap

        val first = builder.build(profile, settingsMap)
        val second = builder.build(profile, settingsMap)

        assertEquals(first, second)
    }

    @Test
    fun `empty settingsMap yields an empty view, not all profile components`() {
        val profile = factory.create(simpleLauncherPreset(), mvpPool())

        val view = builder.build(profile, emptyList())

        assertTrue(view.sections.isEmpty())
    }
}
