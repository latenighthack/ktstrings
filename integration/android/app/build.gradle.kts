plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.compose")
}

android {
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    namespace = "fixture.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "fixture.ktstrings"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1"
        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"
        testProguardFiles("test-proguard-rules.pro")
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "instrumented-release.pro")
        }
    }
    testBuildType = "release"
}

dependencies {
    implementation(project(":catalog"))
    implementation(platform("androidx.compose:compose-bom:2025.08.00"))
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.10.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.08.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}

kotlin { jvmToolchain(17) }
