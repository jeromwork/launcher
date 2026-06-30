package com.launcher.api.preset

import kotlinx.serialization.Serializable

/**
 * Composite identity for a preset: stable uid + monotonic version.
 *
 * Composite-key encoding `"<uid>::<version>"` is used as a JSON Map key in
 * [com.launcher.api.profile.ProfileStoreState.profiles] (decision R3).
 * Reserved separator: `::` is forbidden inside [uid].
 */
@Serializable
data class PresetRef(
    val uid: String,
    val version: Int,
) {
    init {
        require(uid.isNotBlank()) { "PresetRef.uid must not be blank" }
        require(!uid.contains(SEPARATOR)) {
            "PresetRef.uid must not contain reserved separator '$SEPARATOR'"
        }
        require(version >= 1) { "PresetRef.version must be >= 1, was $version" }
    }

    fun toCompositeKey(): String = "$uid$SEPARATOR$version"

    companion object {
        const val SEPARATOR: String = "::"

        fun parseCompositeKey(key: String): PresetRef {
            val idx = key.lastIndexOf(SEPARATOR)
            require(idx > 0 && idx < key.length - SEPARATOR.length) {
                "Invalid PresetRef composite key '$key' — expected '<uid>::<version>'"
            }
            val uid = key.substring(0, idx)
            val versionStr = key.substring(idx + SEPARATOR.length)
            val version = versionStr.toIntOrNull()
                ?: error("Invalid PresetRef version '$versionStr' in '$key'")
            return PresetRef(uid, version)
        }
    }
}
