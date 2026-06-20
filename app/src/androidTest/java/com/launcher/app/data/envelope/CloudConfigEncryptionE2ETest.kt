package com.launcher.app.data.envelope

import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import family.keys.api.AsyncConfigPushQueue
import family.keys.api.AuthIdentity
import family.keys.api.ConfigSaver
import family.keys.api.DeviceId
import family.keys.api.EnvelopeBootstrap
import family.keys.api.IdentityError
import family.keys.api.IdentityProof
import family.keys.api.Outcome
import family.keys.api.RemoteStorage
import family.keys.api.internal.DeviceIdentity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.context.loadKoinModules
import org.koin.core.context.unloadKoinModules
import org.koin.dsl.module
import java.util.UUID

/**
 * F-5b end-to-end test against the realBackend variant.
 *
 * **Two run modes**:
 *
 * 1. **Local Firebase Emulator** (no cloud, no quota):
 *    ```
 *    firebase emulators:start --only firestore,auth          # terminal 1
 *    ./gradlew :app:connectedRealBackendDebugAndroidTest \
 *        -PuseFirebaseEmulator=true                          # terminal 2
 *    ```
 *
 * 2. **Real `launcher-old-dev` Firestore** (network round-trip, real rules):
 *    ```
 *    ./gradlew :app:connectedRealBackendDebugAndroidTest    # no -P flag
 *    ```
 *    Each test seeds + cleans up its own UUID-scoped namespace; the dev
 *    project is left clean after the run finishes.
 *
 * **Covers**:
 *  - SC-001 acceptance: opacity grep on raw Firestore document body
 *    (Bobby Tables 555-1234 plaintext must not leak).
 *  - Roundtrip: saveOwn → loadOwn → byte-equal.
 *  - Envelope structure: recipientKeys map carries this device's [DeviceId].
 */
@RunWith(AndroidJUnit4::class)
class CloudConfigEncryptionE2ETest {

    private val app = ApplicationProvider.getApplicationContext<com.launcher.app.LauncherApplication>()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    private val configSaver: ConfigSaver by lazy { GlobalContext.get().get<ConfigSaver>() }
    private val remoteStorage: RemoteStorage by lazy { GlobalContext.get().get<RemoteStorage>() }
    private val envelopeBootstrap: EnvelopeBootstrap by lazy { GlobalContext.get().get<EnvelopeBootstrap>() }
    private val identityProof: IdentityProof by lazy { GlobalContext.get().get<IdentityProof>() }
    private val deviceIdentity: DeviceIdentity by lazy { GlobalContext.get().get<DeviceIdentity>() }

    private val testUid: String = "e2e-test-${UUID.randomUUID()}"
    private val testConfigName: String = "default"

    /**
     * Test-only [IdentityProof] backed by `FirebaseAuth.currentUser`. The prod
     * adapter ([com.launcher.app.data.identity.GoogleSignInIdentityProof]) only
     * knows about identities obtained through Credential Manager + Google
     * Sign-In, so anonymous Firebase users (emulator-only) are invisible to
     * it. We swap this in via Koin module overlay in [setUp] and remove it
     * in [tearDown].
     */
    private class FirebaseAuthIdentityProof(
        private val auth: FirebaseAuth
    ) : IdentityProof {
        private val state = MutableStateFlow(currentSnapshot())
        init { auth.addAuthStateListener { state.value = currentSnapshot() } }
        override suspend fun currentIdentity(): AuthIdentity? = currentSnapshot()
        override val identityFlow: Flow<AuthIdentity?> = state.asStateFlow()
        override suspend fun requestSignIn(): Outcome<AuthIdentity, IdentityError> =
            currentIdentity()?.let { Outcome.Success(it) }
                ?: Outcome.Failure(IdentityError.NoSupportedProvider)
        override suspend fun signOut(): Outcome<Unit, IdentityError> {
            auth.signOut()
            return Outcome.Success(Unit)
        }
        private fun currentSnapshot(): AuthIdentity? = auth.currentUser
            ?.takeIf { it.uid.isNotEmpty() }
            ?.let {
                AuthIdentity(
                    stableId = it.uid,
                    displayName = it.displayName ?: "anonymous",
                    email = it.email ?: ""
                )
            }
    }

    private val testIdentityModule = module {
        single<IdentityProof>(createdAtStart = true) { FirebaseAuthIdentityProof(auth) }
        // Replace the WorkManager-backed queue with a synchronous in-memory
        // one so the test does not have to wait on background scheduling /
        // network-constraint deferral on a real device.
        single<AsyncConfigPushQueue>(createdAtStart = true) {
            InMemoryAsyncConfigPushQueueImpl(storage = get<RemoteStorage>())
        }
        // Rebuild downstream singletons so they pick up the new IdentityProof
        // and queue above (Koin caches per-definition; if we only swap
        // IdentityProof, the prod-wired EnvelopeBootstrap / ConfigSaver keep
        // their original closed-over references).
        single<ConfigSaver>(createdAtStart = true) {
            family.keys.impl.LocalFirstConfigSaver(
                storage = get<RemoteStorage>(),
                identity = get<IdentityProof>(),
                pushQueue = get<AsyncConfigPushQueue>()
            )
        }
        single<EnvelopeBootstrap>(createdAtStart = true) {
            family.keys.impl.DefaultEnvelopeBootstrap(
                identity = get<IdentityProof>(),
                deviceIdentity = get<DeviceIdentity>(),
                directory = get<family.keys.api.internal.PublicKeyDirectory>()
            )
        }
    }

