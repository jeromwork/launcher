package com.launcher.preset.port

import com.launcher.preset.model.HintFlowEntry

/**
 * T019 — HintPoolSource port (FR-007, CL-7).
 *
 * Domain-level source of localized hint metadata joining `Preset.hintFlow` entries with
 * their rendered text. UI layer (WizardScreen) resolves hints at render time; the
 * ReconcileEngine never processes them (D5).
 *
 * // TODO(shareability): future HintPoolSource adapters — file import, share intent, marketplace
 *
 * See `docs/product/vision.md` § shareability and CLAUDE.md rule 9. The bundled adapter
 * lives in androidMain (`BundledHintPoolSource`); additional sources (`FileImportHintPoolSource`,
 * `ShareIntentHintPoolSource`, `MarketplaceHintPoolSource`) plug in additively without
 * changing the wire format.
 */
interface HintPoolSource {
    /**
     * @return every hint declared by the current preset bundle. Missing / malformed
     *   sources MUST return an empty list — never throw across the domain boundary.
     */
    suspend fun load(): List<HintFlowEntry>
}
