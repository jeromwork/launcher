package com.launcher.core.actions.handlers

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import com.launcher.api.action.Action
import com.launcher.api.action.ActionPayload
import com.launcher.api.action.DispatchResult
import com.launcher.core.contacts.MockContactsRepository

/**
 * Handles `whatsapp_message` and `whatsapp_call` payloads (spec 005 US-501).
 *
 * Strategy (per spec §4.1, contracts/action-wire-format.md):
 *  - Resolve `contactRef` via [MockContactsRepository]; missing `phoneE164` →
 *    `Failure` (the dispatcher falls back to `OpenApp` on the next layer).
 *  - Build `https://wa.me/<digits>?...` URI. There is no public WhatsApp
 *    deep-link to dial directly; for both message and call we open the chat
 *    and let the user tap the voice/video icon. One extra tap is acceptable
 *    for the elderly persona; mis-routed calls are not.
 *  - Try `setPackage("com.whatsapp")` first, then `com.whatsapp.w4b`
 *    (business). Dispatcher already verified availability; here we just
 *    pick the package that actually claims the intent.
 *
 * The handler returns `Failure` on any platform error; the dispatcher (not
 * the handler) decides whether to descend into [Action.fallback].
 */
class WhatsAppHandler(
    private val contacts: MockContactsRepository,
) : ActionHandler {

    override suspend fun handle(action: Action, ctx: HandlerContext): DispatchResult {
        val contactRef: String = when (val payload = action.payload) {
            is ActionPayload.WhatsAppMessage -> payload.contactRef
            is ActionPayload.WhatsAppCall    -> payload.contactRef
            else -> return DispatchResult.Failure(
                "WhatsAppHandler received unexpected payload: ${payload::class.simpleName}"
            )
        }

        val contact = contacts.findByRef(contactRef)
            ?: return DispatchResult.Failure("unknown contactRef '$contactRef'")
        val phone = contact.phoneE164
            ?: return DispatchResult.Failure("contact '$contactRef' has no phone number")
        val phoneDigits = phone.removePrefix("+").filter(Char::isDigit)

        val uri = Uri.parse(WA_BASE + phoneDigits)

        for (pkg in WHATSAPP_PACKAGES) {
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage(pkg)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (ctx.packageManager.resolveActivity(intent, 0) != null) {
                try {
                    ctx.context.startActivity(intent)
                    return DispatchResult.Ok
                } catch (_: ActivityNotFoundException) {
                    // race: package vanished between resolve and start; try next.
                }
            }
        }
        return DispatchResult.Failure("no whatsapp package can handle $uri")
    }

    companion object {
        private const val WA_BASE = "https://wa.me/"
        private val WHATSAPP_PACKAGES = listOf("com.whatsapp", "com.whatsapp.w4b")
    }
}
