plugins {
    id("fabric-loom") version "1.16.2"
}

import org.gradle.api.tasks.bundling.AbstractArchiveTask

val minecraftVersion = "1.20.1"
val yarnMappings = "1.20.1+build.10"
val fabricLoaderVersion = "0.16.14"
val fabricApiVersion = "0.92.9+1.20.1"

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    mappings("net.fabricmc:yarn:$yarnMappings:v2")
    modImplementation("net.fabricmc:fabric-loader:$fabricLoaderVersion")
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")

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

tasks.named<AbstractArchiveTask>("remapJar") {
    archiveFileName.set("AstrBotAdapter-v${project.version}-Fabric.jar")
}
