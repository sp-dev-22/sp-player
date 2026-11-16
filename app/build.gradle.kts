import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.sp.player"
    compileSdk = 34
    buildToolsVersion = "35.0.0"

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
// Paths are captured here (configuration time) so the task stays
// configuration-cache compatible.
val keystoreFile = rootDir.resolve("debug.keystore")
val keystoreB64File = rootDir.resolve("debug.keystore.b64")
tasks.register("restoreDebugKeystore") {
    doLast {
        if (!keystoreFile.exists() && keystoreB64File.exists()) {
            val bytes = java.util.Base64.getDecoder().decode(keystoreB64File.readText().trim())
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
