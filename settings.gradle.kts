pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/")
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.8.2"
}

stonecutter {
    kotlinController = true
    centralScript = "build.gradle.kts"
    create(rootProject){
        versions("1.21.11")
        vcsVersion = "1.21.11"
    }
}

rootProject.name = "Bazaar-Utils-Modern"