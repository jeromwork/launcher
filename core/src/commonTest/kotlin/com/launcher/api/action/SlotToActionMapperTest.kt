package com.launcher.api.action

import com.launcher.api.config.Contact
import com.launcher.api.config.ElementId
import com.launcher.api.config.Slot
import com.launcher.api.config.SlotKind
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Spec 010 T023 — verifies [toAction] mapping для всех `SlotKind` variants;
 * null result on missing contact; emoji в displayName preserved.
 */
class SlotToActionMapperTest {

    private val maria = Contact(
        id = ElementId("11111111-1111-4111-8111-111111111111"),
        displayName = "Маша 👵",
        phoneNumber = "+79161234567",
    )

    private val ivan = Contact(
        id = ElementId("22222222-2222-4222-8222-222222222222"),
        displayName = "Иван",
        phoneNumber = "+14155550123",
    )

    private val contacts = listOf(maria, ivan)

    @Test
    fun call_slot_maps_to_phone_action() {
        val slot = slot(SlotKind.Call, "contactId" to maria.id.value)
        val action = slot.toAction(contacts)
        assertTrue(action != null)
        assertEquals(ProviderId.PHONE, action.providerId)
        assertEquals(ActionPayload.Phone(number = "+79161234567"), action.payload)
    }

    @Test
    fun sms_slot_maps_to_sms_action_without_body() {
        val slot = slot(SlotKind.Sms, "contactId" to ivan.id.value)
        val action = slot.toAction(contacts)
        assertTrue(action != null)
        assertEquals(ProviderId.SMS, action.providerId)
        assertEquals(ActionPayload.Sms(number = "+14155550123", body = null), action.payload)
    }

    @Test
    fun open_app_slot_maps_to_app_action() {
        val slot = slot(SlotKind.OpenApp, "packageName" to "com.whatsapp")
        val action = slot.toAction(contacts)
        assertTrue(action != null)
        assertEquals(ProviderId.APP, action.providerId)
        assertEquals(ActionPayload.OpenApp(packageHint = "com.whatsapp"), action.payload)
    }

    @Test
    fun missing_contact_returns_null_for_call() {
        val slot = slot(SlotKind.Call, "contactId" to "99999999-9999-4999-8999-999999999999")
        assertNull(slot.toAction(contacts))
    }

    @Test
    fun missing_contact_returns_null_for_sms() {
        val slot = slot(SlotKind.Sms, "contactId" to "99999999-9999-4999-8999-999999999999")
        assertNull(slot.toAction(contacts))
    }

    @Test
    fun null_args_returns_null() {
        val slot = Slot(id = ElementId("33333333-3333-4333-8333-333333333333"), kind = SlotKind.Call, args = null)
        assertNull(slot.toAction(contacts))
    }

    @Test
    fun blank_package_returns_null() {
        val slot = slot(SlotKind.OpenApp, "packageName" to "")
        assertNull(slot.toAction(contacts))
    }

    @Test
    fun missing_contactId_field_returns_null() {
        val slot = slot(SlotKind.Call, "label" to "Maria") // no contactId
        assertNull(slot.toAction(contacts))
    }

    @Test
    fun emoji_in_display_name_does_not_affect_mapping() {
        // The mapper does not reference displayName — emoji is a non-issue for the
        // action itself. This guards against a future regression where someone wires
        // displayName into Phone.number formatting.
        val slot = slot(SlotKind.Call, "contactId" to maria.id.value)
        val action = slot.toAction(contacts)
        assertEquals("+79161234567", (action!!.payload as ActionPayload.Phone).number)
    }

    private fun slot(kind: SlotKind, vararg args: Pair<String, String>): Slot =
        Slot(
            id = ElementId("44444444-4444-4444-8444-444444444444"),
            kind = kind,
            args = buildJsonObject {
                args.forEach { (k, v) -> put(k, JsonPrimitive(v)) }
            } as JsonObject,
        )
}
