pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

// A separate build keeps formatting plugins and failures out of the mod's build.
// Spotless's Git ratchet needs its project directory to contain every source target.
rootProject.name = "bazaar-utils-formatting"
rootProject.projectDir = file("..")
rootProject.buildFileName = "formatting/build.gradle.kts"
// Select the relocated root instead of Gradle falling back to an empty build in this folder.
gradle.startParameter.projectDir = rootProject.projectDir
