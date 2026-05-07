package com.launcher.api

/**
 * Single launcher-visible item (data-model.md — Catalog entry).
 * Launch details are opaque; routing goes through [ActionDispatcher].
 */
data class CatalogEntry(
    val stableKey: String,
    val displayLabel: String,
    val contentDescription: String?,
    val isLaunchable: Boolean,
)

data class CatalogSnapshot(
    val generation: Long,
    val entries: List<CatalogEntry>,
)
