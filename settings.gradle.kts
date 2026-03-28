pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "launcher"

include(":app", ":core")

// Future feature modules (pattern :feature-* per spec FR-019), e.g.:
// include(":feature-favorites")
