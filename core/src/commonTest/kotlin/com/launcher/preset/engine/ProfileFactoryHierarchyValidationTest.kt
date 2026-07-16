package com.launcher.preset.engine

import com.launcher.preset.model.Component
import com.launcher.preset.model.Entity
import com.launcher.preset.model.Profile
import com.launcher.preset.model.ValidationError
import com.launcher.preset.model.WizardBehavior
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * T127-019 (FR-016, SC-012, US-4 AS-4) — a broken tree must fail with a named
 * error at assembly, not render half a screen at runtime.
 */
class ProfileFactoryHierarchyValidationTest {

    private val factory = ProfileFactory()

    private fun entity(id: String, component: Component, parentId: String? = null) = Entity(
        id = id,
        component = component,
        wizardBehavior = WizardBehavior.AutoApply,
        critical = false,
        parentId = parentId,
    )

    private fun profileOf(vararg entities: Entity) = Profile(
        basedOnPreset = "p",
        presetVersion = 2,
        layoutKey = "grid",
        components = entities.toList(),
    )

    @Test
    fun validHierarchy_passesClean() {
        val profile = profileOf(
            entity("ws", Component.Workspace()),
            entity("flow-a", Component.Flow(titleKey = "a"), parentId = "ws"),
            entity("tile", Component.AppTile("com.a", "l"), parentId = "flow-a"),
            entity("toolbar", Component.Toolbar(layoutKey = "bottom"), parentId = "ws"),
            entity(
                "btn",
                Component.ToolbarButton(targetFlowId = "flow-a", labelKey = "b"),
                parentId = "toolbar",
            ),
        )

        assertEquals(emptyList(), factory.validateHierarchy(profile))
    }

    @Test
    fun flatProfile_withoutHierarchy_passesClean() {
        // Simple launcher (US-1): every entity a root. Degenerate, but valid.
        val profile = profileOf(
            entity("tile-1", Component.AppTile("com.a", "l")),
            entity("tile-2", Component.AppTile("com.b", "l")),
        )

        assertEquals(emptyList(), factory.validateHierarchy(profile))
    }

    @Test
    fun danglingParentRef_isReported() {
        val profile = profileOf(
            entity("tile", Component.AppTile("com.a", "l"), parentId = "flow-that-vanished"),
        )

        val errors = factory.validateHierarchy(profile)

        assertEquals(
            listOf(ValidationError.DanglingParentRef("tile", "flow-that-vanished")),
            errors,
        )
    }

    @Test
    fun circularParentRef_isReported_andTerminates() {
        val profile = profileOf(
            entity("a", Component.Workspace(), parentId = "b"),
            entity("b", Component.Workspace(), parentId = "a"),
        )

        val errors = factory.validateHierarchy(profile)

        assertTrue(errors.any { it is ValidationError.CircularParentRef }, "expected a cycle error, got $errors")
    }

    @Test
    fun selfParentingEntity_isReportedAsCycle() {
        val profile = profileOf(
            entity("loop", Component.Workspace(), parentId = "loop"),
        )

        val errors = factory.validateHierarchy(profile)

        assertTrue(errors.any { it is ValidationError.CircularParentRef }, "expected a cycle error, got $errors")
    }

    @Test
    fun danglingTargetRef_isReported_whenButtonPointsNowhere() {
        val profile = profileOf(
            entity("ws", Component.Workspace()),
            entity("toolbar", Component.Toolbar(layoutKey = "bottom"), parentId = "ws"),
            entity(
                "btn",
                Component.ToolbarButton(targetFlowId = "flow-missing", labelKey = "b"),
                parentId = "toolbar",
            ),
        )

        val errors = factory.validateHierarchy(profile)

        assertEquals(listOf(ValidationError.DanglingTargetRef("btn", "flow-missing")), errors)
    }

    @Test
    fun errorsCarryI18nKeys() {
        assertEquals(
            "validator.error.dangling_parent_ref",
            ValidationError.DanglingParentRef("a", "b").toI18nKey(),
        )
        assertEquals(
            "validator.error.circular_parent_ref",
            ValidationError.CircularParentRef(listOf("a")).toI18nKey(),
        )
        assertEquals(
            "validator.error.dangling_target_ref",
            ValidationError.DanglingTargetRef("a", "b").toI18nKey(),
        )
    }
}
