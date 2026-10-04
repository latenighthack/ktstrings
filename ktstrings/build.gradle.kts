plugins {
    kotlin("multiplatform")
    id("com.android.library")
    id("com.vanniktech.maven.publish")
}

kotlin {
    jvm()
    androidTarget { publishLibraryVariants("release") }
    js(IR) {
        browser()
        nodejs()
    }
    iosArm64()
    iosSimulatorArm64()
    iosX64()
    macosArm64()
    macosX64()
    sourceSets { commonTest.dependencies { implementation(kotlin("test")) } }
    jvmToolchain(17)
}

android {
    namespace = "com.latenighthack.ktstrings"
    compileSdk = 35
    defaultConfig { minSdk = 23 }
}
