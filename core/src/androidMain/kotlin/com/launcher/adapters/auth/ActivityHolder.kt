package com.launcher.adapters.auth

import android.app.Activity
import java.lang.ref.WeakReference

/**
 * Holder для current foreground Activity, используется
 * [GoogleSignInAuthAdapter] (realBackend only) для запуска Credential Manager
 * bottom-sheet'а (требует Activity-based Context, не application Context).
 *
 * Заполняется через [installAuthActivityTracker] из `LauncherApplication.onCreate`
 * (вызывается **в обоих** flavors — mockBackend просто ничего не использует
 * этот holder, потому что FakeAuthProvider не нуждается в Activity).
 *
 * WeakReference чтобы не держать Activity после destroy.
 *
 * Per spec 017 F-4 wizard integration smoke test (2026-06-19) — обнаружено что
 * application Context не работает для CredentialManager.getCredential():
 * "Failed to launch the selector UI. Hint: ensure the `context` parameter
 * is an Activity-based context."
 */
object ActivityHolder {
    private var ref: WeakReference<Activity>? = null

    fun set(activity: Activity) {
        ref = WeakReference(activity)
    }

    fun clear(activity: Activity) {
        if (ref?.get() === activity) ref = null
    }

    fun current(): Activity? = ref?.get()
}
