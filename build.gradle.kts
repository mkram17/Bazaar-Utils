@file:Suppress("UnstableApiUsage", "PropertyName")
plugins {
    java
    val dgtVersion = "2.35.0"
    id("dev.deftu.gradle.tools") version(dgtVersion) // Applies several configurations to things such as the Java version, project name/version, etc.
    id("dev.deftu.gradle.tools.resources") version(dgtVersion) // Applies resource processing so that we can replace tokens, such as our mod name/version, in our resources.
    id("dev.deftu.gradle.tools.bloom") version(dgtVersion) // Applies the Bloom plugin, which allows us to replace tokens in our source files, such as being able to use `@MOD_VERSION` in our source files.
    id("dev.deftu.gradle.tools.minecraft.loom") version(dgtVersion) // Applies the Loom plugin, which automagically configures Essential's Architectury Loom plugin for you.
    id("dev.deftu.gradle.tools.minecraft.releases") version(dgtVersion) // Applies the Minecraft auto-releasing plugin, which allows you to automatically release your mod to CurseForge and Modrinth.

}

toolkitLoomHelper {
    useOneConfig {
        version = "1.0.0-alpha.106"
        loaderVersion = "1.1.0-alpha.46"

        for (module in arrayOf("commands", "config", "config-impl", "events", "internal", "ui", "utils")) {
            +module
        }
    }

    useDevAuth("1.2.1")
    useMixinExtras("0.4.1")

    // Turns off the server-side run configs, as we're building a client-sided mod.
    disableRunConfigs(GameSide.SERVER)

    // Defines the name of the Mixin refmap, which is used to map the Mixin classes to the obfuscated Minecraft classes.
    useMixinRefMap(modData.id)
}
//
//group = property("maven_group")!!
//version = "v" + property("mod_version") as String + "+mc" + property("deps.core.mcVersion") as String
//
//base {
//    archivesName.set(property("mod.id").toString())
//}
//
//repositories {
//    maven {
//        name = "Dev Auth"
//        url = uri("https://pkgs.dev.azure.com/djtheredstoner/DevAuth/_packaging/public/maven/v1")
//    }
//    maven {
//        name = "meteor-maven"
//        url = uri("https://maven.meteordev.org/releases")
//    }
//    maven {
//        name = "Hypixel"
//        url = uri("https://repo.hypixel.net/repository/Hypixel/")
//    }
//    maven {
//        name = "YACL"
//        url = uri("https://maven.isxander.dev/releases")
//    }
//    maven {
//        name = "Terraformers (for gui)"
//        url = uri("https://maven.terraformersmc.com/")
//    }
//    maven("https://moulberry.repo.ax/v1") {
//        name = "Moulberry's Maven"
//    }
//
//    exclusiveContent {
//        forRepository {
//            maven {
//                url = uri("https://cursemaven.com")
//                name = "CurseMaven" // Repository name is often required for exclusiveContent
//            }
//        }
//        filter {
//            includeGroup("curse.maven")
//        }
//        forRepository {
//            maven {
//                url = uri("https://api.modrinth.com/maven")
//                name = "Modrinth"
//            }
//        }
//        filter {
//            includeGroup("maven.modrinth")
//        }
//    }
//    mavenCentral()
//}
//
//class ModDependencies {
//    operator fun get(name: String) = property("deps.$name").toString()
//}
//val deps = ModDependencies()
//val mcVersion = stonecutter.current.version
//
//dependencies {
//    minecraft("com.mojang:minecraft:${mcVersion}")
//    mappings("net.fabricmc:yarn:${mcVersion}+build.${deps["yarn_build"]}:v2")
//    modImplementation("net.fabricmc:fabric-loader:${deps["fabricLoaderVersion"]}")
//
//    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("deps.fabric_api")}")
//
//    modRuntimeOnly("me.djtheredstoner:DevAuth-fabric:1.2.1")
//
//    implementation("meteordevelopment:orbit:0.2.4")
//    include("meteordevelopment:orbit:0.2.4")
//
//    modImplementation("net.hypixel:hypixel-api-transport-apache:4.4")
//    include("net.hypixel:hypixel-api-transport-apache:4.4")
//    include("net.hypixel:hypixel-api-core:4.4")
//
//    modImplementation("dev.isxander:yet-another-config-lib:${deps["yacl_version"]}")
//
//    modCompileOnly("com.terraformersmc:modmenu:${property("modmenu_version")}")
//
//    // Project Lombok
//    compileOnly("org.projectlombok:lombok:1.18.36")
//    annotationProcessor("org.projectlombok:lombok:1.18.36")
//
//    testCompileOnly("org.projectlombok:lombok:1.18.36")
//    testAnnotationProcessor("org.projectlombok:lombok:1.18.36")
//
//    //Amecs Reborn
//    modCompileOnly("maven.modrinth:amecs-reborn:${property("amecsreborn_version")}+mc${mcVersion}")
//
//    // Mixin Constraints
//    include(implementation("com.moulberry:mixinconstraints:1.0.8")!!)
//
//    //gson extras for easy type adapters
//    implementation("org.danilopianini:gson-extras:3.3.0")
//    include("org.danilopianini:gson-extras:3.3.0")
//    // Skyblocker for compatibility
//    modCompileOnly("maven.modrinth:skyblocker-liap:v${deps["skyblocker_version"]}")
//}
//
//val buildtimeInjectionTask = tasks.register<com.github.mkram17.bazaarutils.build.BuildtimeInjectionTask>("processInitAnnotations") {
//    group = "build"
//    description = "Scans for @RunOnInit @RegisterWidget annotations and injects method calls into their respective methods."
//    // This task should run after compileJava
//    dependsOn(tasks.compileJava)
//    // The input is the output directory of the compileJava task
//    classesDir.set(tasks.compileJava.get().destinationDirectory)
//}
//
//tasks {
//    classes {
//        dependsOn(buildtimeInjectionTask)
//    }
//
//    processResources {
//        inputs.property("version", project.version)
//        inputs.property("mcVersion", mcVersion)
//        inputs.property("major_update_notes", rootProject.property("major_update_notes") as String)
//
//        filesMatching("fabric.mod.json") {
//            expand(mapOf(
//                "version" to project.version,
//                "mod_version" to rootProject.property("mod_version"),
//                "mcVersion" to mcVersion,
//                "major_update_notes" to rootProject.property("major_update_notes")
//            ))
//        }
//    }
//
//    jar {
//        from("LICENSE"){
//            rename { "${it}_${archiveBaseName.get()}" }
//        }
//    }
//}
//loom {
//    runConfigs.all {
//        ideConfigGenerated(true) // Run configurations are not created for subprojects by default
//        runDir = "../../run" // Use a shared run folder and create separate worlds
//    }
//}
//java {
//    withSourcesJar()
//}
//
//publishMods {
//    file = tasks.remapJar.get().archiveFile
//    additionalFiles.from(tasks.remapSourcesJar.get().archiveFile)
//    changelog = "Changelog"
//    type = BETA
//    modLoaders.add("fabric")
//    changelog = rootProject.file("UPDATES.MD").readText()
//    version = "v" + property("mod_version").toString()
//    displayName = "Bazaar Utils v${property("mod_version")} for $mcVersion"
//    dryRun = true
//
//    modrinth {
//        accessToken = providers.environmentVariable("MODRINTH_TOKEN")
//        projectId = "c4u7nzUZ"
//        version = property("mod_version") as String + "+mc" + property("deps.core.mcVersion") as String
//        minecraftVersions.add(mcVersion)
//
//        requires("fabric-api", "yacl")
//        optional("modmenu", "amecs-reborn")
//    }
//    github {
//        accessToken = providers.environmentVariable("GITHUB_TOKEN")
//        repository = "mkram17/Bazaar-Utils"
//        commitish = "modern"
//        type = STABLE
//
//    }
//}