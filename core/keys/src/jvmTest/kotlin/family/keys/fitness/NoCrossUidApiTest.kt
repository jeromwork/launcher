package family.keys.fitness

import family.keys.api.ConfigCipher
import family.keys.api.KeyRegistry
import family.keys.api.RootKeyManager
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Fitness function — strict identity isolation (T122a, FR-031a, G-3 finding).
 *
 * Public API портов KeyRegistry / RootKeyManager / ConfigCipher MUST NOT иметь
 * методов принимающих **два** String/AuthIdentity параметра (cross-UID operation).
 *
 * Sanity check через Java reflection — простой и достаточный для текущего scope.
 * Расширяется на Detekt custom rule если portов больше.
 */
class NoCrossUidApiTest {

    @Test
    fun keyRegistryHasNoCrossUidMethod() {
        assertNoCrossIdentityApi(KeyRegistry::class.java)
    }

    @Test
    fun rootKeyManagerHasNoCrossUidMethod() {
        assertNoCrossIdentityApi(RootKeyManager::class.java)
    }

    @Test
    fun configCipherHasNoCrossUidMethod() {
        assertNoCrossIdentityApi(ConfigCipher::class.java)
    }

    private fun assertNoCrossIdentityApi(clazz: Class<*>) {
        for (m in clazz.declaredMethods) {
            val identityLikeParams = m.parameterTypes.count { paramType ->
                // Считаем String параметры и AuthIdentity параметры как "identity-bound".
                paramType == String::class.java ||
                    paramType.simpleName == "AuthIdentity"
            }
            assertTrue(
                identityLikeParams <= 1,
                "Method ${clazz.simpleName}.${m.name} has $identityLikeParams identity-like " +
                    "parameters — cross-UID API forbidden (FR-031a)"
            )
        }
    }
}
