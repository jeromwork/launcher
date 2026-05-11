# Spec 007 — integration tests, deferred runtime

Status: **code-ready, runtime-deferred.**

The Firestore Emulator (T097/T098) and the local Worker × Emulator stack
(T099) require **JDK 21+**: `firebase-tools` 14+ drops Java 8 support and
will refuse to boot the emulator with a clear error
(`Java major version must be at least 21`). The current development host
ships JDK 1.8 only, so this tier is locked behind that upgrade.

What we did instead today, to keep the gate honest without a JDK
upgrade:

1. **T101 — in-process E2E** runs against two `PairingService` instances
   over a single `FakeRemoteSyncBackend` and verifies the full
   pair → consent → admin-config-write → push-receive path; see
   [`PairingEndToEndTest`](../../core/src/commonTest/kotlin/com/launcher/api/pairing/PairingEndToEndTest.kt).
   Runs in <100 ms on every dev machine and on CI.
2. **T097/T098/T099** are scoped here with ready-to-drop test sources +
   a deterministic runbook so the only remaining work post-JDK-upgrade is
   `gradle :core:connectedAndroidTest` and reading the report.

## T096 — Firebase Emulator config (verified)

[`firebase.json`](../../firebase.json) ships an `emulators` block:

```json
{
  "firestore": { "port": 8080 },
  "auth":      { "port": 9099 },
  "ui":        { "enabled": false },
  "singleProjectMode": true
}
```

Start command (run from repo root):

```sh
firebase emulators:start --only firestore,auth --project demo-test
```

Smoke output to look for:

```
✔  emulators: All emulators ready! It is now safe to connect your app.
✔  firestore: Firestore Emulator listening on http://127.0.0.1:8080
✔  auth:      Authentication Emulator listening on http://127.0.0.1:9099
```

## T097 — `FirebaseRemoteSyncBackend × Firestore Emulator`

Drop the file below into
`core/src/androidInstrumentedTestRealBackend/kotlin/com/launcher/adapters/sync/FirebaseRemoteSyncBackendIntegrationTest.kt`
once a JDK-21 + connectedAndroidTest pipeline exists.

Source-set wiring (one-time, to be added to `core/build.gradle.kts` as
part of the rollout):

```kotlin
android {
    sourceSets {
        getByName("androidTestRealBackend").apply {
            java.srcDir("src/androidInstrumentedTestRealBackend/kotlin")
        }
    }
}
dependencies {
    "androidTestRealBackendImplementation"(libs.androidx.test.runner) // androidx.test:runner:1.6.x
    "androidTestRealBackendImplementation"(libs.androidx.test.ext.junit)
    "androidTestRealBackendImplementation"(libs.kotlinx.coroutines.test)
    "androidTestRealBackendImplementation"(libs.firebase.firestore.ktx)
    "androidTestRealBackendImplementation"(libs.firebase.auth.ktx)
}
```

Test source:

```kotlin
package com.launcher.adapters.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.launcher.api.pairing.PairingToken
import com.launcher.api.result.Outcome
import com.launcher.api.sync.BackendError
import com.launcher.api.sync.DocPath
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class FirebaseRemoteSyncBackendIntegrationTest {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var backend: FirebaseRemoteSyncBackend

    @Before
    fun setUp() {
        FirebaseApp.initializeApp(
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context,
        )
        firestore = FirebaseFirestore.getInstance().apply {
            firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setHost("10.0.2.2:8080")           // emulator host from the device
                .setSslEnabled(false)
                .setPersistenceEnabled(false)
                .build()
            clearPersistence()
        }
        backend = FirebaseRemoteSyncBackend(firestore)
    }

    @After
    fun tearDown() = runTest { backend.dispose() }

    @Test
    fun writeDoc_then_readDoc_roundtrips_full_payload() = runTest {
        val token = PairingToken("ABCD23")
        val path = DocPath.Pairings(token)
        val payload = buildJsonObject {
            put("managedDeviceFirebaseUid", JsonPrimitive("managed-uid"))
            put("expiresAt",                  JsonPrimitive(1_700_000_000_000L))
            put("claimed",                    JsonPrimitive(false))
        }

        val write = backend.writeDoc(path, payload, schemaVersion = 1)
        assertIs<Outcome.Success<Unit>>(write)

        val read = backend.readDoc(path)
        val snap = assertIs<Outcome.Success<*>>(read).value
        assertNotNull(snap)
        assertEquals(1, snap.schemaVersion)
        assertEquals(JsonPrimitive("managed-uid"), (snap.data as Map<*, *>)["managedDeviceFirebaseUid"])
    }

    @Test
    fun deleteDoc_removes_the_doc() = runTest {
        val path = DocPath.Pairings(PairingToken("XYZF34"))
        backend.writeDoc(path, buildJsonObject { put("k", JsonPrimitive("v")) }, schemaVersion = 1)
        backend.deleteDoc(path)
        val read = backend.readDoc(path)
        val snap = assertIs<Outcome.Success<*>>(read).value
        assertNull(snap)
    }

    @Test
    fun observe_emits_initial_then_update() = runTest {
        val path = DocPath.Pairings(PairingToken("OBSERV"))
        backend.writeDoc(path, buildJsonObject { put("v", JsonPrimitive(1)) }, schemaVersion = 1)

        val first = backend.observe(path).first()
        val firstSnap = assertIs<Outcome.Success<*>>(first).value
        assertNotNull(firstSnap)
        assertTrue(!firstSnap.isStale, "live read must not be stale")

        // Verifying observer-receives-update would normally use a turbine
        // or a kotlinx.coroutines test flow collector. Keeping the simple
        // shape here so the test stays JDK-21-only and not turbine-coupled.
    }

    @Test
    fun runTransaction_commits_atomically() = runTest {
        val path = DocPath.Pairings(PairingToken("TXATOM"))
        val outcome = backend.runTransaction {
            // first read (must succeed; doc absent) — then write.
            val existing = readDoc(path)
            assertNull(existing)
            writeDoc(path, buildJsonObject { put("claimed", JsonPrimitive(true)) }, schemaVersion = 1)
        }
        assertIs<Outcome.Success<Unit>>(outcome)
        val after = backend.readDoc(path)
        assertNotNull(assertIs<Outcome.Success<*>>(after).value)
    }
}
```

