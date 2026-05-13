package com.launcher.api.link

import com.launcher.api.identity.AdminIdentity
import com.launcher.api.result.Outcome
import com.launcher.api.sync.BackendError
import com.launcher.api.wireformat.WireFormatJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Wire-format tests for `/links/{linkId}` per
 * [`contracts/link.md`](specs/007-pairing-and-firebase-channel/contracts/link.md)
 * v1.0.0.
 */
class LinkWireFormatTest {

    private val json = WireFormatJson.json

    @Test
    fun roundtrip_preserves_all_fields() {
        val out = LinkWireFormat.serialize(
            adminId = AdminIdentity("anonUidAdminXyz"),
            managedDeviceId = "device-uuid",
            managedDeviceFirebaseUid = "uid-managed",
            createdAt = 1746974400000L,
            updatedAt = 1746974400000L,
        )
        val parsed = LinkWireFormat.deserialize(out).orFail()
        assertEquals(1, parsed.schemaVersion)
        assertEquals("anonUidAdminXyz", parsed.adminId.firebaseAuthUid)
        assertEquals("device-uuid", parsed.managedDeviceId)
        assertEquals("uid-managed", parsed.managedDeviceFirebaseUid)
        assertEquals(1746974400000L, parsed.createdAt)
        assertEquals(1746974400000L, parsed.updatedAt)
    }

    @Test
    fun backwardCompat_unknown_extra_fields_tolerated() {
        val wire = """
            {
              "schemaVersion": 1,
              "adminId": "anonUidAdminXyz",
              "managedDeviceId": "device-uuid",
              "managedDeviceFirebaseUid": "uid-managed",
              "createdAt": 1746974400000,
              "updatedAt": 1746974400000,
              "futureSubcollection": "ignored-by-v1"
            }
        """.trimIndent()
        val element = json.parseToJsonElement(wire)
        val parsed = LinkWireFormat.deserialize(element).orFail()
        assertEquals("device-uuid", parsed.managedDeviceId)
    }

    @Test
    fun unknown_future_version_handled_gracefully() {
        val wire = """
            {
              "schemaVersion": 999,
              "adminId": "anonUidAdminXyz",
              "managedDeviceId": "device-uuid",
              "managedDeviceFirebaseUid": "uid-managed"
            }
        """.trimIndent()
        val element = json.parseToJsonElement(wire)
        val result = LinkWireFormat.deserialize(element)
        assertTrue(result is Outcome.Failure)
        assertTrue(result.error is BackendError.Unknown)
    }

    @Test
    fun missing_required_field_fails() {
        val wire = """{"schemaVersion": 1, "adminId": "u"}"""
        val element = json.parseToJsonElement(wire)
        val result = LinkWireFormat.deserialize(element)
        assertTrue(result is Outcome.Failure)
    }

    private fun Outcome<LinkWireFormat.Parsed, BackendError>.orFail(): LinkWireFormat.Parsed =
        when (this) {
            is Outcome.Success -> value
            is Outcome.Failure -> fail("Expected success, got Failure($error)")
        }
}
