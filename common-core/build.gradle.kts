plugins {
    `java-library`
}

dependencies {
    api(project(":common-protocol"))
    api(project(":common-transport"))
    api(project(":platform-spi"))
    implementation(project(":common-util"))
    implementation("org.yaml:snakeyaml:2.2")
}
