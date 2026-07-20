package com.launcher.api.action

import family.wire.WireVersion
import family.wire.WireVersionHeader
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.jvm.JvmInline

/**
 * Provider-agnostic action dispatched when a user activates a tile.
 *
 * Wire-format root per [`contracts/action-wire-format.md`](specs/005-action-architecture-v2/contracts/action-wire-format.md)
 * v1.0.0. The shape is **public**: it is persisted in mock JSON assets, will be
 * synchronised via backend (spec 007), and may be embedded in QR-share payloads
 * (spec 010). Any change here is a wire-format change.
 *
 * Versioned per [`docs/architecture/wire-format.md`](docs/architecture/wire-format.md) — the three
 * fields are gated at dispatch via [accessFor], not by comparing [schemaVersion] (§3: schemaVersion
 * is diagnostics only). A document written by a newer build is dispatched normally unless it says
 * it needs a newer reader.
 *
 * Invariants validated at parse / dispatch time:
 *  - Version header gate (§3), applied by `AndroidActionDispatcher`.
 *  - Fallback chain depth <= [MAX_FALLBACK_DEPTH] (deeper -> Failure at dispatch).
 *  - [providerId] regex check at construction via [ProviderId.fromWire].
 */
// @EncodeDefault keeps the three version fields on the wire even at their defaults: invariant I1
// says every document carries them, and this format encodes with `encodeDefaults = false`.
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class Action(
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val schemaVersion: WireVersion = SCHEMA_VERSION,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val minReaderVersion: WireVersion = MIN_READER_VERSION,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val minWriterVersion: WireVersion = MIN_WRITER_VERSION,
    val providerId: ProviderId,
    val payload: ActionPayload,
    val fallback: Action? = null,
    val sourceModuleId: String? = null,
) : WireVersionHeader {
    companion object {
        /** What this build writes. Was the integer `1` before the conversion — never lowered (I3). */
        val SCHEMA_VERSION: WireVersion = WireVersion(1, 0)

        /**
         * Every field this format has today is understandable by the original v1 reader, so nothing
         * requires a newer one. Raise only when an existing field changes meaning (§3).
         */
        val MIN_READER_VERSION: WireVersion = WireVersion(1, 0)

        /**
         * Unknown fields survive a read-modify-write, so an older writer cannot destroy meaning
         * here. Raise when that stops being true.
         */
        val MIN_WRITER_VERSION: WireVersion = WireVersion(1, 0)

        /** Maximum recursive depth allowed in fallback chain. action + fallback + fallback-of-fallback. */
        const val MAX_FALLBACK_DEPTH: Int = 2
    }
}

/**
 * Logical provider identifier. Backed by a simple [String] (not enum) on purpose:
 * lets configs from a newer build reach older clients without crashing — unknown
 * provider id surfaces as [DispatchResult.ProviderUnavailable] with hint
 * [UnavailabilityHint.UnknownInThisVersion]. See research R1.
 */
@Serializable(with = ProviderIdSerializer::class)
@JvmInline
value class ProviderId(val value: String) {
    companion object {
        val APP             = ProviderId("app")
        val WHATSAPP        = ProviderId("whatsapp")
        val TELEGRAM        = ProviderId("telegram")
        val PHONE           = ProviderId("phone")
        val SMS             = ProviderId("sms")
        val BROWSER         = ProviderId("browser")
        val YOUTUBE         = ProviderId("youtube")
        val SYSTEM_SETTINGS = ProviderId("system_settings")

        // Wire-format regex: lowercase, alphanumeric, `-` / `_`, length 2..32.
        // Must align with the regex documented in contracts/action-wire-format.md.
        private val WIRE_REGEX = Regex("[a-z][a-z0-9_-]{1,31}")

        /**
         * Parse a wire-format string into a [ProviderId]. Rejects empty / mis-cased
         * / wrong-length values at parse time, but accepts unknown ids — those
         * surface at dispatch as [UnavailabilityHint.UnknownInThisVersion].
         */
        fun fromWire(s: String): ProviderId {
            require(s.matches(WIRE_REGEX)) { "invalid providerId: '$s'" }
            return ProviderId(s)
        }
    }
}

