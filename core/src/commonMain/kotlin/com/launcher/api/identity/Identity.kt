package com.launcher.api.identity

/**
 * Authenticated principal in the spec 007 trust model. Both subtypes wrap an
 * opaque Firebase Auth UID; the discriminator [AdminIdentity] vs
 * [ManagedIdentity] is a **role** assigned at DI scope (admin-mode vs
 * Managed-mode), not a property of the UID itself.
 *
 * Domain types only — no Firebase SDK reference (CLAUDE.md §1).
 *
 * Naming note: "Managed" replaces the historical "Old" naming across the
 * project (universal-app convention saved as memory
 * `project_managed_naming_convention.md`).
 */
sealed interface Identity {
    val firebaseAuthUid: String
}
