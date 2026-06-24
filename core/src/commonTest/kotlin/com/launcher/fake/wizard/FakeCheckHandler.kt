package com.launcher.fake.wizard

import com.launcher.api.wizard.SettingStatus
import com.launcher.api.wizard.data.CheckSpec
import com.launcher.api.wizard.handlers.CheckHandler

/**
 * Symbol-replay [CheckHandler] for tests (CLAUDE.md rule 6 mock-first).
 * Returns [SettingStatus.Indeterminate] for unknown specs — graceful
 * default matching the engine's contract for missing handlers.
 */
class FakeCheckHandler(
    private val responses: Map<CheckSpec, SettingStatus> = emptyMap(),
    private val default: SettingStatus = SettingStatus.Indeterminate,
) : CheckHandler {
    private val invocations = mutableListOf<CheckSpec>()

    override suspend fun check(spec: CheckSpec): SettingStatus {
        invocations += spec
        return responses[spec] ?: default
    }

    fun recordedCalls(): List<CheckSpec> = invocations.toList()
}
