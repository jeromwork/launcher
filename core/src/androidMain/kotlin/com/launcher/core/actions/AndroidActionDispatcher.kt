package com.launcher.core.actions

import com.launcher.api.ProjectEvent
import com.launcher.api.action.Action
import com.launcher.api.action.ActionDispatcher
import com.launcher.api.action.DispatchResult
import com.launcher.api.action.ProviderAvailability
import com.launcher.api.action.ProviderId
import com.launcher.api.action.ProviderRegistry
import com.launcher.api.action.UnavailabilityHint
import com.launcher.core.actions.handlers.ActionHandler
import com.launcher.core.actions.handlers.HandlerContext
import com.launcher.core.events.EventRouter
import family.wire.CorruptWireFormatException
import family.wire.UnknownWireVersionException
import family.wire.accessFor

/**
 * Default Android implementation of [ActionDispatcher] per spec 005 §7.1.
 *
 * Algorithm (verbatim from spec):
 *  1. Apply the version header gate (`docs/architecture/wire-format.md` §3); refusal → `Failure`.
 *  2. Look up handler by `providerId`. Missing → `ProviderUnavailable(UnknownInThisVersion)`,
 *     recurse into fallback if any.
 *  3. Probe `providerRegistry.availability(providerId)`. `Missing`/`NotApplicable`
 *     → recurse into fallback if any, else surface as `ProviderUnavailable(hint)`.
 *  4. Invoke handler. If handler returns `Failure` and fallback exists → recurse.
 *  5. Emit exactly one `ProjectEvent.ActionDispatched` event for the **top-level**
 *     dispatch call (not per fallback recursion). Done by [dispatch] entry point;
 *     internal recursion goes through [dispatchInternal].
 *
 * Fallback depth is bounded by [Action.MAX_FALLBACK_DEPTH]. Going deeper returns
 * `Failure("fallback chain too deep")`.
 *
 * **Threading**: handlers run on the caller's coroutine context. Activity-start
 * is fast; no IO assumed. The dispatcher itself does no blocking work.
 */
class AndroidActionDispatcher(
    private val handlers: Map<ProviderId, ActionHandler>,
    private val providerRegistry: ProviderRegistry,
    private val eventRouter: EventRouter,
    private val handlerContext: HandlerContext,
    private val timeSource: () -> Long = { System.currentTimeMillis() },
) : ActionDispatcher {

    override suspend fun dispatch(action: Action): DispatchResult {
        var fallbackUsed = false
        val result = dispatchInternal(action, depth = 0) { fallbackUsed = true }
        eventRouter.emit(
            ProjectEvent.ActionDispatched(
                providerId = action.providerId,
                resultKind = result.toResultKind(),
                fallbackUsed = fallbackUsed,
                timestampMs = timeSource(),
            )
        )
        return result
    }

    private suspend fun dispatchInternal(
        action: Action,
        depth: Int,
        onFallback: () -> Unit,
    ): DispatchResult {
        if (depth > Action.MAX_FALLBACK_DEPTH) {
            return DispatchResult.Failure("fallback chain too deep")
        }

        // Step 1: version header gate (wire-format.md §3). Note this is NOT a schemaVersion
        // comparison: a document written by a newer build dispatches fine unless it declares that
        // it needs a newer reader. READ_ONLY is irrelevant here — dispatch never writes back.
        try {
            action.accessFor(Action.SCHEMA_VERSION)
        } catch (e: UnknownWireVersionException) {
            return DispatchResult.Failure("unsupported wire version: ${e.message}")
        } catch (e: CorruptWireFormatException) {
            return DispatchResult.Failure("corrupt wire version header: ${e.message}")
        }

        // Step 2: handler lookup. Unknown providerId → fall back if possible.
        val handler = handlers[action.providerId]
        if (handler == null) {
            return tryFallback(
                action,
                depth,
                onFallback,
                fallbackResult = {
                    DispatchResult.ProviderUnavailable(
                        action.providerId,
                        UnavailabilityHint.UnknownInThisVersion,
                    )
                },
            )
        }

        // Step 3: availability probe.
        val availability = providerRegistry.availability(action.providerId)
        if (availability !is ProviderAvailability.Available) {
            val hint = when (availability) {
                is ProviderAvailability.Missing       -> UnavailabilityHint.Missing
                is ProviderAvailability.NotApplicable -> UnavailabilityHint.NotApplicable
                ProviderAvailability.Available        -> error("unreachable")
            }
            return tryFallback(
                action,
                depth,
                onFallback,
                fallbackResult = { DispatchResult.ProviderUnavailable(action.providerId, hint) },
            )
        }

        // Step 4: invoke handler. Failure → fall back if possible.
        val result = runCatching { handler.handle(action, handlerContext) }
            .getOrElse { t -> DispatchResult.Failure(t.message ?: t::class.simpleName ?: "unknown") }

        return if (result is DispatchResult.Failure && action.fallback != null) {
            onFallback()
            dispatchInternal(action.fallback, depth + 1, onFallback)
        } else {
            result
        }
    }

    private suspend fun tryFallback(
        action: Action,
        depth: Int,
        onFallback: () -> Unit,
        fallbackResult: () -> DispatchResult,
    ): DispatchResult {
        return if (action.fallback != null) {
            onFallback()
            dispatchInternal(action.fallback, depth + 1, onFallback)
        } else {
            fallbackResult()
        }
    }

    private fun DispatchResult.toResultKind(): ProjectEvent.ActionDispatched.ResultKind = when (this) {
        is DispatchResult.Ok                 -> ProjectEvent.ActionDispatched.ResultKind.Ok
        is DispatchResult.BlockedByPolicy    -> ProjectEvent.ActionDispatched.ResultKind.BlockedByPolicy
        is DispatchResult.ProviderUnavailable -> ProjectEvent.ActionDispatched.ResultKind.ProviderUnavailable
        is DispatchResult.Failure            -> ProjectEvent.ActionDispatched.ResultKind.Failure
    }
}
