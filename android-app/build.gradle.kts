plugins {
    id("com.android.application") version "8.9.0"
    kotlin("android") version "2.1.20"
    kotlin("plugin.serialization") version "2.1.20"
    id("com.google.devtools.ksp") version "2.1.20-2.0.1"
}

android {
    namespace = "com.fundlistener"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.fundlistener"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    // APK signing — debug uses default keystore, release needs keystore.properties
    signingConfigs {
        create("release") {
            // User-provided keystore; falls back to debug if not configured
            val keystoreProps = rootProject.file("keystore.properties")
            if (keystoreProps.exists()) {
                val props = java.util.Properties()
                props.load(keystoreProps.inputStream())
                storeFile = file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            } else {
                // Fallback to debug signing for development builds
                storeFile = signingConfigs.getByName("debug").storeFile
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            // Debug builds use default signing
        }
    }

    packaging {
        resources {
            // Netty native libs that conflict on Android
            excludes += listOf(
                "META-INF/native/**",
                "META-INF/native-image/**",
                "META-INF/INDEX.LIST",
                "META-INF/io.netty.versions.properties"
            )
        }
    }
}

dependencies {
    // Shared module (exclude JVM-only deps)
    implementation(project(":shared")) {
        exclude(group = "ch.qos.logback", module = "logback-classic")
        exclude(group = "org.xerial", module = "sqlite-jdbc")
    }

    // Android-specific Ktor engine
    implementation("io.ktor:ktor-server-netty:3.1.3")

    // Android logging (replaces logback)
    implementation("org.slf4j:slf4j-android:2.0.17")

    // Koin Android
    implementation("io.insert-koin:koin-android:4.1.0")

    // AndroidX
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.webkit:webkit:1.12.1")

    // Room
    val roomVersion = "2.7.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // ML Kit — Chinese text recognition (offline)
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")
}
