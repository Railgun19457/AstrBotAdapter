plugins {
    `java-library`
}

dependencies {
    implementation(project(":common-core"))
    implementation(project(":platform-spi"))
    compileOnly("com.velocitypowered:velocity-api:3.3.0-SNAPSHOT")
    compileOnly("com.google.inject:guice:5.1.0")
}

tasks.jar {
    dependsOn(configurations.runtimeClasspath)
    archiveFileName.set("AstrBotAdapter-v${project.version}-Velocity.jar")
    from({ configurations.runtimeClasspath.get().filter { it.exists() }.map { if (it.isDirectory) it else zipTree(it) } })
}
