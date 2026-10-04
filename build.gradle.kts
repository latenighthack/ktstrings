plugins {
    kotlin("multiplatform") version "2.3.10" apply false
    kotlin("jvm") version "2.3.10" apply false
    kotlin("plugin.compose") version "2.3.10" apply false
    id("com.android.library") version "8.13.2" apply false
    id("com.vanniktech.maven.publish") version "0.36.0" apply false
}
allprojects {
    group = providers.gradleProperty("GROUP").get()
    version = providers.gradleProperty("VERSION_NAME").get()
}
subprojects {
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
                licenses { license { name.set("The Apache License, Version 2.0"); url.set("https://www.apache.org/licenses/LICENSE-2.0.txt") } }
                developers { developer { id.set("mproberts"); name.set("Mike Roberts"); url.set("https://github.com/mproberts") } }
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
            repositories { maven { name = "Candidate"; url = uri(rootProject.layout.buildDirectory.dir("candidate-repository")) } }
        }
    }
}
