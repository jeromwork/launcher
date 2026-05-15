package com.launcher.api.contacts

import com.launcher.api.result.Outcome

/**
 * Suspendable wrapper over Android `ActivityResultContracts.PickContact`
 * (spec 009 FR-024). Domain projection — adapter resolves
 * `ContactsContract` Cursor / URI, never leaks them upward (CLAUDE.md
 * rule 1).
 *
 * Permission policy (FR-023): `READ_CONTACTS` is requested lazily by the
 * adapter before the first call. If denied, the adapter returns
 * [PickError.PermissionDenied]; UI surfaces deep-link to system settings
 * (FR-023b) and manual-entry alternative (FR-023a).
 */
interface SystemContactPicker {

    suspend fun pickContact(): Outcome<RawPickerContact, PickError>
}

data class RawPickerContact(
    val displayName: String,
    val phoneNumbers: List<String>,
)

sealed interface PickError {
    data object UserCancelled : PickError
    data object PermissionDenied : PickError
    data class Other(val cause: Throwable) : PickError
}
