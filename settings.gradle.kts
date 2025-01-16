pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://oss.sonatype.org/content/repositories/snapshots")
        maven("https://maven.architectury.dev/")
        maven("https://maven.fabricmc.net")
        maven("https://maven.minecraftforge.net/")
        maven("https://repo.spongepowered.org/maven/")
        maven("https://repo.sk1er.club/repository/maven-releases/")


        //testing
        // Releases
        maven("https://maven.deftu.dev/releases")
        maven("https://repo.essential.gg/repository/maven-public")
        maven("https://server.bbkr.space/artifactory/libs-release/")
        maven("https://jitpack.io/")

        // Snapshots
        maven("https://maven.deftu.dev/snapshots")
        mavenLocal()
    }
//    resolutionStrategy {
//        eachPlugin {
//            when (requested.id.id) {
//                "gg.essential.loom" -> useModule("gg.essential:architectury-loom:${requested.version}")
//            }
//        }
//    }
}

//plugins {
//    id("org.gradle.toolchains.foojay-resolver-convention") version("0.6.0")
//}


rootProject.name = "Bazaar-Utils"
