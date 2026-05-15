package com.launcher.api.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class ElementIdTest {

    @Test
    fun random_generates_valid_uuid_v4() {
        val id = ElementId.random()
        // Standard UUID format: 8-4-4-4-12 hex chars
        assertEquals(36, id.value.length)
        assertEquals('-', id.value[8])
        assertEquals('-', id.value[13])
        assertEquals('-', id.value[18])
        assertEquals('-', id.value[23])
    }

    @Test
    fun random_generates_unique_ids() {
        val a = ElementId.random()
        val b = ElementId.random()
        // Collision probability is ~zero; this test would fail при катастрофическом RNG bug
        assertNotEquals(a, b)
    }

    @Test
    fun accepts_well_formed_uuid() {
        // Should not throw
        ElementId("f1111111-1111-4111-8111-111111111111")
    }

    @Test
    fun rejects_too_short() {
        assertFailsWith<IllegalArgumentException> {
            ElementId("short")
        }
    }

    @Test
    fun rejects_missing_dashes() {
        assertFailsWith<IllegalArgumentException> {
            ElementId("f111111111114111811111111111111111111") // 36 chars, no dashes
        }
    }

    @Test
    fun rejects_non_hex_chars() {
        assertFailsWith<IllegalArgumentException> {
            ElementId("g1111111-1111-4111-8111-111111111111") // 'g' invalid hex
        }
    }
}