    @Before
    fun setUp() = runBlocking {
        // Best-effort sign-out only if the current user is anonymous (emulator
        // path) so each run mints a fresh uid. On real cloud we keep the
        // existing Google Sign-In user — anonymous sign-in is disabled
        // (decision 2026-05-30) and we'd lose the only authenticated identity.
        if (auth.currentUser?.isAnonymous == true) {
            auth.signOut()
        }
        // Swap IdentityProof with the FirebaseAuth-backed one so the prod
        // ConfigSaver / EnvelopeBootstrap can see whichever user is signed in.
        loadKoinModules(testIdentityModule)
    }

    @After
    fun tearDown() = runBlocking {
        // Cleanup: delete the test envelope + device entry. On real cloud this
        // keeps the dev project tidy across repeated test runs.
        if (auth.currentUser?.uid != null) {
            try {
                envelopeBootstrap.teardown()
            } catch (_: Throwable) { /* best effort */ }
        }
        unloadKoinModules(testIdentityModule)
    }

    @Test
    fun roundtripOwnConfigByteEqual() = runBlocking {
        signInAnonymouslyOrSkip()
        val boot = envelopeBootstrap.bootstrap()
        assertTrue("bootstrap must succeed, got $boot", boot is Outcome.Success)
        val uid = auth.currentUser!!.uid
        val key = family.keys.api.ConfigSaver.keyOf(testConfigName)
        val payload = "owner config payload — version ${System.currentTimeMillis()}".encodeToByteArray()
        // Drive the storage layer directly to surface adapter errors verbatim
        // (ConfigSaver wraps everything to StorageError.Network with no cause).
        val save = remoteStorage.put(uid, key, payload)
        assertTrue("remoteStorage.put must succeed, got $save", save is Outcome.Success)

        val load = remoteStorage.get(uid, key)
        assertTrue("remoteStorage.get must succeed, got $load", load is Outcome.Success)
        val opened = (load as Outcome.Success<ByteArray>).value
        assertEquals(
            "byte-equal roundtrip",
            payload.toList(),
            opened.toList()
        )
    }

    @Test
    fun sc001OpacityRawFirestoreDocDoesNotLeakPlaintext() = runBlocking {
        signInAnonymouslyOrSkip()
        val boot = envelopeBootstrap.bootstrap()
        assertTrue("bootstrap must succeed, got $boot", boot is Outcome.Success)

        val uid = auth.currentUser!!.uid
        val key = family.keys.api.ConfigSaver.keyOf(testConfigName)
        val marker = "Bobby Tables 555-1234"
        val payload = """{"contact": {"name":"prefix:$marker:suffix"}}""".encodeToByteArray()
        val save = remoteStorage.put(uid, key, payload)
        assertTrue("save must succeed, got $save", save is Outcome.Success)
        Thread.sleep(2_000)

        // Direct Firestore read — bypass envelope.open(), see raw fields.
        val docId = base64UrlNoPad("config/$testConfigName")
        val doc = firestore.document("users/$uid/data/$docId").get().await()
        assertTrue("envelope document must exist", doc.exists())
        val data = doc.data ?: error("data null")

        // SC-001: marker MUST NOT appear in any field's string representation.
        val flat = data.toString()
        assertFalse(
            "SC-001 plaintext leaked into raw Firestore body: $flat",
            flat.contains(marker)
        )

        // Confirm recipientKeys carries this device's DeviceId (sanity that envelope was built).
        @Suppress("UNCHECKED_CAST")
        val recipientKeys = data["recipientKeys"] as? Map<String, Any> ?: error("recipientKeys missing")
        val thisDeviceId = deviceIdentity.thisDeviceId().value
        assertNotNull(
            "this device must be a recipient of the envelope it wrote",
            recipientKeys[thisDeviceId]
        )
    }

    // ─── helpers ───────────────────────────────────────────────────────────

    /**
     * Try to sign in for the test. On the Firebase Emulator anonymous sign-in
     * is unconditionally available; on real cloud anonymous is disabled
     * (decision 2026-05-30), so the test is skipped if an authenticated user
     * is not already present.
     */
    private suspend fun signInAnonymouslyOrSkip() {
        if (auth.currentUser != null) return
        try {
            auth.signInAnonymously().await()
        } catch (t: Throwable) {
            org.junit.Assume.assumeNoException(
                "Anonymous sign-in not available (real cloud — decision 2026-05-30). " +
                    "Run this test with -PuseFirebaseEmulator=true.",
                t
            )
        }
    }

    private fun base64UrlNoPad(s: String): String =
        Base64.encodeToString(
            s.encodeToByteArray(),
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        )
}
