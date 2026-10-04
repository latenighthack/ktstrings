plugins {
    kotlin("multiplatform") version "2.3.10"
    id("com.latenighthack.ktstrings")
}
version = "1.0.0"
kotlin {
    val staticFramework = providers.gradleProperty("staticFramework").map(String::toBoolean).getOrElse(true)
    iosArm64 { binaries.framework { baseName = "Shared"; isStatic = staticFramework } }
    iosSimulatorArm64 { binaries.framework { baseName = "Shared"; isStatic = staticFramework } }
    macosArm64 { binaries.framework { baseName = "Shared"; isStatic = staticFramework } }
}
ktstrings {
    kotlinPackage.set("com.example.appleproof")
    apple {
        enabled.set(true)
        frameworkName.set("Shared")
        frameworkBundleIdentifier.set("com.example.Shared")
    }
}
