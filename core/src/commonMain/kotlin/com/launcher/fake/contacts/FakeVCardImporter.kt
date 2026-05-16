package com.launcher.fake.contacts

import com.launcher.api.contacts.ImportError
import com.launcher.api.contacts.RawVCard
import com.launcher.api.contacts.VCardImporter
import com.launcher.api.result.Outcome

/**
 * Fake for [VCardImporter] (spec 009 Phase 4). Returns the pre-parsed
 * [scripted] for any payload, ignoring bytes — lets editor-flow tests
 * exercise the post-parse pipeline without committing real vCard bytes.
 *
 * Real-bytes contract test lives in androidUnitTest against
 * `VCardImportAdapter` (Phase 5).
 */
class FakeVCardImporter(
    private val scripted: Outcome<RawVCard, ImportError>,
) : VCardImporter {

    override suspend fun parse(payload: ByteArray): Outcome<RawVCard, ImportError> = scripted
}
