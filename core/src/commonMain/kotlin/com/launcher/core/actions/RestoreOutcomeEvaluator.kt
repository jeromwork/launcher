package com.launcher.core.actions

import com.launcher.api.ReturnContextRecord
import com.launcher.api.ReturnRestoreOutcome

class RestoreOutcomeEvaluator(
    private val knownHomeSurfaceRef: String,
) {
    fun evaluate(record: ReturnContextRecord?): ReturnRestoreOutcome {
        if (record == null) return ReturnRestoreOutcome.NO_VALID_CONTEXT
        if (record.homeSurfaceRef == knownHomeSurfaceRef) {
            return ReturnRestoreOutcome.RESTORED_EXACT_HOME
        }
        return ReturnRestoreOutcome.RESTORED_NEAREST_STABLE_HOME
    }
}

