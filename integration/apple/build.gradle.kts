plugins {
    kotlin("multiplatform") version "2.3.10"
    id("com.latenighthack.ktstrings")
    `maven-publish`
}
group = "com.example.appleproof"
version = "1.0.0"
kotlin {
    val staticFramework = providers.gradleProperty("staticFramework").map(String::toBoolean).getOrElse(true)
    iosArm64 { binaries.framework { baseName = "Shared"; isStatic = staticFramework } }
    iosSimulatorArm64 { binaries.framework { baseName = "Shared"; isStatic = staticFramework } }
    macosArm64 { binaries.framework { baseName = "Shared"; isStatic = staticFramework } }
    if (providers.gradleProperty("fatFramework").map(String::toBoolean).getOrElse(false)) {
        iosX64 { binaries.framework { baseName = "Shared"; isStatic = staticFramework } }
        macosX64 { binaries.framework { baseName = "Shared"; isStatic = staticFramework } }
    }
}
ktstrings {
    kotlinPackage.set("com.example.appleproof")
    apple {
        enabled.set(true)
        frameworkName.set("Shared")
        frameworkBundleIdentifier.set("com.example.Shared")
        if (providers.gradleProperty("publishDistribution").orNull == "true") publicationName.set("kotlinMultiplatform")
    }
}
publishing {
    repositories { maven { name = "Fixture"; url = uri(layout.buildDirectory.dir("published-fixture")) } }
}
