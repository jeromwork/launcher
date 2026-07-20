package family.push.fakes

import family.wire.WireVersion

import family.push.api.EventType
import family.push.api.PushHandler
import family.push.api.PushHandlerRegistry

/**
 * T033 — In-memory registry для unit tests.
 */
class FakePushHandlerRegistry(
    initialHandlers: Map<EventType, PushHandler> = emptyMap(),
) : PushHandlerRegistry {

    private val handlers: MutableMap<EventType, PushHandler> = initialHandlers.toMutableMap()

    fun register(eventType: EventType, handler: PushHandler) {
        handlers[eventType] = handler
    }

    fun unregister(eventType: EventType) {
        handlers.remove(eventType)
    }

    override fun handlerFor(eventType: EventType): PushHandler? = handlers[eventType]
}
