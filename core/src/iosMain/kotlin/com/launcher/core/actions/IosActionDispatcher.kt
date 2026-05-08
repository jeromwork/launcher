package com.launcher.core.actions

import com.launcher.api.action.Action
import com.launcher.api.action.ActionDispatcher
import com.launcher.api.action.DispatchResult

/**
 * iOS implementation of [ActionDispatcher] — placeholder stub.
 *
 * Spec 005 is **Documented Platform Asymmetry** per ADR-005 §3.117 and spec
 * §7.7 / §10.1: the action architecture is fully specified for Android in
 * v1.0.0; iOS adoption ships in a later spec when the iOS app gains a UI.
 *
 * This stub exists so that:
 *  - the `:core:iosMain` source-set compiles (required by KMP wiring),
 *  - DI configurations on iOS can fail loud at startup if anything tries to
 *    actually dispatch — rather than silently no-op'ing.
 */
class IosActionDispatcher : ActionDispatcher {
    override suspend fun dispatch(action: Action): DispatchResult =
        TODO("iOS dispatch not implemented in spec 005; tracked for a follow-up spec")
}
