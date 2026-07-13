package cryptokit.keys.contracts

import cryptokit.keys.api.AuthIdentity
import cryptokit.keys.api.IdentityError
import cryptokit.keys.api.Outcome
import cryptokit.keys.fakes.FakeIdentityProof
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * Contract test для [cryptokit.keys.api.IdentityProof] (T030, FR-006).
 */
class IdentityProofContractTest {

    private val testIdentity = AuthIdentity(
        stableId = "test-uid-abc-123",
        displayName = "Test User",
        email = "test@example.com"
    )

    @Test
    fun currentIdentityIsNullBeforeSignIn() = runTest {
        val proof = FakeIdentityProof(initialIdentity = null)
        assertNull(proof.currentIdentity())
    }

    @Test
    fun requestSignInUpdatesCurrentIdentity() = runTest {
        val proof = FakeIdentityProof(signInResult = Outcome.Success(testIdentity))

        val result = proof.requestSignIn()
        assertIs<Outcome.Success<AuthIdentity>>(result)
        assertEquals(testIdentity, result.value)

        assertEquals(testIdentity, proof.currentIdentity())
    }

    @Test
    fun signOutClearsIdentity() = runTest {
        val proof = FakeIdentityProof(
            initialIdentity = testIdentity,
            signInResult = Outcome.Success(testIdentity)
        )
        assertEquals(testIdentity, proof.currentIdentity())

        val out = proof.signOut()
        assertIs<Outcome.Success<Unit>>(out)
        assertNull(proof.currentIdentity())
    }

    @Test
    fun signInCancelledSurfacesError() = runTest {
        val proof = FakeIdentityProof(signInResult = Outcome.Failure(IdentityError.Cancelled))
        val result = proof.requestSignIn()
        assertIs<Outcome.Failure<IdentityError>>(result)
        assertEquals(IdentityError.Cancelled, result.error)
        assertNull(proof.currentIdentity())
    }

    @Test
    fun identityFlowEmitsCurrentValue() = runTest {
        val proof = FakeIdentityProof(initialIdentity = testIdentity)
        val emitted = proof.identityFlow.first()
        assertEquals(testIdentity, emitted)
    }
}
