plugins { id("com.latenighthack.ktstrings"); id("com.android.library") }
android { namespace = "fixture.resourceonly"; compileSdk = 35; defaultConfig { minSdk = 23 } }
ktstrings { catalogDirectory.set(layout.projectDirectory.dir("../../../examples/localization")) }
