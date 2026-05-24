plugins {
    kotlin("jvm") version "2.1.20" apply false
    kotlin("plugin.serialization") version "2.1.20" apply false
}

group = "com.fundlistener"
version = "0.1.0"

allprojects {
    repositories {
        mavenCentral()
    }
}
