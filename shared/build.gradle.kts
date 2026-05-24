plugins {
    kotlin("jvm") version "2.1.20"
    kotlin("plugin.serialization") version "2.1.20"
    `java-library`
}

group = "com.fundlistener"
version = "0.1.0"

repositories {
    mavenCentral()
}

val ktorVersion = "3.1.3"
val koinVersion = "4.1.0"
val logbackVersion = "1.5.18"

dependencies {
    // Ktor Server
    api("io.ktor:ktor-server-core:$ktorVersion")
    api("io.ktor:ktor-server-netty:$ktorVersion")
    api("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    api("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    api("io.ktor:ktor-server-cors:$ktorVersion")
    api("io.ktor:ktor-server-status-pages:$ktorVersion")
    api("io.ktor:ktor-server-call-logging:$ktorVersion")

    // Ktor Client
    api("io.ktor:ktor-client-okhttp:$ktorVersion")
    api("io.ktor:ktor-client-content-negotiation:$ktorVersion")

    // Koin DI
    api("io.insert-koin:koin-ktor:$koinVersion")
    api("io.insert-koin:koin-logger-slf4j:$koinVersion")

    // Logging
    api("ch.qos.logback:logback-classic:$logbackVersion")

    // SQLite (JVM development, replaced by Room on Android)
    api("org.xerial:sqlite-jdbc:3.45.3.0")

    // HTML Parsing
    api("org.jsoup:jsoup:1.17.2")

    // Local OCR
    api("io.github.mymonstercat:rapidocr:0.0.7")
    api("io.github.mymonstercat:rapidocr-onnx-platform:0.0.7")
    api("io.github.mymonstercat:rapidocr-onnx-linux-x86_64:1.2.2")

    // Testing
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("io.insert-koin:koin-test:$koinVersion")
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(23)
}

tasks.test {
    useJUnitPlatform()
}
