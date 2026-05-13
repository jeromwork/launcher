package com.launcher.api.pairing

import com.launcher.api.result.Outcome
import com.launcher.api.sync.BackendError
import com.launcher.api.wireformat.WireFormatJson
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Wire-format tests for `/pairings/{token}` per
 * [`contracts/pairing-token.md`](specs/007-pairing-and-firebase-channel/contracts/pairing-token.md)
 * v1.0.0.
 *
 *  - Roundtrip (FR-026, SC-009).
 *  - Backward-compat: reader v2 reads v1 (SC-010).
 *  - Future-version policy: schemaVersion > CURRENT → Outcome.Failure (T026).
 *  - `pairingType` defaults to admin-managed-link when absent (FR-026 §reusable
 *    trust primitive backward-compat).
 */
class PairingTokenWireFormatTest {

    private val json = WireFormatJson.json

    @Test
    fun roundtrip_preserves_all_fields() {
        val token = PairingToken("A3KX9B")
        val out = PairingWireFormat.serialize(
            token = token,
            managedDeviceId = "device-uuid",
            managedDeviceFirebaseUid = "uid-managed",
            expiresAt = 1746974400000L,
            claimed = false,
            pairingType = PairingType.AdminManagedLink,
            createdAt = 1746974100000L,
            updatedAt = 1746974100000L,
        )
        val parsed = PairingWireFormat.deserialize(out).orFail()
        assertEquals(1, parsed.schemaVersion)
        assertEquals(PairingType.AdminManagedLink, parsed.pairingType)
        assertEquals("device-uuid", parsed.managedDeviceId)
        assertEquals("uid-managed", parsed.managedDeviceFirebaseUid)
        assertEquals(false, parsed.claimed)
        assertEquals(1746974400000L, parsed.expiresAt)
        assertEquals(1746974100000L, parsed.createdAt)
        assertEquals(1746974100000L, parsed.updatedAt)
    }

    @Test
    fun backwardCompat_unknown_extra_fields_tolerated() {
        // Simulates a future producer adding "futureField" — current reader must
        // parse known fields successfully and ignore the unknown one.
        val wire = """
            {
              "schemaVersion": 1,
              "pairingType": "admin-managed-link",
              "managedDeviceId": "device-uuid",
              "managedDeviceFirebaseUid": "uid-managed",
              "claimed": false,
              "expiresAt": 1746974400000,
              "futureField": "ignored-by-v1-reader"
            }
        """.trimIndent()
        val element = json.parseToJsonElement(wire)
        val parsed = PairingWireFormat.deserialize(element).orFail()
        assertEquals("device-uuid", parsed.managedDeviceId)
    }

    @Test
    fun unknown_future_version_handled_gracefully() {
        val wire = """
            {
              "schemaVersion": 999,
              "pairingType": "admin-managed-link",
              "managedDeviceId": "device-uuid",
              "managedDeviceFirebaseUid": "uid-managed",
              "claimed": false,
              "expiresAt": 1746974400000
            }
        """.trimIndent()
        val element = json.parseToJsonElement(wire)
        val result = PairingWireFormat.deserialize(element)
        assertTrue(result is Outcome.Failure)
        assertTrue(
            result.error is BackendError.Unknown,
            "Expected BackendError.Unknown for future version, got ${result.error}",
        )
    }

    @Test
    fun pairingType_default_admin_managed_link_when_absent() {
        // Older fixtures may omit pairingType — backward-compat per contract.
        val wire = """
            {
              "schemaVersion": 1,
              "managedDeviceId": "device-uuid",
              "managedDeviceFirebaseUid": "uid-managed",
              "claimed": false,
              "expiresAt": 1746974400000
            }
        """.trimIndent()
        val element = json.parseToJsonElement(wire)
        val parsed = PairingWireFormat.deserialize(element).orFail()
        assertEquals(PairingType.AdminManagedLink, parsed.pairingType)
    }

    @Test
    fun parseSchemaVersionOnly_extracts_without_full_parse() {
        val wire = """{"schemaVersion": 1, "other": "ignored"}"""
        val element = json.parseToJsonElement(wire) as JsonObject
        assertEquals(1, PairingWireFormat.parseSchemaVersionOnly(element))
    }

    private fun Outcome<PairingWireFormat.Parsed, BackendError>.orFail(): PairingWireFormat.Parsed =
        when (this) {
            is Outcome.Success -> value
            is Outcome.Failure -> fail("Expected success, got Failure($error)")
        }
}
