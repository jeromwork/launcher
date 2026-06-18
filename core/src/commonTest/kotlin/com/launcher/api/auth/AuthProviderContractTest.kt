package com.launcher.api.auth

import com.launcher.api.result.Outcome
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Semantic-invariant contract suite для **любой** реализации [AuthProvider].
 * Runs против [FakeAuthAdapter] здесь; та же логика выполнится против
 * `GoogleSignInAuthAdapter` в `AuthProviderContractTestGoogle` (T755, Phase 5).
 *
 * Если новый адаптер ломает один из этих инвариантов — это ACL leak
 * (CLAUDE.md §2) или нарушение контракта порта.
 *
 * Per spec 017 contract `auth-provider-port.md`, plan.md §"Test Strategy" #1.
 */
class AuthProviderContractTest {

    private fun newAdapter(users: List<AuthIdentity> = listOf(FakeAuthAdapter.DEFAULT_USER)): AuthProvider =
        FakeAuthAdapter(users)

    @Test
    fun initialCurrentUserIsNull() = runTest {
        val adapter = newAdapter()
        assertNull(adapter.currentUser.first())
    }

    @Test
    fun signInSuccessEmitsIdentity() = runTest {
        val adapter = newAdapter()
        val result = adapter.signIn()
        assertTrue(result is Outcome.Success)
        assertEquals(FakeAuthAdapter.DEFAULT_USER, result.value)
        assertEquals(FakeAuthAdapter.DEFAULT_USER, adapter.currentUser.first())
    }

    @Test
    fun signOutClearsCurrentUser() = runTest {
        val adapter = newAdapter()
        adapter.signIn()
        adapter.signOut()
        assertNull(adapter.currentUser.first())
    }

    @Test
    fun signOutIsIdempotent() = runTest {
        val adapter = newAdapter()
        adapter.signOut()
        adapter.signOut() // no exception, no state change
        assertNull(adapter.currentUser.first())
    }

    @Test
    fun cancelledSignInDoesNotChangeCurrentUser() = runTest {
        val adapter = FakeAuthAdapter()
        adapter.simulateCancellation()
        val before = adapter.currentUser.first()
        val result = adapter.signIn()
        assertTrue(result is Outcome.Failure)
        assertEquals(AuthError.Cancelled, result.error)
        assertEquals(before, adapter.currentUser.first())
    }

    @Test
    fun currentUserReflectsLatestState() = runTest {
        val adapter = FakeAuthAdapter()
        // StateFlow по контракту distinct-until-changed — повторное
        // присваивание того же значения не порождает новой эмиссии.
        assertNull(adapter.currentUser.first())
        adapter.forceCurrent(FakeAuthAdapter.DEFAULT_USER)
        assertEquals(FakeAuthAdapter.DEFAULT_USER, adapter.currentUser.first())
        adapter.forceCurrent(null)
        assertNull(adapter.currentUser.first())
    }
}
