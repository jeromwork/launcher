package com.launcher.api.action

/**
 * Port (CLAUDE.md §1) for executing a domain [Action] against the host platform.
 *
 * Real implementations live in platform source-sets (`androidMain`,
 * eventually `iosMain`). Domain code (commonMain) and tests reference only
 * this interface — never the implementation type.
 *
 * Test doubles: [`FakeActionDispatcher`](commonTest) records calls and
 * returns programmable [DispatchResult]s.
 *
 * Behavioural contract per spec 005 §7.1:
 *  1. Reject unsupported [Action.schemaVersion] with [DispatchResult.Failure].
 *  2. Unknown [Action.providerId] -> [DispatchResult.ProviderUnavailable] with
 *     [UnavailabilityHint.UnknownInThisVersion]; recurse into fallback if any.
 *  3. Honour [Action.fallback] up to [Action.MAX_FALLBACK_DEPTH] depth on
 *     `Missing` / `NotApplicable` / handler [DispatchResult.Failure].
 *  4. Emit exactly one `ProjectEvent.ActionDispatched` per top-level call
 *     (not per fallback recursion). See contracts/diagnostics-events-v2.md.
 */
interface ActionDispatcher {
    suspend fun dispatch(action: Action): DispatchResult
}
