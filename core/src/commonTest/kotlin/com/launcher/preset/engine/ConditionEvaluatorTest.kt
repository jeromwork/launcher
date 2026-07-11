package com.launcher.preset.engine

import com.launcher.preset.model.ProfileState
import com.launcher.preset.port.ProfileStateConditionEvaluator
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConditionEvaluatorTest {

    private val eval = ProfileStateConditionEvaluator()

    @Test
    fun nullExpr_evaluatesTrue() {
        assertTrue(eval.evaluate(null, ProfileState()))
    }

    @Test
    fun varReferencingActiveFlag_evaluatesTrue() {
        val state = ProfileState(
            opaque = JsonObject(mapOf("cloud" to JsonPrimitive(true)))
        )
        val expr = buildJsonObject { put("var", JsonPrimitive("profile.state.cloud")) }
        assertTrue(eval.evaluate(expr, state))
    }

    @Test
    fun varReferencingMissingFlag_evaluatesFalse() {
        val state = ProfileState()
        val expr = buildJsonObject { put("var", JsonPrimitive("profile.state.cloud")) }
        assertFalse(eval.evaluate(expr, state))
    }

    @Test
    fun unknownOperator_evaluatesFalse() {
        val expr = buildJsonObject { put("and", JsonPrimitive("x")) }
        assertFalse(eval.evaluate(expr, ProfileState()))
    }
}
