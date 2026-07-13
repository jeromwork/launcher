package cryptokit.keys.fakes

import cryptokit.keys.api.Outcome
import cryptokit.keys.api.RecipientPubKey
import cryptokit.keys.api.internal.RecipientResolver
import cryptokit.keys.api.internal.ResolverError

/**
 * In-memory [RecipientResolver] for tests.
 *
 * Caller seeds the resolver with a static map of `(namespace, keyPrefix?) →
 * recipients`. Production resolver assembles the list from PublicKeyDirectory +
 * access-grants, but for unit tests we feed it pre-computed lists.
 */
internal class FakeRecipientResolver(
    private val recipientsByNamespace: MutableMap<String, List<RecipientPubKey>> = mutableMapOf()
) : RecipientResolver {

    fun seed(namespace: String, recipients: List<RecipientPubKey>) {
        recipientsByNamespace[namespace] = recipients
    }

    override suspend fun resolveFor(
        ownerNamespace: String,
        key: String
    ): Outcome<List<RecipientPubKey>, ResolverError> {
        val list = recipientsByNamespace[ownerNamespace]
            ?: return Outcome.Failure(ResolverError.OwnerHasNoDevices)
        if (list.isEmpty()) return Outcome.Failure(ResolverError.OwnerHasNoDevices)
        return Outcome.Success(list)
    }
}
