package family.push.impl

import family.push.api.EventType
import family.push.api.PushHandler
import family.push.api.PushHandlerRegistry

/**
 * T051 — Map-backed [PushHandlerRegistry] impl. Per FR-023.
 *
 * Used as base for DI-wired registry. Каждый feature module регистрирует свой
 * handler via Koin: `single<PushHandler>(named("config-updated")) { ConfigUpdatedHandler(...) }`,
 * затем foundation builds DefaultPushHandlerRegistry passing all registered handlers.
 *
 * Idempotent registration: register same eventType дважды overwrites previous
 * handler (last-write-wins). Acceptable per FR-023 (handler ownership single).
 */
class DefaultPushHandlerRegistry(
    initialHandlers: Map<EventType, PushHandler> = emptyMap(),
) : PushHandlerRegistry {

    private val handlers: MutableMap<EventType, PushHandler> = initialHandlers.toMutableMap()

    /** Registers (or overwrites) handler. */
    fun register(eventType: EventType, handler: PushHandler) {
        handlers[eventType] = handler
    }

    override fun handlerFor(eventType: EventType): PushHandler? = handlers[eventType]
}
