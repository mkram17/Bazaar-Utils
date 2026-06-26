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
        versions("26.1", "26.1.2")
        vcsVersion = "26.1"
    }
}

rootProject.name = "Bazaar-Utils-Modern"