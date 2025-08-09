pluginManagement {
    repositories {
        maven("https://maven.deftu.dev/releases")
        maven("https://maven.fabricmc.net")
        maven("https://maven.architectury.dev/")
        maven("https://maven.minecraftforge.net")
        maven("https://repo.essential.gg/repository/maven-public")
        maven("https://server.bbkr.space/artifactory/libs-release/")
        maven("https://jitpack.io/")
        maven("https://maven.deftu.dev/snapshots")

        mavenCentral()
        gradlePluginPortal()
        mavenLocal()
    }
}
val projectName: String = extra["mod.name"]?.toString() ?: "Bazaar-Utils-Modern"

// Configures the root project Gradle name based on the value in `gradle.properties`
rootProject.name = projectName
//plugins {
//    id("dev.kikugie.stonecutter") version "0.7"
//}
//
//stonecutter {
//    kotlinController = true
//    centralScript = "build.gradle.kts"
//    create(rootProject){
//        versions("1.21.5", "1.21.6")
//        vcsVersion = "1.21.6"
//    }
//}
//
//rootProject.name = "Bazaar-Utils-Modern"