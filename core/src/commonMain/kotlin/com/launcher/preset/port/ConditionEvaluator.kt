package com.launcher.preset.port

import com.launcher.preset.model.ProfileState
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

interface ConditionEvaluator {
    /**
     * Evaluate a `visibleIf` JsonLogic-style expression against ProfileState.
     * MVP supports only `{"var": "profile.state.<flag>"}`.
     * Unknown expressions return false and log Unsupported.
     */
    fun evaluate(expr: JsonElement?, state: ProfileState): Boolean
}

/**
 * MVP adapter — only handles `{"var": "profile.state.<flag>"}`.
 * Full JsonLogic runtime is a deferred seam per plan.md.
 */
class ProfileStateConditionEvaluator : ConditionEvaluator {

    override fun evaluate(expr: JsonElement?, state: ProfileState): Boolean {
        if (expr == null) return true
        val obj = expr as? JsonObject ?: return false
        val varExpr = obj["var"] ?: return false
        val path = (varExpr as? JsonPrimitive)?.contentOrNullSafe() ?: return false
        if (!path.startsWith(PROFILE_STATE_PREFIX)) return false
        val flag = path.removePrefix(PROFILE_STATE_PREFIX)
        val value = state.opaque[flag] ?: return false
        return when (value) {
            is JsonPrimitive -> value.booleanOrNull() ?: (value.contentOrNullSafe() != null)
            else -> true
        }
    }

    private fun JsonPrimitive.contentOrNullSafe(): String? = if (isString) content else content
    private fun JsonPrimitive.booleanOrNull(): Boolean? = runCatching { boolean }.getOrNull()

    companion object {
        private const val PROFILE_STATE_PREFIX = "profile.state."
    }
}
