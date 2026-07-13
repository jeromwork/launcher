package com.launcher.app.data.identity

import com.launcher.api.auth.AuthError
import com.launcher.api.auth.AuthIdentity as F4AuthIdentity
import com.launcher.api.auth.AuthProvider
import com.launcher.api.result.Outcome as F4Outcome
import cryptokit.keys.api.IdentityError
import cryptokit.keys.api.Outcome as F5Outcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests для [GoogleSignInIdentityProof] adapter (F-4 → F-5 bridge).
 */
class GoogleSignInIdentityProofTest {

    private val testF4 = F4AuthIdentity(stableId = "uid-123", displayName = "Alice", email = "a@example.com")

    @Test
    fun currentIdentityMapsF4ToF5() = runBlocking {
        val stub = StubAuthProvider(initial = testF4)
        val proof = GoogleSignInIdentityProof(stub)

        val id = proof.currentIdentity()
        assertEquals("uid-123", id?.stableId)
        assertEquals("Alice", id?.displayName)
        assertEquals("a@example.com", id?.email)
    }

    @Test
    fun nullCurrentUserMapsToNull() = runBlocking {
        val stub = StubAuthProvider(initial = null)
        val proof = GoogleSignInIdentityProof(stub)
        assertNull(proof.currentIdentity())
    }

    @Test
    fun signInSuccessReturnsF5Identity() = runBlocking {
        val stub = StubAuthProvider(initial = null, signInResult = F4Outcome.Success(testF4))
        val proof = GoogleSignInIdentityProof(stub)

        val r = proof.requestSignIn()
        assertTrue("expected Success but got $r", r is F5Outcome.Success)
        assertEquals("uid-123", (r as F5Outcome.Success).value.stableId)
    }

    @Test
    fun cancelledMapsToIdentityErrorCancelled() = runBlocking {
        val stub = StubAuthProvider(initial = null, signInResult = F4Outcome.Failure(AuthError.Cancelled))
        val proof = GoogleSignInIdentityProof(stub)

        val r = proof.requestSignIn()
        assertTrue(r is F5Outcome.Failure)
        assertEquals(IdentityError.Cancelled, (r as F5Outcome.Failure).error)
    }

    @Test
    fun providerUnavailableMapsToNoSupportedProvider() = runBlocking {
        val stub = StubAuthProvider(initial = null, signInResult = F4Outcome.Failure(AuthError.ProviderUnavailable))
        val proof = GoogleSignInIdentityProof(stub)

        val r = proof.requestSignIn()
        assertTrue(r is F5Outcome.Failure)
        assertEquals(IdentityError.NoSupportedProvider, (r as F5Outcome.Failure).error)
    }

    @Test
    fun networkErrorMapsToFailure() = runBlocking {
        val stub = StubAuthProvider(initial = null, signInResult = F4Outcome.Failure(AuthError.NetworkError))
        val proof = GoogleSignInIdentityProof(stub)

        val r = proof.requestSignIn()
        assertTrue(r is F5Outcome.Failure)
        assertTrue((r as F5Outcome.Failure).error is IdentityError.Failure)
    }

    @Test
    fun signOutSucceeds() = runBlocking {
        val stub = StubAuthProvider(initial = testF4)
        val proof = GoogleSignInIdentityProof(stub)

        val r = proof.signOut()
        assertTrue(r is F5Outcome.Success)
        assertNull(proof.currentIdentity())
    }

    @Test
    fun identityFlowEmitsF5Mapping() = runBlocking {
        val stub = StubAuthProvider(initial = testF4)
        val proof = GoogleSignInIdentityProof(stub)
        val first = proof.identityFlow.first()
        assertEquals("uid-123", first?.stableId)
    }
}

private class StubAuthProvider(
    initial: F4AuthIdentity? = null,
    private val signInResult: F4Outcome<F4AuthIdentity, AuthError> = F4Outcome.Failure(AuthError.Cancelled)
) : AuthProvider {
    private val state = MutableStateFlow(initial)
    override val currentUser: Flow<F4AuthIdentity?> = state
    override suspend fun signIn(): F4Outcome<F4AuthIdentity, AuthError> {
        if (signInResult is F4Outcome.Success) state.value = signInResult.value
        return signInResult
    }
    override suspend fun signOut() {
        state.value = null
    }
}
