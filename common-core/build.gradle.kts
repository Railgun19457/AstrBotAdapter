plugins {
    `java-library`
}

dependencies {
    api(project(":common-protocol"))
    api(project(":common-transport"))
    api(project(":platform-spi"))
    implementation(project(":common-util"))
    implementation("org.bouncycastle:bcprov-jdk18on:1.80")
    implementation("org.yaml:snakeyaml:2.2")
}
