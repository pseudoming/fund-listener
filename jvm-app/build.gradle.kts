plugins {
    kotlin("jvm") version "2.1.20"
    application
}

application {
    mainClass.set("com.fundlistener.ApplicationKt")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":shared"))
}

kotlin {
    jvmToolchain(23)
}
