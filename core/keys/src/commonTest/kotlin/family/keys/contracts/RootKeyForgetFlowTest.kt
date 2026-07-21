package family.keys.contracts

import family.keys.api.AuthIdentity
import family.keys.api.Outcome
import family.keys.fakes.FakeKeyRegistry
import family.keys.fakes.FakeRootKeyManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Contract test: RootKey forget flow (T627, FR-019, SC-012).
 *
 * **Cascade semantics (R-post-review clarification)**: cascade wipe is
 * **caller-orchestrated**, not automatic — [RootKeyManager.forget] wipes only
 * the root-key surface; the caller must also invoke
 * [family.keys.api.KeyRegistry.wipeAll] and
 * [family.keys.api.RecoveryKeyBackup.deleteBlob] (see KDoc on
 * `RootKeyManager.forget`). The tests below exercise that orchestration end-to-end
 * rather than implying that `forget()` alone empties the registry.
 *
 *  - `forget()` + `registry.wipeAll()` → `KeyRegistry.list(stableId)` returns empty
 *  - `forget()` alone → `RootKeyManager.current` emits null
 */
class RootKeyForgetFlowTest {

    private val identity = AuthIdentity("00000000-0000-4000-8000-000000000001", null, null)

    @Test
    fun forgetWithCallerOrchestratedRegistryWipeClearsNamespace() = runTest {
        val registry = FakeKeyRegistry()
        val rootMgr = FakeRootKeyManager()

        // Setup: derive some keys.
        rootMgr.create(identity)
        registry.derive(identity.stableId, "config")
        registry.derive(identity.stableId, "contacts")

        // Verify keys exist before forget.
        val beforeList = (registry.list(identity.stableId) as Outcome.Success).value
        assertTrue(beforeList.isNotEmpty(), "Keys must exist before forget")

        // Forget: wipe both rootMgr and registry.
        rootMgr.forget(identity)
        registry.wipeAll(identity.stableId)

        // After forget: registry must be empty (SC-012).
        val afterList = (registry.list(identity.stableId) as Outcome.Success).value
        assertTrue(afterList.isEmpty(), "After forget, KeyRegistry.list MUST be empty (SC-012)")
    }

    @Test
    fun forgetEmitsNullFromCurrentFlow() = runTest {
        val rootMgr = FakeRootKeyManager()

        // Create key → current becomes non-null.
        rootMgr.create(identity)

        // Forget → current must emit null.
        rootMgr.forget(identity)
        val currentValue = rootMgr.current.first()
        assertNull(currentValue, "After forget, current MUST emit null (FR-019)")
    }

    @Test
    fun forgetDoesNotAffectOtherIdentities() = runTest {
        val registry = FakeKeyRegistry()
        val rootMgr = FakeRootKeyManager()

        val aliceId = AuthIdentity("00000000-0000-4000-8000-000000000001", null, null)
        val bobId = AuthIdentity("00000000-0000-4000-8000-000000000002", null, null)

        rootMgr.create(aliceId)
        rootMgr.create(bobId)
        registry.derive(aliceId.stableId, "config")
        registry.derive(bobId.stableId, "config")

        // Forget alice only.
        rootMgr.forget(aliceId)
        registry.wipeAll(aliceId.stableId)

        // Bob's namespace untouched.
        val bobList = (registry.list(bobId.stableId) as Outcome.Success).value
        assertTrue(bobList.isNotEmpty(), "Bob's namespace MUST not be affected by alice forget (FR-031)")
    }
}
