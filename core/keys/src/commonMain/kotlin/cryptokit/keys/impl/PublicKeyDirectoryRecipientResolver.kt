package cryptokit.keys.impl

import cryptokit.keys.api.Outcome
import cryptokit.keys.api.RecipientPubKey
import cryptokit.keys.api.internal.DirectoryError
import cryptokit.keys.api.internal.PublicKeyDirectory
import cryptokit.keys.api.internal.RecipientResolver
import cryptokit.keys.api.internal.ResolverError

/**
 * Composite [RecipientResolver] backed by [PublicKeyDirectory].
 *
 * Resolution rule (F-5b — owner perspective):
 *   recipients(ownerUid) =
 *       devices(ownerUid)
 *       ∪ ⋃ devices(helperUid) for each helperUid in grantHolders(ownerUid)
 *
 * Self-edit and cross-user delegation are the same code path; the difference
 * is only whether the caller's UID == ownerUid or appears in grantHolders.
 *
 * For F-5b MVP, `key` is not used to filter recipients (one config-level
 * recipient set per namespace). If finer-grained access is needed later
 * (e.g., per-key ACL), the resolver gains key inspection without changing
 * the port signature.
 *
 * **Per-helper isolation note**: when a helper is added via grant, we fetch
 * **all** of that helper's currently-published devices. Adding a new device
 * later does not auto-include it in past encryptions; recipients are baked
 * into each envelope at write time (revocation-by-future-writes semantics, the
 * accepted residual model — see ecosystem-vision.md group-encryption section).
 *
 * Расположен в `core/keys/.../impl/` (а не в `app/src/main/data/envelope/`)
 * по тем же основаниям, что и [EnvelopeRemoteStorage] / [DefaultEnvelopeBootstrap]:
 * чистая логика поверх internal port'ов, без зависимости от backend-flavour'а.
 * Это также удовлетворяет fitness-rule `appCodeOutsideAdaptersMustNotImportKeysApiInternal`
 * (см. `core/keys/src/jvmTest/kotlin/cryptokit/keys/fitness/ImportRestrictionsFitnessTest.kt`):
 * `app/src/main` не должен импортировать `cryptokit.keys.api.internal.*`, а класс
 * по своей природе оперирует именно этими portами — корректное место — `:core:keys`.
 */
class PublicKeyDirectoryRecipientResolver(
    private val directory: PublicKeyDirectory
) : RecipientResolver {

    override suspend fun resolveFor(
        ownerNamespace: String,
        key: String
    ): Outcome<List<RecipientPubKey>, ResolverError> {
        val ownDevices = when (val r = directory.fetchDevicesFor(ownerNamespace)) {
            is Outcome.Success -> r.value
            is Outcome.Failure -> return Outcome.Failure(mapDirectory(r.error))
        }

        val grantHolders = when (val r = directory.fetchGrantHolders(ownerNamespace)) {
            is Outcome.Success -> r.value
            is Outcome.Failure -> return Outcome.Failure(mapDirectory(r.error))
        }

        val helperDevices = mutableListOf<RecipientPubKey>()
        for (helperUid in grantHolders) {
            when (val r = directory.fetchDevicesFor(helperUid)) {
                is Outcome.Success -> helperDevices.addAll(r.value)
                is Outcome.Failure -> {
                    // A single helper read failure should not block the whole write.
                    // Skip this helper but continue with others — the helper just
                    // won't be a recipient for this envelope. Logged in adapter.
                    // (Network errors typically would have failed at the first
                    // ownDevices fetch already.)
                }
            }
        }

        // Deduplicate by deviceId — a UID's own devices and grants shouldn't
        // produce the same deviceId, but be defensive against malformed state.
        val all = (ownDevices + helperDevices)
            .distinctBy { it.deviceId }

        if (all.isEmpty()) {
            return Outcome.Failure(ResolverError.OwnerHasNoDevices)
        }
        return Outcome.Success(all)
    }

    private fun mapDirectory(e: DirectoryError): ResolverError = when (e) {
        is DirectoryError.Network -> ResolverError.Network(e.cause)
        DirectoryError.Unauthorized -> ResolverError.Unauthorized
        DirectoryError.NotFound -> ResolverError.OwnerHasNoDevices
        is DirectoryError.Malformed -> ResolverError.Malformed(e.message)
    }
}
