plugins {
    kotlin("jvm")
    `java-gradle-plugin`
    id("com.vanniktech.maven.publish")
}

kotlin { jvmToolchain(17) }
gradlePlugin {
    plugins {
        create("ktstrings") {
            id = "com.latenighthack.ktstrings"
            implementationClass =
                "com.latenighthack.ktstrings.gradle.KtstringsPlugin"
            displayName = "ktstrings"
            description =
                "Generate typed localization and package application catalogs"
        }
    }
}

val generateReleaseCoordinates by tasks.registering {
    val releaseVersion = project.version.toString()
    inputs.property("releaseVersion", releaseVersion)
    val output = layout.buildDirectory.dir("generated/releaseCoordinates")
    outputs.dir(output)
    doLast {
        val file = output.get().file("com/latenighthack/ktstrings/gradle/ReleaseCoordinates.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            "package com.latenighthack.ktstrings.gradle\ninternal object ReleaseCoordinates { const val VERSION = \"$releaseVersion\"; const val GROUP = \"com.latenighthack.ktstrings\" }\n",
        )
    }
}

kotlin.sourceSets.main { kotlin.srcDir(generateReleaseCoordinates) }
dependencies {
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.10")
    compileOnly("com.android.tools.build:gradle:8.13.2")
    testImplementation(gradleTestKit())
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
}

tasks.test {
    useJUnitPlatform()
    dependsOn(":ktstrings-compiler:installDist", ":ktstrings:jvmJar")
    systemProperty(
        "ktstrings.runtime.jar",
        project(":ktstrings")
            .layout.buildDirectory
            .file("libs/ktstrings-jvm-${project.version}.jar")
            .get()
            .asFile.absolutePath,
    )
    systemProperty(
        "ktstrings.compiler.lib",
        project(":ktstrings-compiler")
            .layout.buildDirectory
            .dir("install/ktstrings-compiler/lib")
            .get()
            .asFile.absolutePath,
    )
}

val integrationToolchain by configurations.creating
dependencies {
    integrationToolchain("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.10")
    integrationToolchain("com.android.tools.build:gradle:8.13.2")
    integrationToolchain("com.google.devtools.ksp:symbol-processing-gradle-plugin:2.3.10")
}

tasks.named<org.gradle.plugin.devel.tasks.PluginUnderTestMetadata>("pluginUnderTestMetadata") { pluginClasspath.from(integrationToolchain) }
