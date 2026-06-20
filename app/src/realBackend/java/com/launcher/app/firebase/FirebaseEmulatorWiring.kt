package com.launcher.app.firebase

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Routes Firebase SDK calls to the local Firebase Emulator instead of the
 * cloud `launcher-old-dev` project. Active only when the app was built with
 * `-PuseFirebaseEmulator=true` (see [com.launcher.app.BuildConfig.USE_FIREBASE_EMULATOR]).
 *
 * Reached from `LauncherApplication.wireFirebaseEmulatorIfRequested()` via
 * reflection so the main source set does not have a compile-time dependency
 * on Firebase types (mockBackend variant must build without them).
 *
 * **Hosts to use**:
 *  - Android emulator AVD: `10.0.2.2` (loopback to host OS where
 *    `firebase emulators:start` runs).
 *  - Real device on the same Wi-Fi: replace with the host's LAN IP and
 *    invoke `setSslEnabled(false)` on Firestore (TODO if needed).
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
            FirebaseFirestore.getInstance().useEmulator(HOST_ANDROID_AVD, PORT_FIRESTORE)
            FirebaseAuth.getInstance().useEmulator(HOST_ANDROID_AVD, PORT_AUTH)
            wired = true
            Log.i(TAG, "Firebase SDK redirected to emulator at $HOST_ANDROID_AVD:$PORT_FIRESTORE (Firestore) and :$PORT_AUTH (Auth)")
        }
    }

    private const val TAG: String = "FirebaseEmulatorWiring"
    private const val HOST_ANDROID_AVD: String = "10.0.2.2"
    private const val PORT_FIRESTORE: Int = 8080
    private const val PORT_AUTH: Int = 9099
}
