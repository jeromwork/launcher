package com.launcher.api.localization

import com.launcher.api.wizard.fakes.FakeStringResolver
import kotlin.test.Test
import kotlin.test.assertEquals

class StringResolverFallbackTest {

    @Test
    fun present_returnsLocalised() {
        val resolver = FakeStringResolver(
            tag = "ru",
            table = mapOf("wizard.next" to "Далее"),
        )
        assertEquals("Далее", resolver.resolve("wizard.next"))
    }

    @Test
    fun missingKey_returnsKeyLiteral() {
        val resolver = FakeStringResolver(tag = "ru", table = emptyMap())
        assertEquals("wizard.next", resolver.resolve("wizard.next"))
    }

    @Test
    fun interpolatesArgs() {
        val resolver = FakeStringResolver(
            tag = "en",
            table = mapOf("wizard.step_n_of_m" to "Step {current} of {total}"),
        )
        assertEquals(
            "Step 2 of 5",
            resolver.resolve("wizard.step_n_of_m", mapOf("current" to 2, "total" to 5)),
        )
    }

    @Test
    fun plural_interpolatesCount() {
        val resolver = FakeStringResolver(
            tag = "en",
            table = mapOf("wizard.step_n_of_m" to "Step {current} of {total} ({count} total)"),
        )
        assertEquals(
            "Step 2 of 5 (5 total)",
            resolver.resolvePlural(
                "wizard.step_n_of_m",
                count = 5,
                args = mapOf("current" to 2, "total" to 5),
            ),
        )
    }
}
