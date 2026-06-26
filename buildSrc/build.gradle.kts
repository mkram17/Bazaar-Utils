plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(gradleApi())
    implementation("org.ow2.asm:asm:9.7.1")
    implementation("org.ow2.asm:asm-tree:9.7.1")
}