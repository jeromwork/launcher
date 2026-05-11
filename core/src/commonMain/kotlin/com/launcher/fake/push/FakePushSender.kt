package com.launcher.fake.push

import com.launcher.api.push.PushError
import com.launcher.api.push.PushSender
import com.launcher.api.push.PushType
import com.launcher.api.result.Outcome
import kotlinx.serialization.json.JsonObject

/**
 * Counter-based [PushSender] for tests and `mockBackend` flavor (FR-012, FR-035).
 * No network — every [notify] call records the payload and increments a
 * per-linkId counter so tests can assert "admin triggered exactly one push for
 * link X".
 *
 * Failure mode injection via [failNext]: the next call returns the supplied
 * [PushError] then resets. Useful for failure-recovery tests.
 */
class FakePushSender : PushSender {

    data class Invocation(
        val linkId: String,
        val type: PushType,
        val extra: JsonObject?,
    )

    private val log: MutableList<Invocation> = mutableListOf()
    private val perLinkCount: MutableMap<String, Int> = mutableMapOf()
    private var pendingFailure: PushError? = null

    override suspend fun notify(
        linkId: String,
        type: PushType,
        extra: JsonObject?,
    ): Outcome<Unit, PushError> {
        pendingFailure?.let {
            pendingFailure = null
            return Outcome.Failure(it)
        }
        log.add(Invocation(linkId, type, extra))
        perLinkCount[linkId] = (perLinkCount[linkId] ?: 0) + 1
        return Outcome.Success(Unit)
    }

    // ---- Test hooks ------------------------------------------------------

    fun invocations(): List<Invocation> = log.toList()

    fun countFor(linkId: String): Int = perLinkCount[linkId] ?: 0

    fun reset() {
        log.clear()
        perLinkCount.clear()
        pendingFailure = null
    }

    /** Make the next [notify] call fail with [error]. Auto-clears after firing. */
    fun failNext(error: PushError) {
        pendingFailure = error
    }
}
