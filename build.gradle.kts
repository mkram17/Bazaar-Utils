plugins {
    id("fabric-loom") version "1.15-SNAPSHOT"
    id("maven-publish")
    `maven-publish`
    java
    id("me.modmuss50.mod-publish-plugin") version "0.8.4"
}

base {
    archivesName.set(property("mod.id").toString())
}

repositories {
    maven {
        name = "Dev Auth"
        url = uri("https://pkgs.dev.azure.com/djtheredstoner/DevAuth/_packaging/public/maven/v1")
    }
    maven {
        name = "meteor-maven"
        url = uri("https://maven.meteordev.org/releases")
    }
    maven {
        name = "Hypixel"
        url = uri("https://repo.hypixel.net/repository/Hypixel/")
    }
    maven {
        name = "YACL"
        url = uri("https://maven.isxander.dev/releases")
    }
    maven {
        name = "Terraformers (for gui)"
        url = uri("https://maven.terraformersmc.com/")
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
group = property("maven_group")!!
val versionNumber = property("mod_version") as String
val releaseChannel = property("mod_release_channel") as String
version = "$versionNumber-$releaseChannel+mc$mcVersion"

dependencies {
    minecraft("com.mojang:minecraft:${mcVersion}")
    mappings("net.fabricmc:yarn:${mcVersion}+build.${deps["yarn_build"]}:v2")
    modImplementation("net.fabricmc:fabric-loader:${deps["fabricLoaderVersion"]}")

    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("deps.fabric_api")}")

    modRuntimeOnly("me.djtheredstoner:DevAuth-fabric:1.2.1")

    implementation("meteordevelopment:orbit:0.2.4")
    include("meteordevelopment:orbit:0.2.4")

    modImplementation("net.hypixel:hypixel-api-transport-apache:4.4")
    include("net.hypixel:hypixel-api-transport-apache:4.4")
    include("net.hypixel:hypixel-api-core:4.4")

    // Apache HTTP Client + all transitive deps — no longer bundled as of 1.21.11
    include("org.apache.httpcomponents:httpclient:4.5.14")
    include("org.apache.httpcomponents:httpcore:4.4.16")
    include("commons-logging:commons-logging:1.2")
    include("commons-codec:commons-codec:1.16.0")

    // Config lib and settings screen
    modImplementation("dev.isxander:yet-another-config-lib:${deps["yacl_version"]}-fabric")

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
}

val buildtimeInjectionTask = tasks.register<com.github.mkram17.bazaarutils.build.BuildtimeInjectionTask>("processInitAnnotations") {
    group = "build"
    description = "Scans for @RunOnInit @RegisterWidget annotations and injects method calls into their respective methods."
    // This task should run after compileJava
    dependsOn(tasks.compileJava)
    // The input is the output directory of the compileJava task
    classesDir.set(tasks.compileJava.get().destinationDirectory)
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
    version = project.version.toString()

    type = if(releaseChannel == "alpha") ALPHA else STABLE
    modLoaders.add("fabric")
    changelog = rootProject.file("UPDATES.MD").readText()
    displayName = "Bazaar Utils v$versionNumber-$releaseChannel for $mcVersion"
    dryRun = true

    modrinth {
        accessToken = providers.environmentVariable("MODRINTH_TOKEN")
        projectId = "c4u7nzUZ"
        minecraftVersions.add(mcVersion)

        requires("fabric-api", "yacl")
        optional("modmenu")
    }
    github {
        accessToken = providers.environmentVariable("GITHUB_TOKEN")
        repository = "mkram17/Bazaar-Utils"
        commitish = "modern"
        tagName = "v" + project.version.toString()
        type = STABLE
    }
    curseforge {
        accessToken = providers.environmentVariable("CURSEFORGE_TOKEN")
        projectId = "1342860"
    }
}