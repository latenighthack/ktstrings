plugins {
    kotlin("multiplatform")
    kotlin("plugin.compose")
    id("com.android.library")
    id("com.vanniktech.maven.publish")
}

kotlin {
    androidTarget { publishLibraryVariants("release") }
    sourceSets {
        commonMain.dependencies { api(project(":ktstrings")) }
        androidMain.dependencies {
            api(project.dependencies.platform("androidx.compose:compose-bom:2025.08.00"))
            api("androidx.compose.runtime:runtime")
            api("androidx.compose.ui:ui")
        }
    }
    jvmToolchain(17)
}

android {
    namespace = "com.latenighthack.ktstrings.compose"
    compileSdk = 35
    defaultConfig { minSdk = 23 }
}
