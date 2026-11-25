pluginManagement {
    // The JetBrains Runtime without JCEF, for the toolchains that ask for the JetBrains vendor (see JbrToolchainPlugin).
    includeBuild("build-logic")

    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        google()
        mavenCentral()
    }
}

plugins {
    // Must come before Foojay: the toolchain resolvers are asked in the order they are applied, and Foojay's JetBrains Runtime has JCEF.
    id("app.kash.jbr-toolchain")

    // Downloads the JDKs the subprojects' toolchains ask for. Pinned here rather than in
    // gradle/libs.versions.toml because a settings plugin can't resolve a version-catalog alias -
    // the catalog isn't built yet at this point.
    // https://github.com/gradle/foojay-toolchains/releases
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "KaSH"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(
    ":app",
    ":app-core",
    ":browser",
    ":jcef-native",
    ":macos",
)
