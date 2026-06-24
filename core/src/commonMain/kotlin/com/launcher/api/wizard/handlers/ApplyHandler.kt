package com.launcher.api.wizard.handlers

import com.launcher.api.wizard.ApplyResult
import com.launcher.api.wizard.data.ApplySpec

/**
 * Per-variant handler that maps an [ApplySpec] to an [ApplyResult]
 * (typically launching a system prompt or no-op). Pair to [CheckHandler].
 *
 * Per data-model.md §1.4 + plan.md Phase 2.
 */
interface ApplyHandler {
    suspend fun apply(spec: ApplySpec): ApplyResult
}
