plugins {
    `java-library`
}

dependencies {
    implementation(project(":common-core"))
    implementation(project(":platform-spi"))
    compileOnly("io.papermc.paper:paper-api:1.20.4-R0.1-SNAPSHOT")
}

tasks.jar {
    dependsOn(configurations.runtimeClasspath)
    archiveFileName.set("AstrBotAdapter-v${project.version}-Bukkit.jar")
    from({ configurations.runtimeClasspath.get().filter { it.exists() }.map { if (it.isDirectory) it else zipTree(it) } })
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}
