package cryptokit.keys.api

/**
 * Caller-facing API for saving and loading config blobs to/from the encrypted
 * remote storage. This is a thin convenience wrapper over [RemoteStorage] —
 * carries the same semantics, just with named methods that mirror the typical
 * config-management use cases.
 *
 * **Why a wrapper instead of direct [RemoteStorage]**: caller code reads as
 * `configSaver.saveOwn("default", bytes)` which carries product-domain
 * intent ("save my own config"), while [RemoteStorage] is the lower-level
 * primitive that also serves photos / messages / future ecosystem data.
 *
 * **Configuration namespacing convention**:
 *  - Logical key = `config/{configName}`.
 *  - configName is the user-chosen short identifier (`default`, `kitchen-tv`,
 *    `grannys-phone`, …).
 *  - `saveOwn / loadOwn` target the caller's own UID namespace.
 *  - `saveForOther / loadForOther` target another UID's namespace; requires
 *    that the owner has granted access to the caller (Firestore Security
 *    Rules + RecipientResolver enforce this).
 */
interface ConfigSaver {

    /** Save the caller's own config under [configName]. */
    suspend fun saveOwn(configName: String, bytes: ByteArray): Outcome<Unit, StorageError>

    /** Load the caller's own config by [configName]. */
    suspend fun loadOwn(configName: String): Outcome<ByteArray, StorageError>

    /** List the caller's own config names. */
    suspend fun listOwn(): Outcome<List<String>, StorageError>

    /** Delete one of the caller's own configs. */
    suspend fun deleteOwn(configName: String): Outcome<Unit, StorageError>

    /**
     * Save a config in another UID's namespace; requires an active access-grant
     * from that UID. The envelope's recipient list is resolved from the
     * **owner**'s [PublicKeyDirectory], so all of the owner's own devices and
     * all current helpers (including the caller) can decrypt.
     */
    suspend fun saveForOther(
        ownerUid: String,
        configName: String,
        bytes: ByteArray
    ): Outcome<Unit, StorageError>

    /** Load a config from another UID's namespace; requires active access-grant. */
    suspend fun loadForOther(
        ownerUid: String,
        configName: String
    ): Outcome<ByteArray, StorageError>

    /** List config names in another UID's namespace; requires active access-grant. */
    suspend fun listForOther(ownerUid: String): Outcome<List<String>, StorageError>

    companion object {
        const val KEY_PREFIX_CONFIG: String = "config/"

        fun keyOf(configName: String): String {
            require(configName.isNotEmpty()) { "configName must not be empty" }
            require(!configName.contains("/")) {
                "configName must not contain '/' (path separator reserved for layout)"
            }
            return KEY_PREFIX_CONFIG + configName
        }

        /** Extract configName from a stored key, or null if the key isn't a config. */
        fun configNameOf(key: String): String? =
            key.takeIf { it.startsWith(KEY_PREFIX_CONFIG) }?.removePrefix(KEY_PREFIX_CONFIG)
    }
}
