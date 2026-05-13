package com.launcher.fake.identity

import com.launcher.api.identity.AdminIdentity
import com.launcher.api.identity.Identity
import com.launcher.api.identity.IdentityError
import com.launcher.api.identity.IdentityProvider
import com.launcher.api.identity.ManagedIdentity
import com.launcher.api.result.Outcome

/**
 * Deterministic [IdentityProvider] for tests and `mockBackend` flavor (FR-012).
 *
 * The role (admin vs Managed) is set on construction so the same Fake can be
 * used to simulate either side of a pairing flow in two parallel "device"
 * instances inside one test.
 */
class FakeIdentityProvider(
    private val role: Role,
    private val seedUid: String,
) : IdentityProvider {

    enum class Role { Admin, Managed }

    private var current: Identity? = null

    /** Forces a specific UID for the next [signInAnonymous] call. Useful for
     *  tests that need to exercise UID rotation (e.g. simulated reinstall). */
    fun preloadUid(uid: String) {
        current = build(uid)
    }

    override suspend fun signInAnonymous(): Outcome<Identity, IdentityError> {
        val identity = current ?: build(seedUid).also { current = it }
        return Outcome.Success(identity)
    }

    override fun currentIdentity(): Identity? = current

    override suspend fun signOut() {
        current = null
    }

    private fun build(uid: String): Identity = when (role) {
        Role.Admin -> AdminIdentity(uid)
        Role.Managed -> ManagedIdentity(uid)
    }
}
