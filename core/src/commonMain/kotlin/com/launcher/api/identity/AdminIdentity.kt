package com.launcher.api.identity

/**
 * The admin device (e.g. телефон внука). Same UID-shape as [ManagedIdentity];
 * the role split is enforced at the Koin DI scope (admin-mode vs Managed-mode
 * source-sets / scopes), not by the UID itself.
 */
@JvmInline
value class AdminIdentity(override val firebaseAuthUid: String) : Identity
