import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.github.peteroomen.keyboard"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.peteroomen.keyboard"
        minSdk = 31 // window blur, dynamic colour
        targetSdk = 36
        // CI numbers each build, so every release is newer than the last (local builds are 1)
        val build = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
        versionCode = (build ?: 0) + 1
        versionName = "0.1.${build ?: 0}"
    }

    // CI signs with the shared debug key it's given, so updates install over the app on the phone
    signingConfigs {
        getByName("debug") {
            System.getenv("DEBUG_KEYSTORE_FILE")?.let { storeFile = file(it) }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.2")
    implementation("androidx.core:core-ktx:1.16.0")

    testImplementation("junit:junit:4.13.2")
}
