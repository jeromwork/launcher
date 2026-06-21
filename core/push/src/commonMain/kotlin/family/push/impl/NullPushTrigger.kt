package family.push.impl

import family.push.api.EventType
import family.push.api.Outcome
import family.push.api.PushTrigger
import family.push.api.PushTriggerError
import family.push.api.TargetScope

/**
 * T053 — No-op [PushTrigger] для local-mode operation. Per CHK-DSS-007
 * (device-self-sufficiency), spec.md §Сценарий 6 (Cloud-mode integration).
 *
 * Every device works locally — even before Sign-In или после cloud sign-out.
 * В этом state, [PushTrigger] resolved via DI к [NullPushTrigger] (mockBackend
 * flavor OR cloud-disabled state) returns Success без сетевого вызова.
 *
 * Foundation contract: ConfigSaver invokes pushTrigger.trigger() unconditionally
 * after save. NullPushTrigger ensures это safe в local mode (no Ktor call, no
 * "no signed-in identity" exceptions surfacing к user).
 */
class NullPushTrigger : PushTrigger {
    override suspend fun trigger(
        eventType: EventType,
        targetScope: TargetScope,
        ownerUid: String,
        payload: Map<String, String>,
    ): Outcome<Unit, PushTriggerError> = Outcome.Success(Unit)
}
