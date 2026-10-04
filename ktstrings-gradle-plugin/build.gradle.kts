plugins { kotlin("jvm"); `java-gradle-plugin`; id("com.vanniktech.maven.publish") }
kotlin { jvmToolchain(17) }
gradlePlugin {
    plugins { create("ktstrings") { id = "com.latenighthack.ktstrings"; implementationClass = "com.latenighthack.ktstrings.gradle.KtstringsPlugin"; displayName = "ktstrings"; description = "Generate typed localization and package application catalogs" } }
}
val generateReleaseCoordinates by tasks.registering {
    val releaseVersion = project.version.toString()
    inputs.property("releaseVersion", releaseVersion)
    val output = layout.buildDirectory.dir("generated/releaseCoordinates")
    outputs.dir(output)
    doLast {
        val file = output.get().file("com/latenighthack/ktstrings/gradle/ReleaseCoordinates.kt").asFile
        file.parentFile.mkdirs()
        file.writeText("package com.latenighthack.ktstrings.gradle\ninternal object ReleaseCoordinates { const val VERSION = \"$releaseVersion\"; const val GROUP = \"com.latenighthack.ktstrings\" }\n")
    }
}
kotlin.sourceSets.main { kotlin.srcDir(generateReleaseCoordinates) }
dependencies { testImplementation(gradleTestKit()); testImplementation(kotlin("test")); testImplementation("org.junit.jupiter:junit-jupiter:5.13.4") }
tasks.test { useJUnitPlatform() }
