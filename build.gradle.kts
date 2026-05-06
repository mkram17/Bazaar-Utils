plugins {
    id("fabric-loom") version "1.15-SNAPSHOT"
    `maven-publish`
    java
    id("me.modmuss50.mod-publish-plugin") version "0.8.4"
    id("com.gradleup.shadow") version "9.2.2"
}

base {
    archivesName.set(property("mod.id").toString())
}

repositories {
    maven("https://pkgs.dev.azure.com/djtheredstoner/DevAuth/_packaging/public/maven/v1") {
        name = "Dev Auth"
    }
    maven("https://maven.meteordev.org/releases") {
        name = "meteor-maven"
    }
    maven("https://repo.hypixel.net/repository/Hypixel/") {
        name = "Hypixel"
    }
    maven("https://maven.teamresourceful.com/repository/maven-public/"){
        name = "Resourceful Config"
    }
    maven("https://maven.terraformersmc.com/") {
        name = "Terraformers (for gui)"
    }
    maven("https://maven.wispforest.io") {
        name = "Owo Lib"
    }
    maven("https://jitpack.io") {
        name = "Jit Pack"
    }
    maven("https://maven.fabricmc.net/") {
        name = "FabricMC"
    }
    maven("https://repo.nea.moe/releases"){
        name = "Nea Repo for Auto Update"
    }

    exclusiveContent {
        forRepository {
            maven {
                url = uri("https://cursemaven.com")
                name = "CurseMaven" // Repository name is often required for exclusiveContent
            }
        }
        filter {
            includeGroup("curse.maven")
        }
        forRepository {
            maven {
                url = uri("https://api.modrinth.com/maven")
                name = "Modrinth"
            }
        }
        filter {
            includeGroup("maven.modrinth")
        }
    }
    mavenCentral()
}

class ModDependencies {
    operator fun get(name: String) = property("deps.$name").toString()
}
val deps = ModDependencies()
val mcVersion = stonecutter.current.version
val maxMcVersion = deps["core.maxMcVersion"]
val fabricKotlinVersion = property("fabric_kotlin_version").toString().trim()
val modMenuVersion = deps["modmenu_version"]
val orbitVersion = deps["orbit_version"]
val devAuthVersion = deps["devauth_version"]
val hypixelApiVersion = deps["hypixel_api_version"]
val apacheHttpClientVersion = deps["apache_httpclient_version"]
val apacheHttpCoreVersion = deps["apache_httpcore_version"]
val commonsLoggingVersion = deps["commons_logging_version"]
val commonsCodecVersion = deps["commons_codec_version"]
val lombokVersion = deps["lombok_version"]
val mixinConstraintsVersion = deps["mixinconstraints_version"]
val gsonExtrasVersion = deps["gson_extras_version"]
val hypixelModApiVersion = deps["hypixel_mod_api_version"]
val owoLibVersion = deps["owo_version"]
val resourcefulConfigVersion = deps["resourcefulconfig_version"]
val autoUpdateVersion = deps["autoupdate_version"]
val skyblockerVersion = deps["skyblocker_version"]
group = property("maven_group")!!
val versionNumber = property("mod_version").toString().trim()
val releaseChannel = property("mod_release_channel").toString().trim().ifEmpty { "stable" }.lowercase()

require(releaseChannel in setOf("stable", "beta", "alpha")) {
    "mod_release_channel must be one of: stable, beta, alpha"
}
val preReleaseNumber = property("mod_prerelease_number").toString().trim().toIntOrNull()
    ?: error("mod_prerelease_number must be a valid integer")

require(preReleaseNumber >= 0) {
    "mod_prerelease_number must be >= 0, got $preReleaseNumber"
}

val prereleaseSuffix = if (preReleaseNumber == 0) "" else ".$preReleaseNumber"
val releaseLabel = "$versionNumber-$releaseChannel$prereleaseSuffix"

version = "$releaseLabel+mc$mcVersion"

