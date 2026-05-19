package com.launcher.ui.setup

import com.launcher.api.ProjectEvent
import com.launcher.api.setup.CheckStatus
import com.launcher.api.setup.SetupCheck
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Spec 010 T072 / T075 (FR-020a + FR-020b) — orchestrates the
 * [SetupCheck] list:
 *
 *  - **Execution model (FR-020a)**: [refresh] runs every check from
 *    [setupChecks] in parallel. The Settings screen calls [refresh] on
 *    `Lifecycle.RESUMED`; the app calls it once at cold start via the
 *    initial `init` block. There is **no background polling** — explicit
 *    constraint per FR-020a.
 *
 *  - **Exception handling (FR-020b)**: each `check()` is wrapped in
 *    try-catch. Caught exceptions become [CheckStatus.NotConfigured]
 *    with `reason = exception.message?.take(200)` AND a
 *    [ProjectEvent.SetupCheckException] diagnostic is emitted via
 *    [emitDiagnostic] (no PII per CHK-security-004; truncation keeps
 *    accidental long stack-strings out of telemetry).
 *
 * [results] is a hot StateFlow keyed by check id → status; safe to
 * `collectAsState` in Compose.
 */
class SetupCheckEngine(
    private val setupChecks: List<SetupCheck>,
    private val emitDiagnostic: (ProjectEvent.SetupCheckException) -> Unit = {},
    private val nowMillis: () -> Long = { 0L },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {

    private val _results = MutableStateFlow<Map<String, CheckStatus>>(emptyMap())
    val results: StateFlow<Map<String, CheckStatus>> = _results.asStateFlow()

    /**
     * FR-020a cold-start trigger: schedules an initial [refresh] on the
     * engine's scope (fire-and-forget). Caller (Application.onCreate or
     * Settings's LaunchedEffect) decides timing. The Settings screen re-calls
     * [refresh] on `Lifecycle.RESUMED`. **No background polling**.
     */
    fun startColdRefresh() {
        scope.launch { refresh() }
    }

    /**
     * Re-runs every check in parallel. Idempotent — safe to call repeatedly
     * (Settings RESUMED throttling is the caller's responsibility; the
     * engine itself does no polling).
     */
    suspend fun refresh() {
        val deferred = setupChecks.map { check ->
            scope.async {
                check.id to safeRun(check)
            }
        }
        _results.value = deferred.awaitAll().toMap()
    }

    /**
     * FR-020b wrapper: catches any Throwable, surfaces it как `NotConfigured`,
     * emits the SetupCheckException diagnostic event.
     */
    private suspend fun safeRun(check: SetupCheck): CheckStatus {
        return try {
            check.check()
        } catch (t: Throwable) {
            val truncated = (t.message ?: t::class.simpleName.orEmpty()).take(200)
            emitDiagnostic(
                ProjectEvent.SetupCheckException(
                    checkId = check.id,
                    reason = truncated,
                    timestampMs = nowMillis(),
                ),
            )
            CheckStatus.NotConfigured(reason = truncated)
        }
    }

    /** Returns the static metadata for каждой check (id + criticality + surfaces). */
    fun allChecks(): List<SetupCheck> = setupChecks
}
