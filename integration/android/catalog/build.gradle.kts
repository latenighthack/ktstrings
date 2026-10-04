plugins {
    kotlin("multiplatform")
    id("com.android.library")
    kotlin("plugin.compose")
    id("com.latenighthack.ktstrings")
}
kotlin { androidTarget(); sourceSets { commonMain.dependencies { } } }
android { namespace="fixture.catalog"; compileSdk=35; defaultConfig { minSdk=23 } }
ktstrings {
    catalogDirectory.set(layout.projectDirectory.dir("../../../examples/localization"))
    kotlinPackage.set("fixture.localization")
    android { compose.set(true) }
}
