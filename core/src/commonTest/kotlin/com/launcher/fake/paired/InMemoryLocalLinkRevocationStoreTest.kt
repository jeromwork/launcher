package com.launcher.fake.paired

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Spec 010 T092 — verifies the [InMemoryLocalLinkRevocationStore] honours
 * the [com.launcher.api.paired.LocalLinkRevocationStore] contract:
 *
 *  - markRevoked / isRevoked roundtrip.
 *  - clearRevoked is idempotent on missing entries.
 *  - markRevoked is idempotent (no duplicate set members).
 *  - revokedLinkIds reflects mutations from both mark + clear.
 *
 * **Persistence-across-process test** is on the Android side
 * (`DataStoreLocalLinkRevocationStorePersistenceTest` — not added in спек 010
 * because Robolectric does not reliably restore Preferences DataStore across
 * a simulated process death; the real verification happens via the manual
 * smoke pass T093 on a физическом устройстве, deferred per
 * `feedback_critical_mentor_stance.md`).
 */
class InMemoryLocalLinkRevocationStoreTest {

    @Test
    fun mark_then_isRevoked_emits_true_then_clear_emits_false() = runTest {
        val store = InMemoryLocalLinkRevocationStore()
        assertFalse(store.isRevoked("link-1").first())

        store.markRevoked("link-1")
        assertTrue(store.isRevoked("link-1").first())
        assertFalse(store.isRevoked("link-2").first(), "other linkIds remain unaffected")

        store.clearRevoked("link-1")
        assertFalse(store.isRevoked("link-1").first())
    }

    @Test
    fun markRevoked_is_idempotent_on_duplicate_inserts() = runTest {
        val store = InMemoryLocalLinkRevocationStore()
        store.markRevoked("link-1")
        store.markRevoked("link-1")
        store.markRevoked("link-1")
        assertEquals(setOf("link-1"), store.snapshot())
    }

    @Test
    fun clearRevoked_no_op_on_missing_entry() = runTest {
        val store = InMemoryLocalLinkRevocationStore()
        store.clearRevoked("never-marked")  // must not crash.
        assertEquals(emptySet(), store.snapshot())
    }

    @Test
    fun revokedLinkIds_reflects_combined_mark_and_clear() = runTest {
        val store = InMemoryLocalLinkRevocationStore()
        store.markRevoked("a")
        store.markRevoked("b")
        store.markRevoked("c")
        store.clearRevoked("b")

        assertEquals(setOf("a", "c"), store.revokedLinkIds().first())
    }

    @Test
    fun initial_seed_carries_into_isRevoked() = runTest {
        val store = InMemoryLocalLinkRevocationStore(initial = setOf("preseeded"))
        assertTrue(store.isRevoked("preseeded").first())
        assertEquals(setOf("preseeded"), store.snapshot())
    }
}
