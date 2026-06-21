package family.push.api

/**
 * T022 — Lookup directory: [EventType] → [PushHandler]. Per spec 019 FR-023.
 *
 * Каждый feature module регистрирует свой handler через DI (Koin module:
 * `single<PushHandler>(named("config-updated")) { ConfigUpdatedHandler(...) }`)
 * и foundation builds registry из всех registered handlers.
 *
 * Default impl: [family.push.impl.DefaultPushHandlerRegistry] — Map-backed.
 *
 * Behavior на lookup miss: returns null → receiver does silent log + ignore
 * (FR-023: «unknown event type → drop without crash»).
 */
interface PushHandlerRegistry {

    /**
     * Returns handler для [eventType], or null если ни один handler не
     * зарегистрирован. Null НЕ error — это expected case (old app version
     * receiving push for event type added в newer build).
     */
    fun handlerFor(eventType: EventType): PushHandler?
}
