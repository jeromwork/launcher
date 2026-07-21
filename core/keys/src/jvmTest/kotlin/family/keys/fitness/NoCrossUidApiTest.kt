package family.keys.fitness

import family.keys.api.RootKeyManager
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Fitness function — root key isolation (G-3 finding, FR-031a residue).
 *
 * The root-key management surface ([RootKeyManager]) MUST stay single-identity:
 * one UID per call. Cross-UID operations on root keys would imply shared
 * cryptographic secrets, which violates the F-5 owner model.
 *
 * **F-5b note**: cross-UID is intentional for the **envelope** surface
 * ([family.keys.api.ConfigSaver.saveForOther], [family.keys.api.RemoteStorage]).
 * That layer routes per-grant access through [family.keys.api.internal.RecipientResolver]
 * and Firestore Security Rules, and it does NOT share root-key material —
 * each device decrypts with its own private X25519 key. So the isolation
 * test only applies to root-key APIs, not to the envelope storage surface.
 */
class NoCrossUidApiTest {

    @Test
    fun rootKeyManagerHasNoCrossUidMethod() {
        assertNoCrossIdentityApi(RootKeyManager::class.java)
    }

    private fun assertNoCrossIdentityApi(clazz: Class<*>) {
        for (m in clazz.declaredMethods) {
            val identityLikeParams = m.parameterTypes.count { paramType ->
                paramType == String::class.java ||
                    paramType.simpleName == "AuthIdentity"
            }
            assertTrue(
                identityLikeParams <= 1,
                "Method ${clazz.simpleName}.${m.name} has $identityLikeParams identity-like " +
                    "parameters — cross-UID API forbidden on root-key surface (FR-031a)"
            )
        }
    }
}
