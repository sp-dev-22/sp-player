import java.util.Base64
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    // NOTE: no kotlin-android plugin — AGP 9 has built-in Kotlin support
    // (enabled by default); applying org.jetbrains.kotlin.android alongside
    // it fails with "Cannot add extension with name 'kotlin'".
}

android {
    namespace = "com.sp.player"
    // compileSdk 35: androidx.core:core-ktx 1.16.0 requires compiling
    // against API 35+. targetSdk stays 34 (no new runtime behavior).
    compileSdk = 35
    // Build Tools 36.0.0 is the minimum AGP 9.1.1 accepts.
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.sp.player"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        getByName("debug") {
            storeFile = rootDir.resolve("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
}

// Stable signing key: the debug keystore is committed as debug.keystore.b64
// (text) and decoded before the build, so every APK installs as an update
// over the last one. The binary keystore is git-ignored and never committed.
// NOTE: this task captures script-level paths, which the configuration
// cache cannot serialize — so the configuration cache stays OFF for this
// project (see gradle.properties). Do NOT "fix" this with file providers:
// `provider.get().asFile` does not compile in this Kotlin DSL script
// (Gradle 9.3.1: `.get()` is an unresolved reference there).
val keystoreFile = rootDir.resolve("debug.keystore")
val keystoreB64File = rootDir.resolve("debug.keystore.b64")
tasks.register("restoreDebugKeystore") {
    doLast {
        if (!keystoreFile.exists() && keystoreB64File.exists()) {
            // NOTE: fully-qualified java.util.Base64 does NOT compile in a
            // Kotlin DSL script — `java` is shadowed by the script's implicit
            // `java` extension, so `util` is "unresolved". Import it instead.
            val bytes = Base64.getDecoder().decode(keystoreB64File.readText().trim())
            keystoreFile.writeBytes(bytes)
            logger.lifecycle("Restored debug.keystore from debug.keystore.b64")
        }
    }
}
tasks.named("preBuild") { dependsOn("restoreDebugKeystore") }

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.session)
}
