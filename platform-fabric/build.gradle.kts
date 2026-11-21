plugins {
    id("net.fabricmc.fabric-loom") version "1.17.18"
}

import org.gradle.api.tasks.bundling.AbstractArchiveTask

val minecraftVersion = "26.1.2"
val fabricLoaderVersion = "0.19.3"
val fabricApiVersion = "0.155.2+26.1.2"

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(25)
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    // Minecraft 26.x is unobfuscated; no Yarn/Mojmap dependency is required.
    implementation("net.fabricmc:fabric-loader:$fabricLoaderVersion")
    implementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")

    implementation(project(":common-core"))
    implementation(project(":platform-spi"))
    implementation(project(":common-protocol"))
    implementation(project(":common-transport"))
    implementation(project(":common-util"))

    include(project(":common-core"))
    include(project(":platform-spi"))
    include(project(":common-protocol"))
    include(project(":common-transport"))
    include(project(":common-util"))
    include("com.google.code.gson:gson:2.11.0")
    include("org.bouncycastle:bcprov-jdk18on:1.80")
    include("org.yaml:snakeyaml:2.2")
    include("org.java-websocket:Java-WebSocket:1.5.7")
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("AstrBotAdapter")
    archiveClassifier.set("Fabric-dev")
}

// Prefer the final jar task for unobfuscated 26.x builds.
tasks.matching { it.name == "remapJar" }.configureEach {
    enabled = false
}

tasks.named<AbstractArchiveTask>("jar") {
    archiveFileName.set("AstrBotAdapter-v${project.version}-Fabric.jar")
}
