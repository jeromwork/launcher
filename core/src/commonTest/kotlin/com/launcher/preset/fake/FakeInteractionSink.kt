package com.launcher.preset.fake

import com.launcher.preset.model.Component
import com.launcher.preset.model.Entity
import com.launcher.preset.model.LifecycleState
import com.launcher.preset.port.InteractionSink

/**
 * T028 — enhanced fake for TASK-126 wizard runtime tests (US-1 / US-2 scaffold).
 *
 * Auto-answers each `Interactive` step with a configurable response and records call
 * order for assertions. Builder pattern: preload responses keyed by Entity.id,
 * then inspect [callLog] after the engine drains.
 *
 * Rule 6 (mock-first): domain and reconcile-engine tests exercise flows without any
 * real UI wiring; production `AndroidInteractionSink` lives under `app/` and is not
 * touched by commonTest.
 *
 * NOTE: an earlier lightweight fake exists at `com.launcher.preset.fakes.FakeInteractionSink`
 * (TASK-120 tree, plural package). This one adds the call log and the builder DSL that
 * upcoming T057-T062 tests will rely on.
 */
class FakeInteractionSink private constructor(
    private val responses: Map<String, Component?>,
    private val fallback: (Entity) -> Component?,
) : InteractionSink {

    private val _callLog: MutableList<String> = mutableListOf()

    /** IDs of Entities asked, in the order the engine invoked [askUser]. */
    val callLog: List<String> get() = _callLog.toList()

    override suspend fun askUser(component: Entity): Component? {
        _callLog += component.id
        return if (responses.containsKey(component.id)) responses[component.id]
        else fallback(component)
    }

    /** Reset the recorded call log between test phases. */
    fun clearCallLog() {
        _callLog.clear()
    }

    class Builder {
        private val responses: MutableMap<String, Component?> = mutableMapOf()
        private var fallback: (Entity) -> Component? = { e -> e.components.firstOrNull { it !is LifecycleState } }

        /** Pre-configure the answer for a specific Entity.id. */
        fun answer(componentId: String, response: Component?): Builder = apply {
            responses[componentId] = response
        }

        /** Pre-configure an explicit `null` (user cancelled / skipped) for a specific id. */
        fun skip(componentId: String): Builder = apply {
            responses[componentId] = null
        }

        /**
         * Override the fallback used when no pre-configured response matches.
         * Default: echo the Entity's declared [Entity.component].
         */
        fun fallback(fn: (Entity) -> Component?): Builder = apply {
            fallback = fn
        }

        fun build(): FakeInteractionSink = FakeInteractionSink(responses.toMap(), fallback)
    }

    companion object {
        fun builder(): Builder = Builder()

        /** Convenience: fake that echoes the declared component for every prompt. */
        fun echo(): FakeInteractionSink = Builder().build()
    }
}
