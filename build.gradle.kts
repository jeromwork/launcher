plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.sqldelight) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.detekt) apply false
}

// TASK-65 R5 — detektFoundation alias runs the 2 custom rules
// (PresetIdBranching + ExtractionReadiness) against core/ and app/.
// Configuration kept minimal; rules live in :lint-rules.
subprojects {
    if (name in setOf("core", "app")) {
        plugins.apply("io.gitlab.arturbosch.detekt")
        extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
            // Disable the default rules — we only want our 2 custom rules.
            buildUponDefaultConfig = false
            allRules = false
            ignoreFailures = false
            source.setFrom(files("src"))
        }
        dependencies {
            "detektPlugins"(project(":lint-rules"))
        }
    }
}

tasks.register("detektFoundation") {
    group = "verification"
    description = "Runs the 2 TASK-65 custom Detekt rules across core/ and app/."
    dependsOn(":core:detekt", ":app:detekt")
}

