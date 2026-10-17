plugins {
    id("com.diffplug.spotless") version "8.10.4"
}

repositories {
    mavenCentral()
}

layout.buildDirectory.set(layout.projectDirectory.dir("formatting/build"))

spotless {
    // Formatting is opt-in locally and advisory in CI; never attach it to check/build.
    setEnforceCheck(false)
    ratchetFrom(providers.gradleProperty("formattingBase").getOrElse("origin/v1.0.0"))
    lineEndings = com.diffplug.spotless.LineEnding.UNIX

    java {
        // Explicit source paths keep generated registries and other build outputs out.
        target(
            fileTree(".") {
                include("src/main/java/**/*.java", "src/test/java/**/*.java", "buildSrc/src/main/java/**/*.java")
            }
        )
        eclipse("4.38").configProperties(
            """
            org.eclipse.jdt.core.compiler.source=25
            org.eclipse.jdt.core.compiler.compliance=25
            org.eclipse.jdt.core.compiler.codegen.targetPlatform=25
            org.eclipse.jdt.core.formatter.tabulation.char=space
            org.eclipse.jdt.core.formatter.tabulation.size=4
            org.eclipse.jdt.core.formatter.indentation.size=4
            org.eclipse.jdt.core.formatter.lineSplit=120
            """.trimIndent()
        )
        trimTrailingWhitespace()
        endWithNewline()
    }
}
