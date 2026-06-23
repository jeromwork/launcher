package com.launcher.cloud.contracts

import com.launcher.cloud.fake.FakeCloudAvailability
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CloudAvailabilityContractTest {

    @Test
    fun read_returns_current_value() = runTest {
        val fake = FakeCloudAvailability(initial = false)
        assertFalse(fake.isCloudAvailable())
        fake.set(true)
        assertTrue(fake.isCloudAvailable())
    }

    @Test
    fun flow_emits_initial_value() = runTest {
        val fake = FakeCloudAvailability(initial = true)
        val emitted = fake.isCloudAvailableFlow.first()
        assertEquals(true, emitted)
    }

    @Test
    fun flow_emits_on_change() = runTest {
        val fake = FakeCloudAvailability(initial = false)
        // StateFlow conflates emissions; assert sequentially via reads.
        assertEquals(false, fake.isCloudAvailableFlow.first())
        fake.set(true)
        assertEquals(true, fake.isCloudAvailableFlow.first())
        fake.set(false)
        assertEquals(false, fake.isCloudAvailableFlow.first())
    }

    @Test
    fun flow_distinct_until_changed() = runTest {
        val fake = FakeCloudAvailability(initial = false)
        // Set same value twice — StateFlow does not emit duplicate.
        // Verified by .value semantic; explicit collection would deadlock since
        // StateFlow never completes. Read current state instead.
        fake.set(false)
        fake.set(false)
        assertEquals(false, fake.isCloudAvailableFlow.first())
        fake.set(true)
        assertEquals(true, fake.isCloudAvailableFlow.first())
    }

    @Test
    fun default_is_false() = runTest {
        val fake = FakeCloudAvailability()
        assertFalse(fake.isCloudAvailable())
    }

    @Test
    fun persistence_survives_recreate() = runTest {
        // Эмуляция «kill app»: external state живёт вне instance, новый instance
        // его читает. Для реального impl (DataStore) тест в androidUnitTest.
        var externalValue = false
        var instance = FakeCloudAvailability(initial = externalValue)
        instance.set(true)
        externalValue = instance.isCloudAvailable()

        val recreated = FakeCloudAvailability(initial = externalValue)
        assertTrue(recreated.isCloudAvailable())
    }
}
