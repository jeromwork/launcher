package com.launcher.api.config

import kotlinx.serialization.Serializable

/**
 * Contact element of `/config/current.contacts[]` (spec 008 §FR-003, contracts/config.md).
 *
 * Identity via [id] (UUID v4) — stable across renames для diff/merge (FR-051).
 *
 * [photoRef] reserved для спека 011 (e2e-encrypted media в Firebase Storage,
 * namespace `private:<uuid>`). Spec 008 leaves it null.
 */
@Serializable
data class Contact(
    val id: ElementId,
    val displayName: String,
    val phoneNumber: String,
    val photoRef: String? = null,
)
