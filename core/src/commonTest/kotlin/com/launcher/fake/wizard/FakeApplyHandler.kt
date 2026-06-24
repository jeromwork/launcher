package com.launcher.fake.wizard

import com.launcher.api.wizard.ApplyResult
import com.launcher.api.wizard.data.ApplySpec
import com.launcher.api.wizard.handlers.ApplyHandler

/**
 * Symbol-replay [ApplyHandler] for tests. Defaults to
 * [ApplyResult.PromptShown] — matches the contract for handlers that
 * defer to a system dialog.
 */
class FakeApplyHandler(
    private val outcomes: Map<ApplySpec, ApplyResult> = emptyMap(),
    private val default: ApplyResult = ApplyResult.PromptShown,
) : ApplyHandler {
    private val invocations = mutableListOf<ApplySpec>()

    override suspend fun apply(spec: ApplySpec): ApplyResult {
        invocations += spec
        return outcomes[spec] ?: default
    }

    fun recordedCalls(): List<ApplySpec> = invocations.toList()
}
