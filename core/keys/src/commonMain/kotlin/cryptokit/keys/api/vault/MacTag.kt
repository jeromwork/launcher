package cryptokit.keys.api.vault

import kotlin.jvm.JvmInline

/** Message-authentication tag returned by [KeyVault.mac]. Purpose-tagged for cross-purpose safety. */
@JvmInline
value class MacTag(val bytes: ByteArray)
