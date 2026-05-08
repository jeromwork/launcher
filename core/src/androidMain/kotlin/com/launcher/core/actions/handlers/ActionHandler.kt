package com.launcher.core.actions.handlers

import android.content.Context
import android.content.pm.PackageManager
import com.launcher.api.action.Action
import com.launcher.api.action.DispatchResult
import com.launcher.core.events.EventRouter

/**
 * Per-provider plug-in invoked by `AndroidActionDispatcher` after the dispatcher
 * has validated `schemaVersion`, resolved availability, and decided that this
 * handler should run (no fallback recursion at this point — the dispatcher
 * unwinds fallbacks itself).
 *
 * Handler responsibility:
 *  - Build the `Intent` (or any platform-side effect) for the given [Action.payload].
 *  - Start the activity / service / etc. with `FLAG_ACTIVITY_NEW_TASK`.
 *  - Translate the platform outcome into a [DispatchResult] (Ok / Failure).
 *
 * Handler MUST NOT:
 *  - Recurse into [Action.fallback] — the dispatcher owns that.
 *  - Emit [com.launcher.api.ProjectEvent.ActionDispatched] — the dispatcher owns that.
 *  - Read `availability` again — the dispatcher already resolved it.
 *
 * Handlers are stateless — single instance per app process, shared across
 * threads. Any required cache (e.g. `MockContactsRepository`) is injected via
 * the constructor.
 */
interface ActionHandler {
    suspend fun handle(action: Action, ctx: HandlerContext): DispatchResult
}

/**
 * Cross-cutting platform handles passed to every [ActionHandler] invocation.
 * Fields stay narrow — adding a new field is a port-shape change, so prefer
 * injecting a domain port into a specific handler over widening this struct.
 */
data class HandlerContext(
    val context: Context,
    val packageManager: PackageManager,
    val eventRouter: EventRouter,
)
