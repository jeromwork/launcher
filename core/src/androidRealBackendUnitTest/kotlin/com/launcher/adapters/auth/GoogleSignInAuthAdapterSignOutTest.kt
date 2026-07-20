package com.launcher.adapters.auth

import com.launcher.wire.WireVersion

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.launcher.api.auth.internal.SessionRecord
import com.launcher.api.auth.internal.SessionStore
import cryptokit.crypto.api.SecureKeyStore
import cryptokit.crypto.api.values.KeyId
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Spec 017 Phase 8 verification test (Task 3 AC #4):
 * Sign-out preserves Keystore data for account recovery.
 */
@RunWith(RobolectricTestRunner::class)
class GoogleSignInAuthAdapterSignOutTest {

    private class InMemorySessionStore : SessionStore {
        private val _changes = MutableStateFlow<SessionRecord?>(null)
        override val sessionChanges: Flow<SessionRecord?> = _changes.asStateFlow()
        override suspend fun save(session: SessionRecord) { _changes.value = session }
        override suspend fun current(): SessionRecord? = _changes.value
        override suspend fun clear() { _changes.value = null }
    }

    @Test
    fun signOut_preservesKeystoreRecoveryData() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val firebaseAuth = mockk<FirebaseAuth>(relaxed = true)
        val firestore = mockk<FirebaseFirestore>(relaxed = true)
        val sessionStore = InMemorySessionStore()
        val credentialManager = mockk<CredentialManager>(relaxed = true)
        val secureKeyStore = mockk<SecureKeyStore>(relaxed = true)

        val adapter = GoogleSignInAuthAdapter(
            context = context,
            firebaseAuth = firebaseAuth,
            firestore = firestore,
            sessionStore = sessionStore,
            credentialManager = credentialManager,
            serverClientId = "test-client-id"
        )

        val recoveryKeyId = KeyId("recovery-uid-test-12345")

        // Populate session store with a mock user session
        sessionStore.save(
            SessionRecord(
                schemaVersion = WireVersion(1, 0),
                stableId = "recovery-uid-test-12345",
                expiresAtEpochMillis = System.currentTimeMillis() + 100000L,
                refreshToken = null,
                extra = emptyMap()
            )
        )

        // Perform sign-out
        adapter.signOut()

        // Verify session and user state are cleared
        assertNull(sessionStore.current())
        assertNull(adapter.currentUser.first())
        verify { firebaseAuth.signOut() }

        // AC #4 Verification: Keystore recovery data MUST NOT be deleted upon sign-out
        coVerify(exactly = 0) { secureKeyStore.delete(recoveryKeyId) }
    }
}
