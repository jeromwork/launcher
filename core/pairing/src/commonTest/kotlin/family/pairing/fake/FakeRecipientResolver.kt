package family.pairing.fake

import family.pairing.api.DeviceIdentity
import family.pairing.api.RecipientResolver

/**
 * TEST-ONLY [RecipientResolver] with configurable recipients per link —
 * CLAUDE.md §6 mock-first.
 *
 * Usage:
 *   val resolver = FakeRecipientResolver()
 *   resolver.setRecipients("link-1", listOf(peerIdentity))
 *   resolver.resolveRecipients("link-1")  // returns peerIdentity
 */
class FakeRecipientResolver : RecipientResolver {

    private val byLink: MutableMap<String, List<DeviceIdentity>> = mutableMapOf()

    fun setRecipients(linkId: String, recipients: List<DeviceIdentity>) {
        byLink[linkId] = recipients.toList()
    }

    fun clear() = byLink.clear()

    override suspend fun resolveRecipients(linkId: String): List<DeviceIdentity> =
        byLink[linkId] ?: emptyList()
}
