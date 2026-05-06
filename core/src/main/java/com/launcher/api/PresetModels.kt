package com.launcher.api

/**
 * Three universal usage modes any user picks at first launch.
 * Slug is the wire-format identifier — never rename without versioned migration.
 */
enum class FlowPreset(
    val slug: String,
    val titleResKey: String,
    val descriptionResKey: String,
    val iconResKey: String,
) {
    WORKSPACE(
        slug = "workspace",
        titleResKey = "preset_workspace_title",
        descriptionResKey = "preset_workspace_description",
        iconResKey = "ic_preset_workspace",
    ),
    LAUNCHER(
        slug = "launcher",
        titleResKey = "preset_launcher_title",
        descriptionResKey = "preset_launcher_description",
        iconResKey = "ic_preset_launcher",
    ),
    SIMPLE_LAUNCHER(
        slug = "simple-launcher",
        titleResKey = "preset_simple_launcher_title",
        descriptionResKey = "preset_simple_launcher_description",
        iconResKey = "ic_preset_simple_launcher",
    );

    companion object {
        fun fromSlug(slug: String?): FlowPreset? = values().firstOrNull { it.slug == slug }
    }
}
