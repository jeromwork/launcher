package com.launcher.api.setup

/**
 * Where a [SetupCheck]'s status is consumed (spec 010 FR-017, FR-020a).
 *
 * Drives both *when* the check is re-run and *where* the result is rendered:
 *  - [Settings] → re-run on Settings screen `Lifecycle.RESUMED`; result feeds
 *    the `WhatNeedsConfiguringScreen` + the `!N` / `?M` badge.
 *  - [MainScreen] → reserved seam for the anticipated spec 013 «inline нудный
 *    статус-бар» consumer that surfaces a subset of checks on the home screen.
 *    Today no UI uses it; the variant exists so [SetupCheck.surfaces] can be
 *    typed precisely and the spec-013 consumer arrives without a schema bump.
 *
 * TODO(C-5 — spec 010 plan §11): once spec 013 lands and pins down its exact
 * `MainScreen` consumer (which checks, which copy), revisit whether this stays
 * a sealed pair or graduates to a richer enum (e.g. a `MainScreenBanner` slot
 * with an explicit priority). If spec 013 never ships, fold this seam back
 * down to a non-sealed boolean — the spec 010 wizard only needs `Settings`.
 */
sealed class Surface {
    object Settings : Surface()
    object MainScreen : Surface()
}