## T098 — claim transaction atomicity + race

Same source set as T097. Two `FirebaseRemoteSyncBackend` instances over
**two distinct** `FirebaseFirestore` instances (Firestore caches per
`FirebaseApp`, so to simulate two clients we boot two named apps).

```kotlin
@RunWith(AndroidJUnit4::class)
class PairingClaimRaceIntegrationTest {

    private lateinit var admin1Backend: FirebaseRemoteSyncBackend
    private lateinit var admin2Backend: FirebaseRemoteSyncBackend
    private lateinit var managedBackend: FirebaseRemoteSyncBackend

    @Before
    fun setUp() {
        admin1Backend  = bootEmulatorBackend("admin1")
        admin2Backend  = bootEmulatorBackend("admin2")
        managedBackend = bootEmulatorBackend("managed")
    }

    @Test
    fun only_one_admin_claims_when_both_race() = runTest {
        val token = PairingToken("RACEAA")
        // Managed creates /pairings/{token}.
        managedBackend.writeDoc(
            DocPath.Pairings(token),
            buildJsonObject {
                put("managedDeviceFirebaseUid", JsonPrimitive("managed-uid"))
                put("expiresAt", JsonPrimitive(System.currentTimeMillis() + 60_000))
                put("claimed",   JsonPrimitive(false))
            },
            schemaVersion = 1,
        )

        // Both admins run claim transactions concurrently.
        val deferred1 = async { claim(admin1Backend, token, adminUid = "admin-1") }
        val deferred2 = async { claim(admin2Backend, token, adminUid = "admin-2") }
        val r1 = deferred1.await()
        val r2 = deferred2.await()

        val wins = listOf(r1, r2).count { it is Outcome.Success<*> }
        val loses = listOf(r1, r2).count { it is Outcome.Failure<*> }
        assertEquals(1, wins, "exactly one claim must win")
        assertEquals(1, loses, "exactly one claim must lose with TransactionConflict")
    }

    private suspend fun claim(
        backend: FirebaseRemoteSyncBackend,
        token: PairingToken,
        adminUid: String,
    ): Outcome<Unit, BackendError> = backend.runTransaction {
        val read = readDoc(DocPath.Pairings(token))
        val snap = checkNotNull(read) { "pairings doc missing" }
        val data = snap.data as Map<*, *>
        check(data["claimed"] == JsonPrimitive(false)) { "already claimed" }
        writeDoc(
            DocPath.Pairings(token),
            buildJsonObject {
                put("managedDeviceFirebaseUid", JsonPrimitive(data["managedDeviceFirebaseUid"].toString()))
                put("expiresAt", data["expiresAt"] as JsonPrimitive)
                put("claimed",   JsonPrimitive(true))
                put("adminId",   JsonPrimitive(adminUid))
                put("linkId",    JsonPrimitive("link-$adminUid"))
            },
            schemaVersion = 1,
        )
    }
}
```

## T099 — Worker × Emulator stack

`push-worker` already has a 10-test vitest suite that mocks the Firebase
REST API directly — see [`push-worker/test/worker.test.ts`](../../push-worker/test/worker.test.ts).
The end-to-end variant **also requires the auth emulator** so the Worker
can verify a real Firebase ID token. Runbook:

```sh
# Terminal 1 — Firebase Emulator (Firestore + Auth).
firebase emulators:start --only firestore,auth --project demo-test

# Terminal 2 — Worker dev server pointing at the local emulator.
cd push-worker
WORKER_FIREBASE_PROJECT_ID=demo-test \
  WORKER_FCM_BASE_URL=http://127.0.0.1:9999/fcm \   # local mock FCM
  wrangler dev --local

# Terminal 3 — emit a token via Auth emulator and POST /notify.
node scripts/issue-token.js | xargs -I {} curl \
  -H "Authorization: Bearer {}" \
  -d '{"linkId":"link-ABC123","type":"config-changed"}' \
  http://127.0.0.1:8787/notify
```

Acceptance: HTTP 200; mock-FCM server logs the body
`{"message":{"topic":"link-link-ABC123","data":{"type":"config-changed"}}}`.

A `scripts/issue-token.js` helper is one half-screen of `firebase-admin`
SDK code; out of scope for this commit since the runtime is JDK-blocked
anyway.

## T100 — CI workflow

See [`.github/workflows/integration-tests.yml`](../../.github/workflows/integration-tests.yml).
The workflow is wired but currently has `if: false` on the job so it
does not gate PRs while JDK 21 is unavailable on dev machines. Flip to
`if: true` after the JDK upgrade.

## Closing checklist before T097/T098 ship green

- [ ] Dev machine on JDK 21+ (`java -version` reports `21.x.x` or higher).
- [ ] `androidInstrumentedTestRealBackend` source set added to
      `core/build.gradle.kts` (snippet above).
- [ ] An Android emulator booted via the `android-emulator` skill before
      `gradle :core:connectedAndroidTestRealBackendDebug` is invoked.
- [ ] Firebase Emulator started in a parallel terminal
      (`firebase emulators:start --only firestore,auth --project demo-test`).
- [ ] Flip CI gate `if: false` → `if: true` in
      `.github/workflows/integration-tests.yml`.
