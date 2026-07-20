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
}

// Architecture fitness rules run as ordinary unit tests
// (app/src/test/java/com/launcher/app/fitness/). Detekt used to host them as
// custom rules; it never loaded them (TASK-140) — and a rule that fails to load
// reports nothing and passes, which is worse than having no rule.
//
// A test is harder to silence, but not immune: these rules scan source TEXT, and Gradle infers
// a test task's inputs from its classpath. Editing a file in another build variant left the
// task UP-TO-DATE and the rules did not run at all — the same silence in different clothes
// (TASK-142). The scanned tree is now declared as an explicit input in app/build.gradle.kts.
// If you add a rule that reads files Gradle cannot see, extend that declaration too.
tasks.register("fitnessCheck") {
    group = "verification"
    description = "Runs the architecture fitness rules across core/ and app/."
    dependsOn(":app:testMockBackendDebugUnitTest")
}


