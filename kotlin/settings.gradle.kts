pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "sqisign-kotlin"

// AndroidX Microbenchmark of the Android target on a physical device; see benchmarks/README.md.
include(":benchmark-android")
