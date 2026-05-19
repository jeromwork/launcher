package com.launcher.ui.paired

import com.launcher.api.link.Link
import com.launcher.api.link.LinkRegistry
import com.launcher.api.paired.LocalLinkRevocationStore
import com.launcher.util.DateFormatter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Spec 010 T084 — orchestrates the local-first revocation flow
 * (FR-032 + FR-032a). UI calls [confirmUnlink] when the user taps ДА в
 * [UnlinkConfirmationDialog]; the presenter:
 *
 *  1. Marks the link as locally revoked в [LocalLinkRevocationStore] —
 *     persistent, survives kill/restart.
 *  2. Asks [LinkRegistry] to drop its in-memory state (best-effort local
 *     reaction: state-publisher stops emitting, ConfigEditor stops
 *     listening; the actual port no-op if revoke() takes server-side
 *     action — the worker is what handles server cleanup).
 *  3. Fires [enqueueCleanup] (host-provided) to schedule the WorkManager
 *     `UnlinkCleanupWorker` for the queued server-side `deactivate(linkId)`.
 *     The enqueue callback is the platform seam: androidMain wires it to
 *     `UnlinkCleanupWorker.enqueue(context, linkId)`; commonTest passes a
 *     fake that records invocations.
 *
 * The `items` Flow combines:
 *  - [LinkRegistry.currentLink] — the single «Кто помогает мне» entry
 *    (multi-admin is future-spec OUT-013).
 *  - [LocalLinkRevocationStore.revokedLinkIds] — filters out locally-revoked
 *    links so they disappear immediately (FR-032 (c)).
 *
 * The «Кому я помогаю» list stays empty for спек 010 — реальный Admin-side
 * registry lands в спека 008 multi-side sync.
 */
class PairedDevicesPresenter(
    private val linkRegistry: LinkRegistry,
    private val revocationStore: LocalLinkRevocationStore,
    private val enqueueCleanup: (linkId: String) -> Unit,
    private val displayNameFor: (Link) -> String = { link ->
        // Спек 011 attaches admin display name to the trust edge; до тех пор
        // fall back to a short slice of the opaque uid so the row isn't blank.
        link.adminId.firebaseAuthUid.take(8).ifEmpty { link.linkId.take(8) }
    },
) {

    /** Hot UI items combining current Managed-side link + local revocation flag. */
    fun items(): Flow<PairedDevicesViewState> =
        combine(
            linkRegistry.currentLink(),
            revocationStore.revokedLinkIds(),
        ) { link, revokedIds ->
            val helpsMe = if (link != null && link.linkId !in revokedIds) {
                listOf(
                    PairedDeviceItem(
                        linkId = link.linkId,
                        displayName = displayNameFor(link),
                        pairedDateLabel = DateFormatter.formatShortDate(link.createdAt),
                        role = PairedDeviceItem.Section.HelpsMe,
                    ),
                )
            } else {
                emptyList()
            }
            PairedDevicesViewState(
                helpsMe = helpsMe,
                iHelp = emptyList(),
            )
        }

    /**
     * FR-032 + FR-032a (a)/(b) entry point. Marks the link locally revoked
     * AND queues server-side cleanup. Idempotent — repeated calls for the
     * same linkId are safe (DataStore set-insert is no-op).
     */
    suspend fun confirmUnlink(linkId: String) {
        revocationStore.markRevoked(linkId)
        enqueueCleanup(linkId)
    }
}

/** Aggregated state surfaced to the Composable layer. */
data class PairedDevicesViewState(
    val helpsMe: List<PairedDeviceItem>,
    val iHelp: List<PairedDeviceItem>,
)

