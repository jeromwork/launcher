package com.launcher.core.actions

import com.launcher.api.action.ActionPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomPayloadValidatorTest {

    private val v = CustomPayloadValidator()

    private fun customWith(params: Map<String, String>) =
        ActionPayload.Custom(key = "ask", params = params)

    @Test
    fun zeroKeys_isOk() {
        assertTrue(v.validate(customWith(emptyMap())) is CustomPayloadValidator.Result.Ok)
    }

    @Test
    fun sixteenKeys_isOk() {
        val params = (1..16).associate { "key_$it" to "v" }
        assertTrue(v.validate(customWith(params)) is CustomPayloadValidator.Result.Ok)
    }

    @Test
    fun seventeenKeys_isInvalid() {
        val params = (1..17).associate { "key_$it" to "v" }
        val r = v.validate(customWith(params)) as CustomPayloadValidator.Result.Invalid
        assertTrue(r.errors.any { "17 keys" in it })
    }

    @Test
    fun keyLength_64isOk() {
        val key = "k" + "x".repeat(63)
        assertEquals(64, key.length)
        assertTrue(v.validate(customWith(mapOf(key to "v"))) is CustomPayloadValidator.Result.Ok)
    }

    @Test
    fun keyLength_65isInvalid() {
        val key = "k" + "x".repeat(64)
        assertEquals(65, key.length)
        val r = v.validate(customWith(mapOf(key to "v")))
        assertTrue(r is CustomPayloadValidator.Result.Invalid)
    }

    @Test
    fun keyMustStartWithLetter() {
        val r = v.validate(customWith(mapOf("1abc" to "v")))
        assertTrue(r is CustomPayloadValidator.Result.Invalid)
    }

    @Test
    fun keyMustBeLowercase() {
        val r = v.validate(customWith(mapOf("Abc" to "v")))
        assertTrue(r is CustomPayloadValidator.Result.Invalid)
    }

    @Test
    fun valueLength_1024isOk() {
        val value = "x".repeat(1024)
        assertTrue(v.validate(customWith(mapOf("key" to value))) is CustomPayloadValidator.Result.Ok)
    }

    @Test
    fun valueLength_1025isInvalid() {
        val value = "x".repeat(1025)
        val r = v.validate(customWith(mapOf("key" to value)))
        assertTrue(r is CustomPayloadValidator.Result.Invalid)
    }

    @Test
    fun nestedJsonValue_warnsButPasses() {
        val r = v.validate(customWith(mapOf("nested_key" to """{"nested": true}""")))
        assertTrue(r is CustomPayloadValidator.Result.Ok)
        assertTrue(r.warnings.any { "nested JSON" in it })
    }
}
