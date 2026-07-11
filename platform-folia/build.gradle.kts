plugins {
    `java-library`
}

dependencies {
    implementation(project(":common-core"))
    implementation(project(":platform-spi"))
    implementation(project(":platform-bukkit"))
    compileOnly("dev.folia:folia-api:1.20.4-R0.1-SNAPSHOT")
}

tasks.jar {
    dependsOn(configurations.runtimeClasspath)
    archiveFileName.set("AstrBotAdapter-v${project.version}-Folia.jar")
    from({ configurations.runtimeClasspath.get().filter { it.exists() }.map { if (it.isDirectory) it else zipTree(it) } })
}
