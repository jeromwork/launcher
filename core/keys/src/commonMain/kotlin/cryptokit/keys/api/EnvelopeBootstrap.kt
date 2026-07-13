package cryptokit.keys.api

/**
 * Post-sign-in publication of this device's identity into the
 * [cryptokit.keys.api.internal.PublicKeyDirectory].
 *
 * Idempotent — safe to call on every app launch. The implementation publishes
 * the local [cryptokit.keys.api.internal.DeviceIdentity]'s public key under the
 * signed-in user's namespace; the directory entry survives until the device
 * is explicitly unpublished or the app data is wiped.
 *
 * Caller (LauncherApplication / sign-in observer) invokes [bootstrap] once
 * authentication has produced a valid [AuthIdentity].
 */
interface EnvelopeBootstrap {

    /** Publishes this device's pub key under the signed-in user's namespace. */
    suspend fun bootstrap(): Outcome<Unit, BootstrapError>

    /** Removes this device from the directory; called on explicit sign-out. */
    suspend fun teardown(): Outcome<Unit, BootstrapError>
}

sealed class BootstrapError {
    data object NoIdentity : BootstrapError()
    data class Backend(val message: String) : BootstrapError()
}
