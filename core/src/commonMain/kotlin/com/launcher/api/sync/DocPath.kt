package com.launcher.api.sync

import com.launcher.api.pairing.PairingToken

/**
 * Domain-typed path to a Firestore document. Replaces raw `"links/abc/state"`
 * strings so the domain layer NEVER composes paths by hand and adapters have
 * one parse-point per subtree (per spec 007 plan §Module map).
 *
 * Used by [RemoteSyncBackend] for read/write/observe; by `LinkRegistry.revoke()`
 * to iterate the known subcollection paths during recursive deletion (research
 * §Recursive). When a new subcollection is added (spec 011 private-media etc.)
 * — extend [DocPath] here and update the revoke iteration list.
 */
sealed interface DocPath {
    val rawPath: String

    data class Pairings(val token: PairingToken) : DocPath {
        override val rawPath: String get() = "pairings/${token.raw}"
    }

    data class Links(val linkId: String) : DocPath {
        override val rawPath: String get() = "links/$linkId"

        fun child(sub: String): SubPath = SubPath(this, sub)
    }

    data class LinkState(val linkId: String) : DocPath {
        override val rawPath: String get() = "links/$linkId/state/current"
    }

    data class LinkConfig(val linkId: String) : DocPath {
        override val rawPath: String get() = "links/$linkId/config/current"
    }

    data class LinkCapabilities(val linkId: String) : DocPath {
        override val rawPath: String get() = "links/$linkId/capabilities/current"
    }

    data class LinkHealth(val linkId: String) : DocPath {
        override val rawPath: String get() = "links/$linkId/health/current"
    }

    data class LinkCommand(val linkId: String, val cmdId: String) : DocPath {
        override val rawPath: String get() = "links/$linkId/commands/$cmdId"
    }

    data class Devices(val managedDeviceId: String) : DocPath {
        override val rawPath: String get() = "devices/$managedDeviceId"
    }

    /** Escape hatch for ad-hoc subcollections under `/links/{linkId}/`; prefer adding a typed
     *  subtype above when a new subcollection becomes stable. */
    data class SubPath(val parent: Links, val sub: String) : DocPath {
        override val rawPath: String get() = "${parent.rawPath}/$sub"
    }
}
