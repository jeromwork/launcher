package com.launcher.app.firebase

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.launcher.app.BuildConfig

/**
 * Routes Firebase SDK calls to the local Firebase Emulator instead of the
 * cloud `launcher-old-dev` project. Active only when the app was built with
 * `-PuseFirebaseEmulator=true` (see [com.launcher.app.BuildConfig.USE_FIREBASE_EMULATOR]).
 *
 * Reached from `LauncherApplication.wireFirebaseEmulatorIfRequested()` via
 * reflection so the main source set does not have a compile-time dependency
 * on Firebase types (mockBackend variant must build without them).
 *
 * **Host** comes from `BuildConfig.FIREBASE_EMULATOR_HOST` (gradle prop
 * `-PfirebaseEmulatorHost=<host>`):
 *  - `10.0.2.2` (default) — Android AVD loopback to the host OS.
 *  - `127.0.0.1` — real USB-connected device with
 *    `adb reverse tcp:8080 tcp:8080 && adb reverse tcp:9099 tcp:9099`.
 *  - `<LAN IP>` — real device on the same Wi-Fi as the host. Firestore
 *    Emulator listens on all interfaces; Auth Emulator may need
 *    `firebase emulators:start --inspect-functions` style options.
 *
 * **Ports**: 8080 Firestore, 9099 Auth — defaults of `firebase.json`.
 */
object FirebaseEmulatorWiring {

    @Volatile
    private var wired: Boolean = false

    /**
     * Idempotent: subsequent calls are no-ops. Called via reflection from
     * main-set code; `@JvmStatic` keeps the method signature stable for
     * `Class.forName(...).getMethod("apply").invoke(null)`.
     */
    @JvmStatic
    fun apply() {
        if (wired) return
        synchronized(this) {
            if (wired) return
            val host = BuildConfig.FIREBASE_EMULATOR_HOST
            FirebaseFirestore.getInstance().useEmulator(host, PORT_FIRESTORE)
            FirebaseAuth.getInstance().useEmulator(host, PORT_AUTH)
            wired = true
            Log.i(TAG, "Firebase SDK redirected to emulator at $host:$PORT_FIRESTORE (Firestore) and :$PORT_AUTH (Auth)")
        }
    }

    private const val TAG: String = "FirebaseEmulatorWiring"
    private const val PORT_FIRESTORE: Int = 8080
    private const val PORT_AUTH: Int = 9099
}
