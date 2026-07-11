plugins {
    `java-library`
}

dependencies {
    api(project(":common-protocol"))
    api("org.java-websocket:Java-WebSocket:1.5.7")
}
