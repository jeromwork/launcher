package family.push

import com.launcher.wire.WireVersion

import family.push.api.EventType
import family.push.fakes.FakePushHandler
import family.push.impl.DefaultPushHandlerRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * T052 — [DefaultPushHandlerRegistry] unit tests.
 */
class PushHandlerRegistryTest {

    @Test
    fun handlerFor_unregisteredEventType_returnsNull() {
        val registry = DefaultPushHandlerRegistry()
        assertNull(registry.handlerFor(EventType.ConfigUpdated))
    }

    @Test
    fun handlerFor_registeredEventType_returnsHandler() {
        val handler = FakePushHandler()
        val registry = DefaultPushHandlerRegistry()
        registry.register(EventType.ConfigUpdated, handler)
        assertSame(handler, registry.handlerFor(EventType.ConfigUpdated))
    }

    @Test
    fun handlerFor_initialMap_isPopulated() {
        val handler = FakePushHandler()
        val registry = DefaultPushHandlerRegistry(
            initialHandlers = mapOf(EventType.ConfigUpdated to handler),
        )
        assertNotNull(registry.handlerFor(EventType.ConfigUpdated))
    }

    @Test
    fun register_sameEventTypeTwice_lastWriteWins() {
        val h1 = FakePushHandler()
        val h2 = FakePushHandler()
        val registry = DefaultPushHandlerRegistry()
        registry.register(EventType.ConfigUpdated, h1)
        registry.register(EventType.ConfigUpdated, h2)
        assertSame(h2, registry.handlerFor(EventType.ConfigUpdated))
    }

    @Test
    fun eventType_fromWireOrNull_configUpdated() {
        // T013 inline verification — wireValue resolves to ConfigUpdated singleton.
        val resolved = EventType.fromWireOrNull("config-updated")
        assertEquals(EventType.ConfigUpdated, resolved)
    }

    @Test
    fun eventType_fromWireOrNull_unknown_returnsNull() {
        assertNull(EventType.fromWireOrNull("future-feature"))
    }
}
