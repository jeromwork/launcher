package cryptokit.keys.impl

import cryptokit.keys.api.CipherError
import cryptokit.keys.api.Outcome
import cryptokit.keys.api.RemoteStorage
import cryptokit.keys.api.StorageError
import cryptokit.keys.api.internal.ConfigCipher2
import cryptokit.keys.api.internal.DeviceIdentity
import cryptokit.keys.api.internal.EnvelopeStorage
import cryptokit.keys.api.internal.EnvelopeStorageError
import cryptokit.keys.api.internal.RecipientResolver
import cryptokit.keys.api.internal.ResolverError

/**
 * Facade implementation of [RemoteStorage] — wires [ConfigCipher2] +
 * [RecipientResolver] + [EnvelopeStorage] + [DeviceIdentity] behind the
 * caller-facing `put / get / list / delete` API.
 *
 * Internal-to-keys-module class. App-side code (DI module) constructs this with
 * concrete adapter implementations and exposes only the [RemoteStorage]
 * interface. Caller code never names this class.
 *
 * AAD binding: `"family-storage::v1::$namespace::$key"`. Includes namespace and
 * key to defend against context confusion (re-routing a sealed envelope from
 * one path to another inside the same backend).
 */
class EnvelopeRemoteStorage(
    private val cipher: ConfigCipher2,
    private val resolver: RecipientResolver,
    private val storage: EnvelopeStorage,
    private val deviceIdentity: DeviceIdentity
) : RemoteStorage {

    override suspend fun put(
        namespace: String,
        key: String,
        bytes: ByteArray
    ): Outcome<Unit, StorageError> {
        require(namespace.isNotEmpty()) { "namespace must not be empty" }
        require(key.isNotEmpty()) { "key must not be empty" }
        if (bytes.size > RemoteStorage.MAX_ENTRY_BYTES) {
            return Outcome.Failure(StorageError.TooLarge)
        }

        val recipients = when (val r = resolver.resolveFor(namespace, key)) {
            is Outcome.Success -> r.value
            is Outcome.Failure -> return Outcome.Failure(mapResolverError(r.error))
        }

        val aad = aadFor(namespace, key)
        val envelope = when (val r = cipher.seal(bytes, recipients, aad)) {
            is Outcome.Success -> r.value
            is Outcome.Failure -> return Outcome.Failure(mapCipherError(r.error))
        }

        return when (val r = storage.store(namespace, key, envelope)) {
            is Outcome.Success -> Outcome.Success(Unit)
            is Outcome.Failure -> Outcome.Failure(mapStorageError(r.error))
        }
    }

    override suspend fun get(
        namespace: String,
        key: String
    ): Outcome<ByteArray, StorageError> {
        require(namespace.isNotEmpty()) { "namespace must not be empty" }
        require(key.isNotEmpty()) { "key must not be empty" }

        val envelope = when (val r = storage.load(namespace, key)) {
            is Outcome.Success -> r.value
            is Outcome.Failure -> return Outcome.Failure(mapStorageError(r.error))
        }

        val myDeviceId = deviceIdentity.thisDeviceId()
        val myPrivKey = deviceIdentity.myPrivKey()
        val aad = aadFor(namespace, key)
        return try {
            when (val r = cipher.open(envelope, myPrivKey, myDeviceId, aad)) {
                is Outcome.Success -> Outcome.Success(r.value)
                is Outcome.Failure -> Outcome.Failure(mapCipherError(r.error))
            }
        } finally {
            myPrivKey.fill(0)
        }
    }

    override suspend fun list(
        namespace: String,
        keyPrefix: String
    ): Outcome<List<String>, StorageError> =
        when (val r = storage.list(namespace, keyPrefix)) {
            is Outcome.Success -> Outcome.Success(r.value)
            is Outcome.Failure -> Outcome.Failure(mapStorageError(r.error))
        }

    override suspend fun delete(
        namespace: String,
        key: String
    ): Outcome<Unit, StorageError> =
        when (val r = storage.delete(namespace, key)) {
            is Outcome.Success -> Outcome.Success(Unit)
            is Outcome.Failure -> Outcome.Failure(mapStorageError(r.error))
        }

    private fun aadFor(namespace: String, key: String): ByteArray =
        "$AAD_PREFIX::$namespace::$key".encodeToByteArray()

    private fun mapResolverError(e: ResolverError): StorageError = when (e) {
        is ResolverError.Network -> StorageError.Network(e.cause)
        ResolverError.OwnerHasNoDevices -> StorageError.Malformed("owner has no published devices")
        ResolverError.NoGrant -> StorageError.Unauthorized
        ResolverError.Unauthorized -> StorageError.Unauthorized
        is ResolverError.Malformed -> StorageError.Malformed(e.message)
    }

    private fun mapCipherError(e: CipherError): StorageError = when (e) {
        CipherError.AeadAuthFailed -> StorageError.IntegrityFailure
        CipherError.ConfigTooLarge -> StorageError.TooLarge
        CipherError.AlgorithmUnsupported -> StorageError.UnsupportedFormat
        CipherError.SchemaDowngradeDetected -> StorageError.IntegrityFailure
        CipherError.KeyUnavailable -> StorageError.NoIdentity
        CipherError.NotARecipient -> StorageError.NotARecipient
        is CipherError.InvalidInput -> StorageError.Malformed(
            e.cause.message ?: "invalid input"
        )
    }

    private fun mapStorageError(e: EnvelopeStorageError): StorageError = when (e) {
        is EnvelopeStorageError.Network -> StorageError.Network(e.cause)
        EnvelopeStorageError.Unauthorized -> StorageError.Unauthorized
        EnvelopeStorageError.NotFound -> StorageError.NotFound
        is EnvelopeStorageError.Malformed -> StorageError.Malformed(e.message)
    }

    companion object {
        const val AAD_PREFIX: String = "family-storage::v1"
    }
}
