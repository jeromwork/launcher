package com.launcher.api.pairing

/** Discriminated error surface for [PairingService] operations.
 *
 *  The PairingService translates [com.launcher.api.sync.BackendError] into
 *  [PairingError] so the UI layer reasons about pairing concepts
 *  (`TokenAlreadyClaimed`) rather than transport concepts (`TransactionConflict`). */
sealed interface PairingError {
    data object TokenAlreadyClaimed : PairingError
    data object TokenExpired : PairingError
    data object TokenNotFound : PairingError
    data object NetworkUnavailable : PairingError
    data object PermissionDenied : PairingError
    data class Unknown(val message: String) : PairingError
}
