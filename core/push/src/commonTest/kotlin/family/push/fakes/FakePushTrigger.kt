package family.push.fakes

import com.launcher.wire.WireVersion

import family.push.api.EventType
import family.push.api.Outcome
import family.push.api.PushTrigger
import family.push.api.PushTriggerError
import family.push.api.TargetScope

/**
 * T030 — In-memory [PushTrigger] для unit tests. Captures invocations в list
 * для assertion.
 *
 * Usage:
 * ```
 * val fake = FakePushTrigger()
 * subject.doSomethingThatTriggers()
 * assertEquals(1, fake.invocations.size)
 * assertEquals(EventType.ConfigUpdated, fake.invocations.first().eventType)
 * ```
 *
 * Default behavior: returns [Outcome.Success]. Use [respondWith] для testing
 * failure paths (rate-limit, network failure).
 */
class FakePushTrigger : PushTrigger {

    data class Invocation(
        val eventType: EventType,
        val targetScope: TargetScope,
        val ownerUid: String,
        val payload: Map<String, String>,
    )

    val invocations: MutableList<Invocation> = mutableListOf()

    private var nextResult: Outcome<Unit, PushTriggerError> = Outcome.Success(Unit)

    /** Configure outcome for next (and subsequent) trigger() calls. */
    fun respondWith(result: Outcome<Unit, PushTriggerError>) {
        nextResult = result
    }

    override suspend fun trigger(
        eventType: EventType,
        targetScope: TargetScope,
        ownerUid: String,
        payload: Map<String, String>,
    ): Outcome<Unit, PushTriggerError> {
        invocations += Invocation(eventType, targetScope, ownerUid, payload)
        return nextResult
    }
}
