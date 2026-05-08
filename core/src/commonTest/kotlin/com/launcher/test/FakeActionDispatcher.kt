package com.launcher.test

import com.launcher.api.action.Action
import com.launcher.api.action.ActionDispatcher
import com.launcher.api.action.DispatchResult

/**
 * In-memory test double for [ActionDispatcher]. Records every dispatch call
 * and returns programmable [DispatchResult]s. Used by:
 *  - UI tests of `FlowScreen` (verify correct [Action] sent on tile tap).
 *  - Wizard tests where dispatch isn't the focus.
 *  - Integration tests that need a deterministic end-of-chain.
 *
 * Per CLAUDE.md §6 (mock-first), every port should ship with a fake.
 *
 * Not thread-safe — intended for single-threaded test contexts (which match
 * `runTest` and `createComposeRule.runOnUiThread`).
 */
class FakeActionDispatcher(
    /** Default result for any action whose providerId isn't pinned via [pin]. */
    var defaultResult: DispatchResult = DispatchResult.Ok,
) : ActionDispatcher {

    private val pinned: MutableMap<String, DispatchResult> = mutableMapOf()
    private val _calls: MutableList<Action> = mutableListOf()

    /** Every action passed to [dispatch] in order received, including fallback recursion if any. */
    val calls: List<Action> get() = _calls.toList()

    /** Top-level (first-call) actions only. */
    val topLevelCalls: List<Action> get() = _calls.toList()

    fun reset() {
        _calls.clear()
        pinned.clear()
        defaultResult = DispatchResult.Ok
    }

    /** Pin a specific [DispatchResult] for any action whose providerId.value matches. */
    fun pin(providerIdValue: String, result: DispatchResult) {
        pinned[providerIdValue] = result
    }

    override suspend fun dispatch(action: Action): DispatchResult {
        _calls.add(action)
        return pinned[action.providerId.value] ?: defaultResult
    }
}
