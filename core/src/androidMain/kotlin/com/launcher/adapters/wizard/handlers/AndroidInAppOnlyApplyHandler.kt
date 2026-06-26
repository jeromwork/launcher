package com.launcher.adapters.wizard.handlers

import com.launcher.api.wizard.ApplyResult
import com.launcher.api.wizard.data.ApplySpec
import com.launcher.api.wizard.handlers.ApplyHandler

/**
 * `ApplySpec.InAppOnly` → no-op apply. Caller surfaces an in-app toggle
 * elsewhere; this handler only signals that the prompt is "shown" so
 * the wizard does not error.
 */
class AndroidInAppOnlyApplyHandler : ApplyHandler {
    override suspend fun apply(spec: ApplySpec): ApplyResult {
        if (spec !is ApplySpec.InAppOnly) return ApplyResult.UnsupportedMechanism
        return ApplyResult.PromptShown
    }
}
