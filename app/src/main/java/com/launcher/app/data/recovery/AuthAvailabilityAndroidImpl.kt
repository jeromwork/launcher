package com.launcher.app.data.recovery

import com.launcher.api.setup.GmsAvailabilityPort
import com.launcher.api.setup.GmsStatus
import family.keys.api.AuthAvailability
import family.keys.api.AuthAvailabilityStatus
import family.keys.api.AvailabilityReason

/**
 * Android adapter wiring F-4's [GmsAvailabilityPort] (spec 010) onto the F-5
 * domain [AuthAvailability] port (T638, FR-013).
 *
 * **Bridge rationale** (CLAUDE.md rule 2 ACL):
 *  - F-4 owns runtime GMS state and exposes [GmsStatus]. That sealed class
 *    speaks vendor vocabulary (GMS, GoogleApiAvailability, recoverable error
 *    codes).
 *  - F-5 domain only knows [AvailabilityReason] (NoSupportedProvider /
 *    KeystoreLocked / NetworkUnreachable). It MUST NOT import GMS types per
 *    `ImportRestrictionsFitnessTest` and CLAUDE.md rule 1.
 *  - This adapter maps one onto the other. If a second provider arrives
 *    (Email/Password, Phone, own-server JWT), this adapter widens its
 *    decision logic; the domain port stays unchanged.
 *
 * **Mapping policy**:
 *  - [GmsStatus.Available]            → [AuthAvailabilityStatus.Available]
 *  - [GmsStatus.MissingRecoverable]   → [AuthAvailabilityStatus.Available]
 *    (Credential Manager / GMS resolution dialog can recover; from F-5's
 *    perspective the provider is reachable. F-4 surfaces the recovery
 *    dialog; F-5 should treat the provider as available unless sign-in
 *    actually fails.)
 *  - [GmsStatus.MissingFatal]         → Unavailable(NoSupportedProvider)
 *    (Huawei without GMS, etc. — no recoverable path on this device.)
 *
 * **Why not also map KeystoreLocked / NetworkUnreachable here**: those are
 * properties of the Keystore / network at sign-in time, not of provider
 * availability. They surface from sign-in attempts or from independent
 * health checks, not from [GmsAvailabilityPort]. Reserved for future
 * adapters (e.g., a `KeystoreHealthPort`) per FR-013.
 */
class AuthAvailabilityAndroidImpl(
    private val gmsAvailabilityPort: GmsAvailabilityPort
) : AuthAvailability {

    override suspend fun check(): AuthAvailabilityStatus {
        return when (gmsAvailabilityPort.status()) {
            GmsStatus.Available -> AuthAvailabilityStatus.Available
            is GmsStatus.MissingRecoverable -> AuthAvailabilityStatus.Available
            is GmsStatus.MissingFatal -> AuthAvailabilityStatus.Unavailable(
                AvailabilityReason.NoSupportedProvider
            )
        }
    }
}