private object ProviderIdSerializer : KSerializer<ProviderId> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.launcher.api.action.ProviderId", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ProviderId) =
        encoder.encodeString(value.value)

    override fun deserialize(decoder: Decoder): ProviderId =
        ProviderId.fromWire(decoder.decodeString())
}

/**
 * Tagged-union payload describing what the provider should do. Discriminator
 * field on the wire is `kind` (set via [SerialName]). Adding new variants is a
 * minor wire-format bump; renaming or removing is a breaking major bump per
 * [`contracts/action-wire-format.md`](specs/005-action-architecture-v2/contracts/action-wire-format.md).
 *
 * [Custom] is the deliberate escape-hatch (research R2) for provider payloads
 * not yet promoted to typed variants. Bounds enforced by `CustomPayloadValidator`
 * at dispatch time (spec 005, security CHK-011).
 */
@Serializable
sealed class ActionPayload {

    @Serializable
    @SerialName("open_app")
    data class OpenApp(
        val packageHint: String,
        val storeUrlHint: String? = null,
    ) : ActionPayload()

    @Serializable
    @SerialName("whatsapp_message")
    data class WhatsAppMessage(
        val contactRef: String,
    ) : ActionPayload()

    @Serializable
    @SerialName("whatsapp_call")
    data class WhatsAppCall(
        val contactRef: String,
        @SerialName("callKind")
        val kind: WhatsAppCallKind,
    ) : ActionPayload()

    @Serializable
    @SerialName("phone")
    data class Phone(
        val number: String,
    ) : ActionPayload()

    @Serializable
    @SerialName("sms")
    data class Sms(
        val number: String,
        val body: String? = null,
    ) : ActionPayload()

    @Serializable
    @SerialName("url")
    data class Url(
        val url: String,
    ) : ActionPayload()

    @Serializable
    @SerialName("youtube")
    data class YouTube(
        val target: YouTubeTarget,
    ) : ActionPayload()

    @Serializable
    @SerialName("open_settings")
    data class OpenSettings(
        val target: SettingsTarget = SettingsTarget.General,
    ) : ActionPayload()

    @Serializable
    @SerialName("custom")
    data class Custom(
        val key: String,
        val params: Map<String, String> = emptyMap(),
    ) : ActionPayload()
}

@Serializable
enum class WhatsAppCallKind { VOICE, VIDEO }

@Serializable
sealed class YouTubeTarget {

    @Serializable
    @SerialName("home")
    data object Home : YouTubeTarget()

    @Serializable
    @SerialName("video")
    data class Video(val videoId: String) : YouTubeTarget()

    @Serializable
    @SerialName("channel")
    data class Channel(val channelHandle: String) : YouTubeTarget()
}

@Serializable
enum class SettingsTarget {
    /** Top-level system settings entry point. Future variants added by minor bumps. */
    General,
}

/**
 * Outcome of a single [Action] dispatch. Provider-agnostic — no WhatsApp- or
 * Telegram-specific variants. UI surfaces [Failure] / [ProviderUnavailable] as
 * a single user-visible "couldn't run that" snackbar (US-508).
 */
sealed class DispatchResult {

    data object Ok : DispatchResult()

    data class BlockedByPolicy(val reason: BlockReason) : DispatchResult()

    data class ProviderUnavailable(
        val providerId: ProviderId,
        val hint: UnavailabilityHint,
    ) : DispatchResult()

    data class Failure(val reason: String) : DispatchResult()
}

enum class BlockReason {
    INVALID_REQUEST,
    PERMISSION_OR_POLICY,
}

/** Reason why a provider could not be used right now. Drives wizard UX (US-507). */
enum class UnavailabilityHint {
    /** App / target package not installed; fallback or install link can resolve. */
    Missing,

    /** Device hardware or OS configuration cannot perform the action (no SIM, no telephony, no browser). */
    NotApplicable,

    /**
     * Provider id is newer than this build knows. Wizards hide such providers;
     * existing flows referencing them show "unavailable in your version".
     * See research R1 and Clarification C1.
     */
    UnknownInThisVersion,
}
