package com.launcher.test.fitness

import com.launcher.api.ProjectEvent
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Spec 005 / contracts/diagnostics-events-v2.md §Test coverage / T581:
 * `ProjectEvent.ActionDispatched` field set is **frozen** at four fields:
 * providerId, resultKind, fallbackUsed, timestampMs. Adding any field
 * requires an explicit security review per Article XIV §3 (no PII allowed)
 * and an updated contract document.
 *
 * Reflection-based check — fast on JVM, runs in :core:test.
 */
class EventTaxonomyTest {

    @Test
    fun actionDispatched_hasExactlyFourFields() {
        val klass = ProjectEvent.ActionDispatched::class.java
        // Filter out compiler-synthesised fields (Kotlin metadata, $stable, etc.)
        val dataFields = klass.declaredFields
            .filterNot { it.isSynthetic }
            .filterNot { it.name.startsWith("$") }
            .map { it.name }
            .toSet()
        val expected = setOf("providerId", "resultKind", "fallbackUsed", "timestampMs")
        assertEquals(
            "ProjectEvent.ActionDispatched field set is frozen per contracts/diagnostics-events-v2.md.\n" +
                "Adding a field requires Article XIV §3 review.",
            expected, dataFields,
        )
    }
}
