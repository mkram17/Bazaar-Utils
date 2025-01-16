import dev.deftu.gradle.utils.GameSide
import org.apache.commons.lang3.SystemUtils

plugins {
    idea
    java
    id("gg.essential.loom") version "0.10.0.+"
    id("dev.architectury.architectury-pack200") version "0.1.3"
    id("com.github.johnrengelman.shadow") version "8.1.1"


    //testing
    kotlin("jvm") version("2.0.0")
    val dgtVersion = "2.17.0"
    id("dev.deftu.gradle.tools") version(dgtVersion) // Applies several configurations to things such as the Java version, project name/version, etc.
    id("dev.deftu.gradle.tools.resources") version(dgtVersion) // Applies resource processing so that we can replace tokens, such as our mod name/version, in our resources.
    id("dev.deftu.gradle.tools.bloom") version(dgtVersion) // Applies the Bloom plugin, which allows us to replace tokens in our source files, such as being able to use `@MOD_VERSION` in our source files.
    id("dev.deftu.gradle.tools.shadow") version(dgtVersion) // Applies the Shadow plugin, which allows us to shade our dependencies into our mod JAR. This is NOT recommended for Fabric mods, but we have an *additional* configuration for those!
    id("dev.deftu.gradle.tools.minecraft.loom") version(dgtVersion) // Applies the Loom plugin, which automagically configures Essential's Architectury Loom plugin for you.
    id("dev.deftu.gradle.tools.minecraft.releases") version(dgtVersion) // Applies the Minecraft auto-releasing plugin, which allows you to automatically release your mod to CurseForge and Modrinth.

}
dependencies{
    implementation ("net.hypixel:hypixel-api-transport-apache:4.4")

    // Basic OneConfig dependencies for legacy versions. See OneConfig example mod for more info
    compileOnly("cc.polyfrost:oneconfig-1.8.9-forge:0.2.2-alpha+") // Should not be included in jar
    // include should be replaced with a configuration that includes this in the jar
    include("cc.polyfrost:oneconfig-wrapper-launchwrapper:1.0.0-beta+") // Should be included in jar

}
repositories{
    mavenCentral()
    maven("https://repo.hypixel.net/repository/Hypixel/")

    maven("https://repo.polyfrost.cc/releases")

}
//Constants:

val baseGroup: String by project
val mcVersion: String by project
val version: String by project
val mixinGroup = "$baseGroup.mixin"
val modid: String by project
val transformerFile = file("src/main/resources/accesstransformer.cfg")

// Toolchains:
java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(8))
}

