package family.crypto.api.values

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KeyIdTest {

    @Test
    fun acceptsValidConfigKeyId() {
        val id = KeyId("config-admin-identity-v1")
        assertEquals("config-admin-identity-v1", id.raw)
    }

    @Test
    fun acceptsAllNamespaces() {
        KeyId("config-admin-identity-v1")
        KeyId("media-photo-album-v1")
        KeyId("messenger-sender-key-v1")
        KeyId("recovery-escrow-key-v1")
        KeyId("__internal-wrap-key-v1")
    }

    @Test
    fun rejectsKeyIdWithoutKnownPrefix() {
        assertFailsWith<IllegalArgumentException> { KeyId("photo-album-v1") }
    }

    @Test
    fun rejectsUppercaseLetters() {
        assertFailsWith<IllegalArgumentException> { KeyId("Config-Admin-V1") }
    }

    @Test
    fun rejectsEmptyString() {
        assertFailsWith<IllegalArgumentException> { KeyId("") }
    }

    @Test
    fun rejectsTrailingSpecialChars() {
        assertFailsWith<IllegalArgumentException> { KeyId("config-admin!") }
    }

    @Test
    fun keyNamespacePrefixesAreStable() {
        assertEquals(
            listOf("config-", "media-", "messenger-", "recovery-", "__internal-"),
            KeyNamespace.allPrefixes()
        )
    }
}
