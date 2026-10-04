plugins { id("com.android.application"); kotlin("android") }
android {
    compileOptions { sourceCompatibility=JavaVersion.VERSION_17; targetCompatibility=JavaVersion.VERSION_17 }
    namespace="fixture.app"; compileSdk=35
    defaultConfig { applicationId="fixture.ktstrings"; minSdk=23; targetSdk=35; versionCode=1; versionName="1"; testInstrumentationRunner="androidx.test.runner.AndroidJUnitRunner" }
    buildTypes { getByName("release") { isMinifyEnabled=true; isShrinkResources=true; signingConfig=signingConfigs.getByName("debug"); proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt")) } }
    testBuildType="release"
}
dependencies { implementation(project(":catalog")); androidTestImplementation("androidx.test:runner:1.6.2"); androidTestImplementation("androidx.test.ext:junit:1.2.1") }

kotlin { jvmToolchain(17) }
