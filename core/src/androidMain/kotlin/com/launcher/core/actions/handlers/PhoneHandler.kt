package com.launcher.core.actions.handlers

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import com.launcher.api.action.Action
import com.launcher.api.action.ActionPayload
import com.launcher.api.action.DispatchResult

/**
 * Handles `phone` payloads.
 *
 * **History**:
 *  - Spec 005 §7.5 deliberately used `Intent.ACTION_DIAL` only (no CALL_PHONE
 *    permission, no auto-call) — protected elderly users from accidental
 *    calls when ACTION_CALL would fire on first tap.
 *  - Spec 010 FR-012 supersedes that constraint by introducing an explicit
 *    user confirmation dialog (`CallConfirmationDialog`) BEFORE this handler
 *    runs. The dialog's «Позвонить» button is the user's explicit consent,
 *    so we can now CALL directly via [Intent.ACTION_CALL] when the runtime
 *    permission is granted — saving the user one tap (SC-003 «2 taps to call»).
 *
 * **Behaviour after spec 010**:
 *  - If [Manifest.permission.CALL_PHONE] is granted → fire [Intent.ACTION_CALL].
 *  - Otherwise → fall back to [Intent.ACTION_DIAL] (same as spec 005 path),
 *    which still calls but requires the user to tap the green button. No
 *    functional regression.
 *
 * The `<queries>` manifest declaration (spec 010 T003) ensures `ACTION_CALL`
 * on `tel:` resolves on Android 11+ even with no default-dialer assignment.
 */
class PhoneHandler : ActionHandler {

    override suspend fun handle(action: Action, ctx: HandlerContext): DispatchResult {
        val payload = action.payload as? ActionPayload.Phone
            ?: return DispatchResult.Failure(
                "PhoneHandler received unexpected payload: ${action.payload::class.simpleName}"
            )

        val canCall = ContextCompat.checkSelfPermission(
            ctx.context,
            Manifest.permission.CALL_PHONE,
        ) == PackageManager.PERMISSION_GRANTED

        val intent = Intent(
            if (canCall) Intent.ACTION_CALL else Intent.ACTION_DIAL,
            Uri.parse(TEL_SCHEME + payload.number),
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            ctx.context.startActivity(intent)
            DispatchResult.Ok
        } catch (e: ActivityNotFoundException) {
            DispatchResult.Failure("no dialer for tel: (${e.message ?: "ActivityNotFoundException"})")
        } catch (e: SecurityException) {
            // Edge case: ACTION_CALL throws even with permission granted на
            // некоторых OEM (Xiaomi MIUI «restricted apps»). Fall back to DIAL.
            val fallback = Intent(Intent.ACTION_DIAL, Uri.parse(TEL_SCHEME + payload.number))
                .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            try {
                ctx.context.startActivity(fallback)
                DispatchResult.Ok
            } catch (ee: ActivityNotFoundException) {
                DispatchResult.Failure("no dialer for tel: (${ee.message})")
            }
        }
    }

    companion object {
        private const val TEL_SCHEME = "tel:"
    }
}
