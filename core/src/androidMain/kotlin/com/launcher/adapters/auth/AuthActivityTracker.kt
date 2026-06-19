package com.launcher.adapters.auth

import android.app.Activity
import android.app.Application
import android.os.Bundle

/**
 * Регистрирует Application.ActivityLifecycleCallbacks, чтобы заполнять
 * [ActivityHolder] текущей foreground Activity.
 *
 * Это нужно [GoogleSignInAuthAdapter] (realBackend only) — Credential Manager
 * требует Activity-based Context. В mockBackend [FakeAuthProvider] не
 * нуждается в Activity, callback просто заполняет holder без consumer'ов.
 *
 * Вызывается из `LauncherApplication.onCreate` для обоих flavors —
 * безопасно, идемпотентно, mockBackend не тратит ресурсы (holder = WeakReference).
 */
fun Application.installAuthActivityTracker() {
    registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
        override fun onActivityStarted(activity: Activity) {}
        override fun onActivityResumed(activity: Activity) {
            ActivityHolder.set(activity)
        }
        override fun onActivityPaused(activity: Activity) {
            ActivityHolder.clear(activity)
        }
        override fun onActivityStopped(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        override fun onActivityDestroyed(activity: Activity) {
            ActivityHolder.clear(activity)
        }
    })
}
