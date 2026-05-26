package com.launcher.adapters.media

import com.launcher.api.media.PrivateMediaResolution

/**
 * Spec 012 — narrow port для эмитирования `PartialReason.MediaDecryptFailed`
 * в `/state/current.partialApplyReasons` (FR-021, SC-010).
 *
 * Closes existing 011 obligation: enum value было зарезервировано в спеке 008
 * (`PartialReason.MediaDecryptFailed`), но never эмитировалось. Spec 012 — первая
 * реальная эмиссия из real code (см. `state-applied.md:65` в спеке 008).
 *
 * Host wiring (Phase 5/6 / ViewModel integration):
 *  - При `PrivateMediaResolver.resolve()` returns `Failed` → host calls
 *    `report(reason)` с categorical subcategory.
 *  - Adapter обновляет `/state/current.partialApplyReasons` (через
 *    `StateApplied` writer from спека 008).
 *  - При следующем successful apply config'а — reasons clear'ятся (per
 *    research.md R9 — depending on spec 008 implementation).
 *
 * Implementation pending — Phase 5/6 integration. Сейчас — interface +
 * categorical taxonomy.
 *
 * Task: T1250 (Phase 7). FR-021, SC-010, closes 008 obligation.
 */
fun interface MediaDecryptFailedReporter {
    /**
     * @param subcategory specifies which CryptoError subcategory triggered the
     *                    failure (BlobMissing / MacFailed / etc.). Used by admin
     *                    indicator hint dispatch (FR-022).
     */
    suspend fun report(subcategory: PrivateMediaResolution.FailureReason)
}

/**
 * No-op reporter — used in tests or в places где admin indicator UI отсутствует
 * (например, fake mockBackend variant). Production wiring overrides this с real
 * `StateApplied` writer.
 */
class NoOpMediaDecryptFailedReporter : MediaDecryptFailedReporter {
    override suspend fun report(subcategory: PrivateMediaResolution.FailureReason) {
        // No-op. Reasons just aren't surfaced в /state — host UI shows только
        // local placeholder без cross-device indicator.
    }
}
