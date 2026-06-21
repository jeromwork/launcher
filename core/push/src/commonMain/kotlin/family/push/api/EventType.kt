package family.push.api

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * T013 — Sealed registry событий, которые foundation умеет триггерить и
 * принимать. Per spec 019 FR-021, FR-077, data-model.md §EventType.
 *
 * Adding новый event type — single-file change here (sealed interface) +
 * EventTypeRegistry entry в Worker (workers/push/src/registry/event-types.ts).
 * Per FR-052, FR-060 (extensibility ≤ 15 LOC).
 *
 *  • [wireValue] — string serialized in PushTriggerRequest / PushPayload. Stable wire
 *    contract (renaming = breaking change → schemaVersion bump).
 *  • [handlerTimeout] — passed to BackgroundDispatcher.dispatch(). Каждый event type
 *    объявляет свой budget: config = 30s, photo = 5min, SOS = 10s. Foundation
 *    не assumes "one size fits all".
 */
sealed interface EventType {
    val wireValue: String
    val handlerTimeout: Duration

    companion object {
        val DEFAULT_TIMEOUT: Duration = 30.seconds

        /**
         * Lookup helper для PushHandlerRegistry / receiver dispatch. Unknown event
         * type → null → caller does silent log + ignore (per FR-023, FR-075).
         */
        fun fromWireOrNull(value: String): EventType? = when (value) {
            ConfigUpdated.wireValue -> ConfigUpdated
            // Future event types added here (S-4 SOS, S-9 health, V-3 album, etc.).
            // Каждый добавляется in respective feature spec, не F-5c.
            else -> null
        }
    }

    /**
     * F-5c primary use case. ConfigSaver (spec 018 F-5b) триггерит этот event
     * after successful save → recipient devices invoke ConfigSaver.loadOwn/loadForOther.
     *
     * Timeout 30s — config blobs <100KB, download + decrypt + write занимает
     * единицы секунд в нормальных условиях.
     */
    data object ConfigUpdated : EventType {
        override val wireValue: String = "config-updated"
        override val handlerTimeout: Duration = DEFAULT_TIMEOUT
    }

    // Future event types (added в feature specs, не F-5c):
    //
    // data object SosTriggered : EventType {
    //     override val wireValue = "sos-triggered"
    //     override val handlerTimeout = 10.seconds       // SOS — fast or fail loud
    // }
    //
    // data object AlbumPhotoAdded : EventType {
    //     override val wireValue = "album-photo-added"
    //     override val handlerTimeout = 5.minutes        // photo download long
    // }
}
