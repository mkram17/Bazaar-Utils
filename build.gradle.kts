
plugins {
    id("fabric-loom") version "1.11-SNAPSHOT"
    id("maven-publish")
    `maven-publish`
    java
    id("me.modmuss50.mod-publish-plugin") version "0.8.4"
}

group = property("maven_group")!!
version = "v" + property("mod_version") as String + "+mc" + property("deps.core.mcVersion") as String

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

dependencies {
    minecraft("com.mojang:minecraft:${mcVersion}")
    mappings("net.fabricmc:yarn:${mcVersion}+build.${deps["yarn_build"]}:v2")
    modImplementation("net.fabricmc:fabric-loader:${deps["fabricLoaderVersion"]}")

    modImplementation("net.fabricmc.fabric-api:fabric-api:${deps["fabric_api"]}")

    modRuntimeOnly("me.djtheredstoner:DevAuth-fabric:1.2.1")

    implementation("meteordevelopment:orbit:0.2.4")
    include("meteordevelopment:orbit:0.2.4")

    modImplementation("net.hypixel:hypixel-api-transport-apache:4.4")
    include("net.hypixel:hypixel-api-transport-apache:4.4")
    include("net.hypixel:hypixel-api-core:4.4")

    // Config lib and settings screen
    modImplementation("com.teamresourceful.resourcefulconfig:resourcefulconfig-fabric-${deps["resourcefulconfig_version"]}")

    modCompileOnly("com.terraformersmc:modmenu:${property("modmenu_version")}")

    // Project Lombok
    compileOnly("org.projectlombok:lombok:1.18.36")
    annotationProcessor("org.projectlombok:lombok:1.18.36")

    testCompileOnly("org.projectlombok:lombok:1.18.36")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.36")
    // Mixin Constraints
    include(implementation("com.moulberry:mixinconstraints:1.0.8")!!)

    //gson extras for easy type adapters
    implementation("org.danilopianini:gson-extras:3.3.0")
    include("org.danilopianini:gson-extras:3.3.0")
    // Skyblocker for compatibility
    modCompileOnly("maven.modrinth:skyblocker-liap:v${deps["skyblocker_version"]}")

    // Owo Lib
    modImplementation("io.wispforest:owo-lib:${deps["owo_version"]}")
    //  If a player installs without installing owo, sentinel will prevent their game from launching and instead open a window warning them that owo is required.
    include("io.wispforest:owo-sentinel:${deps["owo_version"]}")

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
        inputs.property("major_update_notes", rootProject.property("major_update_notes") as String)

        filesMatching("fabric.mod.json") {
            expand(mapOf(
                "version" to project.version,
                "mod_version" to rootProject.property("mod_version"),
                "mcVersion" to mcVersion,
                "maxMcVersion" to maxMcVersion,
                "major_update_notes" to rootProject.property("major_update_notes")
            ))
        }
    }

    jar {
        from("LICENSE"){
            rename { "${it}_${archiveBaseName.get()}" }
        }
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
    changelog = "Changelog"
    type = if(version.toString().contains("alpha")) ALPHA else STABLE
    modLoaders.add("fabric")
    changelog = rootProject.file("UPDATES.MD").readText()
    version = "v" + property("mod_version").toString()
    displayName = "Bazaar Utils v${property("mod_version")} for $mcVersion"
//    dryRun = true

    modrinth {
        accessToken = providers.environmentVariable("MODRINTH_TOKEN")
        projectId = "c4u7nzUZ"
        version = property("mod_version") as String + "+mc" + property("deps.core.mcVersion") as String
        minecraftVersions.add(mcVersion)

        requires("fabric-api", "yacl")
        optional("modmenu")
    }
    github {
        accessToken = providers.environmentVariable("GITHUB_TOKEN")
        repository = "mkram17/Bazaar-Utils"
        commitish = "modern"
        type = STABLE
    }
    curseforge {
        accessToken = providers.environmentVariable("CURSEFORGE_TOKEN")
        projectId = "1342860"
    }
}