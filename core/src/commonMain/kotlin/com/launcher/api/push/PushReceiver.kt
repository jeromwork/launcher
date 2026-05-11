package com.launcher.api.push

/**
 * Port (spec 007 §FR-037) for handling an incoming push on the Managed side.
 * Implementations route by [PushPayload.type]:
 *
 *  - [PushType.ConfigChanged] — read `/links/{linkId}/config`, log
 *    `received: schemaVersion=N` (spec 008 will apply to Room + UI; TODO
 *    in `PushReceiver` impl).
 *  - [PushType.CommandIssued] — read the command doc, log (spec 009 will
 *    dispatch).
 *  - [PushType.Revoke] — admin-side: refresh paired-devices list.
 *
 * Implementations:
 *  - `LauncherFirebaseMessagingService` (androidMain, `realBackend`) wraps
 *    FCM `onMessageReceived` → `FcmReceiverContract.parseFcmDataMap` → here.
 *  - `FakePushReceiver` (commonTest) — direct call.
 */
interface PushReceiver {
    suspend fun onPush(payload: PushPayload)
}
