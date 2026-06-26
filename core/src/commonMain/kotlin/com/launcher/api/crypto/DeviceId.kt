package com.launcher.api.crypto

import kotlin.jvm.JvmInline
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@JvmInline
@Serializable
@SerialName("DeviceId")
value class DeviceId(val value: String) {
    init {
        require(isUuid(value)) { "DeviceId must be UUID format: $value" }
    }

    companion object {
        @OptIn(ExperimentalUuidApi::class)
        fun random(): DeviceId = DeviceId(Uuid.random().toString())

        internal fun isUuid(s: String): Boolean {
            if (s.length != 36) return false
            for (i in s.indices) {
                val c = s[i]
                val expectDash = i == 8 || i == 13 || i == 18 || i == 23
                if (expectDash) {
                    if (c != '-') return false
                } else {
                    if (!c.isHexChar()) return false
                }
            }
            return true
        }

        private fun Char.isHexChar(): Boolean =
            this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
    }
}
