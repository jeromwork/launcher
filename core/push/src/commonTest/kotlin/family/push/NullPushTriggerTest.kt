package family.push

import com.launcher.wire.WireVersion

import family.push.api.EventType
import family.push.api.Outcome
import family.push.api.TargetScope
import family.push.impl.NullPushTrigger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

/**
 * T304 (smoke variant) — verifies [NullPushTrigger] returns Success без network
 * call. Per spec 019 Scenario 6 (Cloud-mode integration), CHK-DSS-007.
 */
class NullPushTriggerTest {

    @Test
    fun trigger_returnsSuccess_noSideEffect() = runTest {
        val trigger = NullPushTrigger()
        repeat(100) {
            val outcome = trigger.trigger(
                eventType = EventType.ConfigUpdated,
                targetScope = TargetScope.OwnAndGrants,
                ownerUid = "any-uid",
                payload = mapOf("configName" to "main"),
            )
            assertEquals(Outcome.Success(Unit), outcome)
        }
    }
}
