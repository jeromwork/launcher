package com.launcher.cloud.contracts

import com.launcher.cloud.fake.FakeEmergencyNumberResolver
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class EmergencyNumberResolverContractTest {

    private val phoneRegex = Regex("^[0-9]{3,4}$")

    @Test
    fun returns_non_empty_string() = runTest {
        val resolver = FakeEmergencyNumberResolver(number = "112")
        val number = resolver.getEmergencyNumber()
        assertTrue(number.isNotEmpty(), "Emergency number must be non-empty (INV-1)")
    }

    @Test
    fun returns_valid_phone_format() = runTest {
        listOf("102", "103", "112", "911", "110", "000").forEach { candidate ->
            val resolver = FakeEmergencyNumberResolver(number = candidate)
            val number = resolver.getEmergencyNumber()
            assertTrue(
                phoneRegex.matches(number),
                "Number '$number' must match $phoneRegex (INV-2)",
            )
        }
    }
}
