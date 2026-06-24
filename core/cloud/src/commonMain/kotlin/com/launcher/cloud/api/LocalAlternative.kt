package com.launcher.cloud.api

/**
 * Opt-in port для критических фич с локальным fallback. Реализуется только
 * теми фичами, которые ДОЛЖНЫ работать без cloud (SOS, в будущем — аварийные
 * локальные уведомления). НЕ обязательный contract для всех cloud-фич.
 *
 * ## Invariants (из contracts/local-alternative-port.md)
 *
 *  - INV-1: [executeLocally] НЕ делает cloud-проверок — работает независимо от
 *    [CloudAvailability]. Implementation не должна иметь ссылок на этот port.
 *  - INV-2: Deterministic для одинакового [ActionContext] (в рамках session).
 *  - INV-3: Возвращает [ActionResult] — никаких exceptions кроме
 *    [kotlinx.coroutines.CancellationException].
 *  - INV-4: Время выполнения < 1 секунды для UI-triggered actions (SC-002).
 */
interface LocalAlternative {
    suspend fun executeLocally(context: ActionContext): ActionResult
}