dependencies {
    minecraft("com.mojang:minecraft:${mcVersion}")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:${deps["fabricLoaderVersion"]}")

    modImplementation("net.fabricmc.fabric-api:fabric-api:${deps["fabric_api"]}")

    modLocalRuntime("maven.modrinth:hypixel-mod-api:$hypixelModApiVersion")
    modRuntimeOnly("me.djtheredstoner:DevAuth-fabric:$devAuthVersion")

    implementation("meteordevelopment:orbit:$orbitVersion")
    include("meteordevelopment:orbit:$orbitVersion")

    modImplementation("tech.thatgravyboat:skyblock-api:${deps["skyblock_api_version"]}") {
        capabilities { requireCapability("tech.thatgravyboat:skyblock-api-${deps["skyblock_api_platform"]}") }
    }
    include("tech.thatgravyboat:skyblock-api:${deps["skyblock_api_version"]}") {
        capabilities { requireCapability("tech.thatgravyboat:skyblock-api-${deps["skyblock_api_platform"]}-remapped") }
    }

    compileOnly("org.jetbrains.kotlin:kotlin-stdlib")
    modImplementation("net.fabricmc:fabric-language-kotlin:$fabricKotlinVersion")

    modImplementation("net.hypixel:hypixel-api-transport-apache:$hypixelApiVersion")
    include("net.hypixel:hypixel-api-transport-apache:$hypixelApiVersion")
    include("net.hypixel:hypixel-api-core:$hypixelApiVersion")

    // Apache HTTP Client + all transitive deps — no longer bundled as of 1.21.11
    include("org.apache.httpcomponents:httpclient:$apacheHttpClientVersion")
    include("org.apache.httpcomponents:httpcore:$apacheHttpCoreVersion")
    include("commons-logging:commons-logging:$commonsLoggingVersion")
    include("commons-codec:commons-codec:$commonsCodecVersion")

    // Config lib and settings screen
    modImplementation("com.teamresourceful.resourcefulconfig:resourcefulconfig-fabric-$resourcefulConfigVersion")

    modCompileOnly("com.terraformersmc:modmenu:$modMenuVersion")

    // Project Lombok
    compileOnly("org.projectlombok:lombok:$lombokVersion")
    annotationProcessor("org.projectlombok:lombok:$lombokVersion")

    testCompileOnly("org.projectlombok:lombok:$lombokVersion")
    testAnnotationProcessor("org.projectlombok:lombok:$lombokVersion")
    // Mixin Constraints
    include(implementation("com.moulberry:mixinconstraints:$mixinConstraintsVersion")!!)

    //gson extras for easy type adapters
    implementation("org.danilopianini:gson-extras:$gsonExtrasVersion")
    include("org.danilopianini:gson-extras:$gsonExtrasVersion")
    // Skyblocker for compatibility
    modCompileOnly("maven.modrinth:skyblocker-liap:v$skyblockerVersion")

    // Owo Lib for lang features
    modImplementation("io.wispforest:owo-lib:$owoLibVersion")

    // Auto Update Library
    implementation("moe.nea:libautoupdate:$autoUpdateVersion")
    shadow("moe.nea:libautoupdate:$autoUpdateVersion")
}

val buildtimeInjectionTask = tasks.register<com.github.mkram17.bazaarutils.build.BuildtimeInjectionTask>("processInitAnnotations") {
    group = "build"
    description = "Scans for @RunOnInit @RegisterWidget annotations and injects method calls into their respective methods."
    // This task should run after compileJava
    dependsOn(tasks.compileJava)
    // The input is the output directory of the compileJava task
    classesDir.set(tasks.compileJava.get().destinationDirectory)
}

val generateModuleRegistry = tasks.register<com.github.mkram17.bazaarutils.build.ModuleRegistryGeneratingTask>("generateModuleRegistry") {
    group = "build"
    sourcesDir.set(rootProject.file("src/main/java"))
    outputDir.set(layout.buildDirectory.dir("generated/modules"))
}

sourceSets {
    main {
        java.srcDir(generateModuleRegistry.flatMap { it.outputDir })
    }
}

tasks {
    classes {
        dependsOn(buildtimeInjectionTask)
    }

    processResources {
        inputs.property("version", project.version)
        inputs.property("mcVersion", mcVersion)
        inputs.property("minor_update_notes", rootProject.property("minor_update_notes") as String)

        filesMatching("fabric.mod.json") {
            expand(mapOf(
                "version" to project.version,
                "mod_version" to rootProject.property("mod_version"),
                "mcVersion" to mcVersion,
                "maxMcVersion" to maxMcVersion,
                "minor_update_notes" to rootProject.property("minor_update_notes")
            ))
        }
    }

    jar {
        from("LICENSE"){
            rename { "${it}_${archiveBaseName.get()}" }
        }
    }

    shadowJar {
        configurations = listOf(project.configurations.shadow.get())
    }
    remapJar {
        dependsOn(shadowJar)
        inputFile.set(shadowJar.get().archiveFile)
    }
}
loom {
    runConfigs.all {
        ideConfigGenerated(true) // Run configurations are not created for subprojects by default
        runDir = "../../run" // Use a shared run folder and create separate worlds
    }
}
java {
    withSourcesJar()
}

publishMods {
    file = tasks.remapJar.get().archiveFile
    additionalFiles.from(tasks.remapSourcesJar.get().archiveFile)
    version = project.version.toString()

    type = when (releaseChannel) {
        "alpha" -> ALPHA
        "beta" -> BETA
        else -> STABLE
    }
    modLoaders.add("fabric")
    changelog = rootProject.file("UPDATES.MD").readText()
    displayName = "Bazaar Utils v$releaseLabel for $mcVersion"
    dryRun = true

    modrinth {
        accessToken = providers.environmentVariable("MODRINTH_TOKEN")
        projectId = "c4u7nzUZ"
        minecraftVersions.add(mcVersion)

        requires("fabric-api", "resourceful-config")
        optional("modmenu")
    }
    github {
        accessToken = providers.environmentVariable("GITHUB_TOKEN")
        repository = "mkram17/Bazaar-Utils"
        commitish = "modern"
        tagName = "v" + project.version.toString()
        type = when (releaseChannel) {
            "alpha" -> ALPHA
            "beta" -> BETA
            else -> STABLE
        }
    }
    curseforge {
        accessToken = providers.environmentVariable("CURSEFORGE_TOKEN")
        projectId = "1342860"
    }
}