package com.launcher.api.edit

import com.launcher.wire.WireVersion

/**
 * Domain error variants для [NamedConfigsLocalStore] operations (FR-003c,
 * FR-003a, contracts/named-config-local.md §Invariants).
 *
 * Sealed для exhaustive `when` в presentation layer. Variants map directly
 * to user-visible behaviors per failure-recovery.md CHK001.
 */
sealed class StoreError {

    /** Attempted to create a 6th config — FR-003c hard limit reached. */
    data object LimitReached : StoreError()

    /**
     * Submitted [configName] failed validation. [reason] is one of:
     *  - `"EmptyName"` — blank after NFC normalize / trim.
     *  - `"TooLong"` — exceeds [NamedConfig.MAX_CONFIG_NAME_LENGTH].
     *  - `"InvalidChars"` — contains characters outside `\p{L}\p{N}` + space + hyphen.
     */
    data class InvalidName(val reason: String) : StoreError()

    /** Case-insensitive [name] match already exists в store. */
    data class NameAlreadyExists(val name: String) : StoreError()

    /** Lookup of config by name returned no match. */
    data object NotFound : StoreError()

    /**
     * Cannot remove the last config с `isDefault = true` — FR-003a invariant
     * (exactly one default must exist at any time).
     */
    data object DefaultMustExist : StoreError()

    /**
     * Read attempted on persisted JSON with `schemaVersion > CURRENT_SCHEMA_VERSION`.
     * Fail-closed per contracts/named-config-local.md §Forward compatibility.
     * Presentation layer shows "Обновите приложение для использования этого конфига".
     */
    data class UnsupportedSchemaVersion(val required: WireVersion?, val readerLevel: WireVersion) : StoreError()
}
