plugins {
    base
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
    kotlin("multiplatform") version "2.3.10" apply false
    kotlin("jvm") version "2.3.10" apply false
    kotlin("plugin.compose") version "2.3.10" apply false
    id("com.android.library") version "8.13.2" apply false
    id("com.vanniktech.maven.publish") version "0.36.0" apply false
}

ktlint {
    version.set("1.8.0")
    kotlinScriptAdditionalPaths {
        include(
            fileTree("integration") {
                include("**/*.kt", "**/*.kts")
                exclude("**/build/**", "**/.gradle/**")
            },
        )
    }
}

tasks.register("lint") {
    group = "verification"
    description = "Checks Kotlin formatting and Android lint across the modules and integration fixtures."
    dependsOn("ktlintCheck", subprojects.map { "${it.path}:ktlintCheck" })
    dependsOn(":ktstrings:lint", ":ktstrings-compose:lint")
}

tasks.named("check") {
    group = "verification"
    description = "Runs lint and all module checks."
    dependsOn("lint", subprojects.map { "${it.path}:check" })
}

allprojects {
    group = providers.gradleProperty("GROUP").get()
    version = providers.gradleProperty("VERSION_NAME").get()
}

subprojects {
    apply(plugin = "base")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    extensions.configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set("1.8.0")
        filter {
            exclude { it.file.invariantSeparatorsPath.contains("/build/") }
        }
    }

    // Central accepts documentation archives; include usable project documentation on every target.
    tasks.withType<org.gradle.jvm.tasks.Jar>().matching { it.name.contains("javadoc", ignoreCase = true) }.configureEach {
        from(rootProject.file("README.md"))
        from(rootProject.file("docs")) { into("docs") }
    }

    plugins.withId("com.vanniktech.maven.publish") {
        extensions.configure<com.vanniktech.maven.publish.MavenPublishBaseExtension> {
            publishToMavenCentral(automaticRelease = false)
            if (providers.gradleProperty("releaseSigning").orNull == "true") signAllPublications()
            pom {
                name.set(project.name)
                description.set("Typed localization catalogs, native resources, and application artifact packaging")
                url.set("https://github.com/latenighthack/ktstrings")
                inceptionYear.set("2026")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("mproberts")
                        name.set("Mike Roberts")
                        url.set("https://github.com/mproberts")
                    }
                }
                scm {
                    url.set("https://github.com/latenighthack/ktstrings")
                    connection.set("scm:git:https://github.com/latenighthack/ktstrings.git")
                    developerConnection.set("scm:git:ssh://git@github.com/latenighthack/ktstrings.git")
                }
            }
        }
    }

    plugins.withId("maven-publish") {
        extensions.configure<PublishingExtension> {
            repositories {
                maven {
                    name = "Candidate"
                    url = uri(rootProject.layout.buildDirectory.dir("candidate-repository"))
                }
            }
        }
    }
}
