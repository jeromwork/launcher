package com.launcher.adapters.push

import android.util.Log
import com.launcher.api.push.PushPayload
import com.launcher.api.push.PushReceiver
import com.launcher.api.push.PushType
import com.launcher.api.result.Outcome
import com.launcher.api.sync.DocPath
import com.launcher.api.sync.RemoteSyncBackend

/**
 * Managed-side handler for incoming FCM data-messages (FR-037).
 *
 * Spec 007 scope: **read latest doc + log** — actual application of
 * configuration to local Room / UI happens in spec 008; command dispatch
 * in spec 009. The point of T059 is to verify the push channel end-to-end
 * (push received → Firestore read succeeds) without coupling spec 007 to
 * either of those future surfaces.
 *
 * Idempotency (contract §Idempotency): all handlers are safe to run twice
 * — `config-changed` is a read-latest, command-issued is a read + future-
 * dispatch (handler in spec 009 must dedupe by cmdId).
 *
 * TODO(spec 008): expand `ConfigChanged` handler to write to local Room
 * + trigger UI refresh.
 * TODO(spec 009): expand `CommandIssued` handler to dispatch command.
 */
class LauncherPushReceiver(
    private val backend: RemoteSyncBackend,
) : PushReceiver {

    override suspend fun onPush(payload: PushPayload) {
        Log.i(TAG, "push received: type=${payload.type.wireValue} linkId=${payload.linkId}")
        when (val type = payload.type) {
            PushType.ConfigChanged -> handleConfigChanged(payload.linkId)
            PushType.CommandIssued -> handleCommandIssued(payload)
            PushType.Revoke -> Log.i(TAG, "revoke push — admin-side UI refresh (spec 009)")
            else -> Log.w(TAG, "unknown push type: $type")
        }
    }

    private suspend fun handleConfigChanged(linkId: String) {
        val outcome = backend.readDoc(DocPath.LinkConfig(linkId))
        when (outcome) {
            is Outcome.Success -> {
                val snap = outcome.value
                Log.i(TAG, "config received: schemaVersion=${snap?.schemaVersion} updatedAt=${snap?.updatedAt}")
                // TODO(spec 008): apply config to local Room + UI refresh.
            }
            is Outcome.Failure -> Log.w(TAG, "config-changed: failed to read /config: ${outcome.error}")
        }
    }

    private suspend fun handleCommandIssued(payload: PushPayload) {
        val cmdId = payload.extra?.get("cmdId")?.let { it.toString().trim('"') }
        if (cmdId.isNullOrEmpty()) {
            Log.w(TAG, "command-issued without cmdId in extras: ${payload.extra}")
            return
        }
        val outcome = backend.readDoc(DocPath.LinkCommand(payload.linkId, cmdId))
        when (outcome) {
            is Outcome.Success -> Log.i(TAG, "command received: cmdId=$cmdId (dispatch in spec 009)")
            is Outcome.Failure -> Log.w(TAG, "command-issued: failed to read /commands/$cmdId: ${outcome.error}")
        }
    }

    companion object {
        private const val TAG = "LauncherPushReceiver"
    }
}
