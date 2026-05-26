package com.launcher.api.media

import kotlinx.serialization.Serializable

/**
 * Spec 012 — media payload type carried в `envelope.metadata.kind`.
 *
 * Wire value — lowercase string ("image", "document"). Reader 011 envelope нейтрален
 * к metadata content — клиент (spec 012+) определяет registry значений.
 * См. specs/012-contact-photos-and-private-documents/contracts/metadata-kind-registry.md.
 *
 * Extension policy: future kinds (Audio, Video, File) добавляются additive — добавляем
 * value, добавляем wire string, обновляем registry. Old readers получают unknown kind →
 * graceful opaque handling (envelope-level).
 *
 * Task: T1209 (Phase 2). FR-006.
 */
@Serializable
enum class PrivateMediaKind(val wireValue: String) {
    Image("image"),
    Document("document"),
    ;

    companion object {
        /**
         * Permissive lookup. Returns null for unknown values — caller decides behaviour
         * (default to Image for legacy 011 smoke blobs, or fail-closed).
         */
        fun fromWireOrNull(value: String?): PrivateMediaKind? =
            entries.firstOrNull { it.wireValue == value }
    }
}
