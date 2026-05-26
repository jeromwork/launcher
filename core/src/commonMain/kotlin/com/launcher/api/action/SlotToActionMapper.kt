package com.launcher.api.action

import com.launcher.api.config.Contact
import com.launcher.api.config.Slot
import com.launcher.api.config.SlotKind
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Free function (spec 010 plan §11 C-4 — NOT a class / interface) mapping a
 * persisted [Slot] from `/config/current` to a provider-agnostic [Action] the
 * existing dispatcher (spec 005) can run.
 *
 * Returns `null` when the slot references data that no longer exists — e.g.
 * a Call/Sms slot whose `contactId` is not in [contacts], or an OpenApp slot
 * with no `packageName`. The home-screen consumer (spec 010 FR-002 / FR-003)
 * surfaces a disabled tile in that case rather than dispatching a broken
 * action.
 *
 * Pure / side-effect-free; safe to call from a Compose `remember { … }` block.
 */
fun Slot.toAction(contacts: List<Contact>): Action? {
    val args: JsonObject = args ?: return null
    return when (kind) {
        SlotKind.Call -> {
            val contactId = args.stringField("contactId") ?: return null
            val contact = contacts.firstOrNull { it.id.value == contactId } ?: return null
            Action(
                providerId = ProviderId.PHONE,
                payload = ActionPayload.Phone(number = contact.phoneNumber),
            )
        }
        SlotKind.Sms -> {
            val contactId = args.stringField("contactId") ?: return null
            val contact = contacts.firstOrNull { it.id.value == contactId } ?: return null
            Action(
                providerId = ProviderId.SMS,
                payload = ActionPayload.Sms(number = contact.phoneNumber, body = null),
            )
        }
        SlotKind.OpenApp -> {
            val packageName = args.stringField("packageName")?.takeIf { it.isNotBlank() }
                ?: return null
            Action(
                providerId = ProviderId.APP,
                payload = ActionPayload.OpenApp(packageHint = packageName),
            )
        }
        SlotKind.Document -> {
            // Spec 012 — Document slot не маппится в Action (это UI navigation:
            // tap → fullscreen DocumentViewer, не provider dispatch).
            // Caller (home screen) распознаёт Document kind напрямую и open'ает viewer.
            null
        }
    }
}

private fun JsonObject.stringField(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content