//testing
toolkitLoomHelper{

    //only for oneConfig v1
//    useOneConfig {
//        version = "1.0.0-alpha.55"
//        loaderVersion = "1.1.0-alpha.35"
//
//        usePolyMixin = true
//        polyMixinVersion = "0.8.4+build.2"
//
//        applyLoaderTweaker = true
//
//        for (module in arrayOf("commands", "config", "config-impl", "events", "internal", "ui", "utils")) {
//            +module
//        }
//    }

    useDevAuth("1.2.1")
    useMixinExtras("0.4.1")

    disableRunConfigs(GameSide.SERVER)

    // Defines the name of the Mixin refmap, which is used to map the Mixin classes to the obfuscated Minecraft classes.
    if (!mcData.isNeoForge) {
        useMixinRefMap(modData.id)
    }
    if (mcData.isForge) {
        // Configures the Mixin tweaker if we are building for Forge.
        useForgeMixin(modData.id)
    }

}
tasks {
    jar { // loads OneConfig at launch. Add these launch attributes but keep your old attributes!
        manifest.attributes += mapOf(
            "ModSide" to "CLIENT",
            "TweakOrder" to 0,
            "ForceLoadAsMod" to true,
            "TweakClass" to "cc.polyfrost.oneconfig.loader.stage0.LaunchWrapperTweaker"
        )
    }
}
// Minecraft configuration:
//loom {
//    log4jConfigs.from(file("log4j2.xml"))
//    launchConfigs {
//        "client" {
//            // If you don't want mixins, remove these lines
//            property("mixin.debug", "true")
////            arg("--tweakClass", "org.spongepowered.asm.launch.MixinTweaker")
//            arg("--tweakClass", "cc.polyfrost.oneconfig.loader.stage0.LaunchWrapperTweaker")
//        }
//    }
//    runConfigs {
//        "client" {
//            if (SystemUtils.IS_OS_MAC_OSX) {
//                // This argument causes a crash on macOS
//                vmArgs.remove("-XstartOnFirstThread")
//            }
//        }
//        remove(getByName("server"))
//    }
//    forge {
//        pack200Provider.set(dev.architectury.pack200.java.Pack200Adapter())
//        // If you don't want mixins, remove this lines
//        mixinConfig("mixins.$modid.json")
//	    if (transformerFile.exists()) {
//			println("Installing access transformer")
//		    accessTransformer(transformerFile)
//	    }
//    }
//    // If you don't want mixins, remove these lines
//    mixin {
//        defaultRefmapName.set("mixins.$modid.refmap.json")
//    }
//}
//
//sourceSets.main {
//    output.setResourcesDir(sourceSets.main.flatMap { it.java.classesDirectory })
//}
//
//// Dependencies:
//val shade: Configuration by configurations.creating {
//    configurations.implementation.get().extendsFrom(this)
//}
//
//repositories {
//    mavenCentral()
//    maven("https://repo.spongepowered.org/maven/")
//    // If you don't want to log in with your real minecraft account, remove this line
//    maven("https://pkgs.dev.azure.com/djtheredstoner/DevAuth/_packaging/public/maven/v1")
//    maven("https://repo.hypixel.net/repository/Hypixel/")
//    maven("https://repo.polyfrost.cc/releases")
//}
//
//val shadowImpl: Configuration by configurations.creating {
//    configurations.implementation.get().extendsFrom(this)
//}
//
//dependencies {
//    minecraft("com.mojang:minecraft:1.8.9")
//    mappings("de.oceanlabs.mcp:mcp_stable:22-1.8.9")
//    forge("net.minecraftforge:forge:1.8.9-11.15.1.2318-1.8.9")
////    implementation("com.google.code.gson:gson:2.11.0")
//    implementation ("net.hypixel:hypixel-api-transport-reactor:4.4")
//    implementation ("net.hypixel:hypixel-api-transport-apache:4.4")
//
//    // Basic OneConfig dependencies for legacy versions. See OneConfig example mod for more info
//    compileOnly("cc.polyfrost:oneconfig-1.8.9-forge:0.2.2-alpha+") // Should not be included in jar
//    // include should be replaced with a configuration that includes this in the jar
////    include("cc.polyfrost:oneconfig-wrapper-launchwrapper:1.0.0-beta+") // Should be included in jar
//    shade("cc.polyfrost:oneconfig-wrapper-launchwrapper:1.0.0-beta17")
//    // If you don't want mixins, remove these lines
//    shadowImpl("org.spongepowered:mixin:0.7.11-SNAPSHOT") {
//        isTransitive = false
//    }
//    annotationProcessor("org.spongepowered:mixin:0.8.5-SNAPSHOT")
//
//    // If you don't want to log in with your real minecraft account, remove this line
//    runtimeOnly("me.djtheredstoner:DevAuth-forge-legacy:1.2.1")
//
//}
//
//// Tasks:
//
//tasks.withType(JavaCompile::class) {
//    options.encoding = "UTF-8"
//}
//
//tasks.withType(org.gradle.jvm.tasks.Jar::class) {
//    archiveBaseName.set(modid)
//    manifest.attributes.run {
//        this["FMLCorePluginContainsFMLMod"] = "true"
//        this["ForceLoadAsMod"] = "true"
//
//        // If you don't want mixins, remove these lines
//        this["TweakClass"] = "org.spongepowered.asm.launch.MixinTweaker"
//        this["MixinConfigs"] = "mixins.$modid.json"
//	    if (transformerFile.exists())
//			this["FMLAT"] = "${modid}_at.cfg"
//    }
//}
//
//tasks.processResources {
//    inputs.property("version", project.version)
//    inputs.property("mcversion", mcVersion)
//    inputs.property("modid", modid)
//    inputs.property("basePackage", baseGroup)
//
//    filesMatching(listOf("mcmod.info", "mixins.$modid.json")) {
//        expand(inputs.properties)
//    }
//
//    rename("accesstransformer.cfg", "META-INF/${modid}_at.cfg")
//}
//
//
//val remapJar by tasks.named<net.fabricmc.loom.task.RemapJarTask>("remapJar") {
//    archiveClassifier.set("")
//    from(tasks.shadowJar)
//    input.set(tasks.shadowJar.get().archiveFile)
//}
//
//tasks.jar {
//    archiveClassifier.set("without-deps")
//    destinationDirectory.set(layout.buildDirectory.dir("intermediates"))
//    manifest.attributes += mapOf(
//        "ModSide" to "CLIENT",
//        "TweakOrder" to 0,
//        "ForceLoadAsMod" to true,
//        "TweakClass" to "cc.polyfrost.oneconfig.loader.stage0.LaunchWrapperTweaker"
//    )
//}
//
//tasks.shadowJar {
//    destinationDirectory.set(layout.buildDirectory.dir("intermediates"))
//    archiveClassifier.set("non-obfuscated-with-deps")
//    configurations = listOf(shadowImpl)
//    doLast {
//        configurations.forEach {
//            println("Copying dependencies into mod: ${it.files}")
//        }
//    }
//
//    // If you want to include other dependencies and shadow them, you can relocate them in here
//    fun relocate(name: String) = relocate(name, "$baseGroup.deps.$name")
//}
//
//tasks.assemble.get().dependsOn(tasks.remapJar)

