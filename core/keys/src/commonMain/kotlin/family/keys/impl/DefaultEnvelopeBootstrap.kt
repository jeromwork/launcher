package family.keys.impl

import family.keys.api.BootstrapError
import family.keys.api.EnvelopeBootstrap
import family.keys.api.IdentityProof
import family.keys.api.Outcome
import family.keys.api.internal.DeviceIdentity
import family.keys.api.internal.DirectoryError
import family.keys.api.internal.PublicKeyDirectory

/**
 * Default [EnvelopeBootstrap] — publishes / unpublishes this device's
 * [DeviceIdentity] in the [PublicKeyDirectory] under the signed-in user's
 * namespace.
 *
 * Idempotent: calling [bootstrap] multiple times overwrites the same directory
 * entry without side-effects (Firestore `set` is overwrite).
 */
class DefaultEnvelopeBootstrap(
    private val identity: IdentityProof,
    private val deviceIdentity: DeviceIdentity,
    private val directory: PublicKeyDirectory
) : EnvelopeBootstrap {

    override suspend fun bootstrap(): Outcome<Unit, BootstrapError> {
        val uid = identity.currentIdentity()?.stableId?.takeIf { it.isNotEmpty() }
            ?: return Outcome.Failure(BootstrapError.NoIdentity)
        val deviceId = deviceIdentity.thisDeviceId()
        val pubKey = deviceIdentity.myPubKey()
        return when (val r = directory.publishMyDevice(uid, deviceId, pubKey)) {
            is Outcome.Success -> Outcome.Success(Unit)
            is Outcome.Failure -> Outcome.Failure(mapDirectory(r.error))
        }
    }

    override suspend fun teardown(): Outcome<Unit, BootstrapError> {
        val uid = identity.currentIdentity()?.stableId?.takeIf { it.isNotEmpty() }
            ?: return Outcome.Failure(BootstrapError.NoIdentity)
        val deviceId = deviceIdentity.thisDeviceId()
        return when (val r = directory.unpublishMyDevice(uid, deviceId)) {
            is Outcome.Success -> Outcome.Success(Unit)
            is Outcome.Failure -> Outcome.Failure(mapDirectory(r.error))
        }
    }

    private fun mapDirectory(e: DirectoryError): BootstrapError = when (e) {
        is DirectoryError.Network -> BootstrapError.Backend("network: ${e.cause?.message ?: "unknown"}")
        DirectoryError.Unauthorized -> BootstrapError.Backend("unauthorized")
        DirectoryError.NotFound -> BootstrapError.Backend("not-found")
        is DirectoryError.Malformed -> BootstrapError.Backend("malformed: ${e.message}")
    }
}
