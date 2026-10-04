pluginManagement {
    includeBuild("../..")
    repositories { mavenCentral(); google(); gradlePluginPortal() }
}
dependencyResolutionManagement { repositories { mavenCentral(); google() } }
rootProject.name = "ktstrings-apple-consumer"
// Explicit development substitutions; isolated publication acceptance uses a file Maven repository.
includeBuild("../..") {
    dependencySubstitution {
        substitute(module("com.latenighthack.ktstrings:ktstrings")).using(project(":ktstrings"))
        substitute(module("com.latenighthack.ktstrings:ktstrings-compiler")).using(project(":ktstrings-compiler"))
    }
}
