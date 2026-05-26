package com.launcher.adapters.media

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Spec 012 — wire format **внутри** ciphertext envelope'а для документов
 * (privacy invariant FR-006). Содержит расшифрованные image bytes + sensitive label.
 *
 * Label **не должен** появляться в `envelope.metadata` — она plaintext (sent в clear
 * вместе с blob'ом для server-side AAD). Метаdata = только non-sensitive categorical
 * hints (kind=image/document).
 *
 * Encoding: JSON (image bytes base64-encoded) для simplicity + читаемости в debug log'ах
 * fake adapter'ов. Alternative — CBOR / Proto — overhead не оправдан для одного payload type
 * со sensitive content. JSON is consistent с rest of project's wire format strategy
 * (per ADR notes about kotlinx-serialization defaults).
 *
 * For [PrivateMediaKind.Image] payload (фото контакта без label) — caller передаёт raw bytes
 * напрямую AeadCipher без обёртки. Только Document use case needs DocumentPayload.
 *
 * Task: T1217 (Phase 3). FR-006.
 */
@Serializable
internal data class DocumentPayload(
    /** Base64-encoded raw image bytes (JPEG, PNG, etc.). */
    val imageBytesBase64: String,
    /** Sensitive label visible only to recipient after decrypt. */
    val label: String,
)

@OptIn(ExperimentalEncodingApi::class)
internal object DocumentPayloadCodec {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    fun encode(imageBytes: ByteArray, label: String): ByteArray {
        val payload = DocumentPayload(
            imageBytesBase64 = Base64.encode(imageBytes),
            label = label,
        )
        return json.encodeToString(payload).encodeToByteArray()
    }

    fun decode(plaintext: ByteArray): DocumentPayload? = try {
        json.decodeFromString<DocumentPayload>(plaintext.decodeToString())
    } catch (e: Throwable) {
        null
    }

    fun decodeImageBytes(plaintext: ByteArray): ByteArray? = decode(plaintext)?.let { payload ->
        try {
            Base64.decode(payload.imageBytesBase64)
        } catch (e: Throwable) {
            null
        }
    }
}
