package com.launcher.api.setup

/**
 * Domain-level description of an Intent the launcher wants to fire (spec 010
 * Plan §3, CHK-domain-007).
 *
 * Carries ONLY primitives — never `android.content.Intent`, `Uri`, `ComponentName`,
 * or `Bundle`. The adapter responsible for navigation (`SetupIntentResolver` in
 * androidMain) is the only place where these primitive fields turn into a real
 * platform `Intent` — keeps domain code testable without an Android runtime
 * (enforced by [com.launcher.test.fitness.Spec010IsolationTest.T009]).
 *
 * Example: ROLE_HOME request →
 *   `IntentSpec(category = "role.home", action = "request", extras = emptyMap())`.
 *
 * The adapter knows that `category = "role.home"` maps to
 * `RoleManager.createRequestRoleIntent(ROLE_HOME)` on API ≥ 29, and to the
 * legacy `CATEGORY_HOME` chooser on API 26-28 (plan §11 C-6 fallback).
 */
data class IntentSpec(
    val category: String,
    val action: String,
    val extras: Map<String, String> = emptyMap(),
)
