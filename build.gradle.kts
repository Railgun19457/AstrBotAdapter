import java.security.MessageDigest

allprojects {
    group = "io.github.railgun19457"
    version = "3.0.0-SNAPSHOT"
}

subprojects {
    apply(plugin = "java-library")

    dependencies {
        add("testImplementation", "org.junit.jupiter:junit-jupiter:5.11.4")
        add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher")
    }

    extensions.configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(17)
    }

    tasks.withType<Jar>().configureEach {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}

val releaseArtifacts = listOf(
    "platform-bukkit/build/libs/AstrBotAdapter-v${project.version}-Bukkit.jar",
    "platform-folia/build/libs/AstrBotAdapter-v${project.version}-Folia.jar",
    "platform-velocity/build/libs/AstrBotAdapter-v${project.version}-Velocity.jar",
    "platform-fabric/build/libs/AstrBotAdapter-v${project.version}-Fabric.jar",
)

tasks.register("checksums") {
    // Minecraft 26.x Fabric builds are unobfuscated, so the final artifact is produced by jar.
    dependsOn(":platform-bukkit:jar", ":platform-folia:jar", ":platform-velocity:jar", ":platform-fabric:jar")
    val outputFile = layout.buildDirectory.file("distributions/SHA256SUMS.txt")
    outputs.file(outputFile)
    doLast {
        val lines = releaseArtifacts.map { path -> file(path) }
            .filter { it.exists() }
            .map { artifact -> sha256(artifact) + "  " + artifact.name }
        val target = outputFile.get().asFile
        target.parentFile.mkdirs()
        target.writeText(lines.joinToString(System.lineSeparator()) + System.lineSeparator())
    }
}

tasks.register("buildAll") {
    dependsOn(subprojects.map { "${it.path}:build" })
    dependsOn("checksums")
}

fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(8192)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) {
                break
            }
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
