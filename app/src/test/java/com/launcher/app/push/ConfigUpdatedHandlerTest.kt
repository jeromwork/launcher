package com.launcher.app.push

import family.keys.api.ConfigSaver
import family.keys.api.Outcome
import family.keys.api.StorageError
import family.push.api.PushPayload
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * T122 unit tests — verifies debounce + ownerUid routing in [ConfigUpdatedHandler].
 *
 * Robolectric runner — because handler invokes `android.util.Log` (silent ignore
 * paths). Pure JVM run would throw "Method not mocked" RuntimeException.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = android.app.Application::class)
class ConfigUpdatedHandlerTest {

    private class FakeConfigSaver : ConfigSaver {
        data class LoadOwnCall(val configName: String)
        data class LoadForOtherCall(val ownerUid: String, val configName: String)

        val loadOwnCalls: MutableList<LoadOwnCall> = mutableListOf()
        val loadForOtherCalls: MutableList<LoadForOtherCall> = mutableListOf()

        override suspend fun saveOwn(configName: String, bytes: ByteArray): Outcome<Unit, StorageError> =
            Outcome.Success(Unit)

        override suspend fun loadOwn(configName: String): Outcome<ByteArray, StorageError> {
            loadOwnCalls += LoadOwnCall(configName)
            return Outcome.Success(byteArrayOf(1, 2, 3))
        }

        override suspend fun listOwn(): Outcome<List<String>, StorageError> = Outcome.Success(emptyList())
        override suspend fun deleteOwn(configName: String): Outcome<Unit, StorageError> = Outcome.Success(Unit)

        override suspend fun saveForOther(
            ownerUid: String,
            configName: String,
            bytes: ByteArray,
        ): Outcome<Unit, StorageError> = Outcome.Success(Unit)

        override suspend fun loadForOther(
            ownerUid: String,
            configName: String,
        ): Outcome<ByteArray, StorageError> {
            loadForOtherCalls += LoadForOtherCall(ownerUid, configName)
            return Outcome.Success(byteArrayOf(4, 5, 6))
        }

        override suspend fun listForOther(ownerUid: String): Outcome<List<String>, StorageError> =
            Outcome.Success(emptyList())
    }

    private val currentUid = "self-uid"
    private val saver = FakeConfigSaver()
    private var fakeNow = 0L

    private fun handler() = ConfigUpdatedHandler(
        configSaver = saver,
        currentUidSupplier = { currentUid },
        nowMillis = { fakeNow },
        debounceWindowMillis = 2_000L,
    )

    @Test
    fun handle_ownerEqCurrentUid_invokesLoadOwn() = runTest {
        val payload = PushPayload(
            eventType = "config-updated",
            ownerUid = currentUid,
            triggerId = "t-1",
            fields = mapOf("configName" to "main"),
        )
        handler().handle(payload)
        assertEquals(1, saver.loadOwnCalls.size)
        assertEquals("main", saver.loadOwnCalls.first().configName)
        assertEquals(0, saver.loadForOtherCalls.size)
    }

    @Test
    fun handle_ownerDifferentFromCurrentUid_invokesLoadForOther() = runTest {
        val payload = PushPayload(
            eventType = "config-updated",
            ownerUid = "other-uid",
            triggerId = "t-2",
            fields = mapOf("configName" to "kitchen"),
        )
        handler().handle(payload)
        assertEquals(0, saver.loadOwnCalls.size)
        assertEquals(1, saver.loadForOtherCalls.size)
        assertEquals("other-uid", saver.loadForOtherCalls.first().ownerUid)
        assertEquals("kitchen", saver.loadForOtherCalls.first().configName)
    }

    @Test
    fun handle_missingConfigName_drops_noLoadCalled() = runTest {
        val payload = PushPayload(
            eventType = "config-updated",
            ownerUid = currentUid,
            triggerId = "t-3",
            fields = emptyMap(),
        )
        handler().handle(payload)
        assertEquals(0, saver.loadOwnCalls.size)
        assertEquals(0, saver.loadForOtherCalls.size)
    }

    @Test
    fun handle_sameTriggerIdWithinDebounceWindow_loadInvokedOnce() = runTest {
        val h = handler()
        val payload = PushPayload(
            eventType = "config-updated",
            ownerUid = currentUid,
            triggerId = "t-dup",
            fields = mapOf("configName" to "main"),
        )
        fakeNow = 0L
        h.handle(payload)
        fakeNow = 500L
        h.handle(payload)
        fakeNow = 1_999L
        h.handle(payload)

        assertEquals(1, saver.loadOwnCalls.size)
    }

    @Test
    fun handle_sameTriggerIdAfterDebounceWindow_loadInvokedTwice() = runTest {
        val h = handler()
        val payload = PushPayload(
            eventType = "config-updated",
            ownerUid = currentUid,
            triggerId = "t-rep",
            fields = mapOf("configName" to "main"),
        )
        fakeNow = 0L
        h.handle(payload)
        fakeNow = 2_500L
        h.handle(payload)

        assertEquals(2, saver.loadOwnCalls.size)
    }

    @Test
    fun handle_tenDuplicateTriggers_loadInvokedOnce_perSC006() = runTest {
        val h = handler()
        val payload = PushPayload(
            eventType = "config-updated",
            ownerUid = currentUid,
            triggerId = "t-burst",
            fields = mapOf("configName" to "main"),
        )
        repeat(10) {
            fakeNow = it * 100L
            h.handle(payload)
        }
        assertEquals(1, saver.loadOwnCalls.size)
    }

    @Test
    fun handle_nullOwnerUid_treatedAsSelf() = runTest {
        val payload = PushPayload(
            eventType = "config-updated",
            ownerUid = null,
            triggerId = "t-null",
            fields = mapOf("configName" to "main"),
        )
        handler().handle(payload)
        assertEquals(1, saver.loadOwnCalls.size)
        assertEquals(0, saver.loadForOtherCalls.size)
    }
}
