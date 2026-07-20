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

// detektFoundation runs our architecture fitness rules against core/ and app/.
// Rules live in :lint-rules, registered via LauncherRuleSetProvider (the
// META-INF/services entry is what makes Detekt discover them — without it the
// detectors compile, pass their own unit tests, and check nothing; that was the
// state from TASK-65 until TASK-16 fixed the wiring on 2026-07-20).
// config/detekt.yml disables every built-in ruleset: style findings are not
// architecture invariants, and a gate reporting 500+ of them stops being a gate.
subprojects {
    if (name in setOf("core", "app")) {
        plugins.apply("io.gitlab.arturbosch.detekt")
        extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
            buildUponDefaultConfig = false
            allRules = false
            ignoreFailures = false
            config.setFrom(rootProject.files("config/detekt.yml"))
            source.setFrom(files("src"))
        }
        dependencies {
            "detektPlugins"(project(":lint-rules"))
        }
    }
}

tasks.register("detektFoundation") {
    group = "verification"
    description = "Runs the custom architecture fitness rules across core/ and app/."
    dependsOn(":core:detekt", ":app:detekt")
}

