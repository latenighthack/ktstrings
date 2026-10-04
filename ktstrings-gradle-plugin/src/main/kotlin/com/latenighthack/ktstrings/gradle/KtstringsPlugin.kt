package com.latenighthack.ktstrings.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.GradleException
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

class KtstringsPlugin : Plugin<Project> {
    override fun apply(project: Project) = with(project) {
        pluginManager.apply("base")
        val extension=extensions.create("ktstrings",KtstringsExtension::class.java)
        extension.catalogDirectory.convention(layout.projectDirectory.dir("localization"))
        extension.react.packageVersion.convention(version.toString())
        extension.apple.frameworkBundleIdentifier.convention(extension.kotlinPackage.map { "$it.ktstrings" })
        val compiler=configurations.create("ktstringsCompiler") { it.isCanBeConsumed=false; it.isCanBeResolved=true; it.description="Isolated ktstrings catalog compiler" }
        dependencies.add(compiler.name,"${ReleaseCoordinates.GROUP}:ktstrings-compiler:${ReleaseCoordinates.VERSION}")
        val generation=tasks.register("generateKtstrings",GenerateKtstringsTask::class.java) { task -> task.run {
            catalogDirectory.set(extension.catalogDirectory);compilerClasspath.from(compiler);compilerVersion.set(ReleaseCoordinates.VERSION)
            kotlinPackage.set(extension.kotlinPackage);kotlinIntegrated.convention(false)
            appleEnabled.set(extension.apple.enabled);androidEnabled.convention(false);reactEnabled.set(extension.react.enabled)
            frameworkName.set(extension.apple.frameworkName);frameworkBundleIdentifier.set(extension.apple.frameworkBundleIdentifier)
            reactPackageName.set(extension.react.packageName);reactPackageVersion.set(extension.react.packageVersion)
            outputDirectory.set(layout.buildDirectory.dir("generated/ktstrings/catalog"))
            androidResourcesDirectory.convention(outputDirectory.dir("android/resources"))
            androidSourcesDirectory.convention(outputDirectory.dir("android/kotlin"))
        } }
        val validation=tasks.register("validateKtstrings",ValidateKtstringsTask::class.java) { task -> task.run {
            catalogDirectory.set(extension.catalogDirectory);compilerClasspath.from(compiler);compilerVersion.set(ReleaseCoordinates.VERSION)
            validationMarker.set(layout.buildDirectory.file("outputs/ktstrings/validation.txt"))
        } }
        tasks.named("check").configure { it.dependsOn(validation) }
        tasks.register("reportKtstringsCoverage",CoverageKtstringsTask::class.java) { task -> task.run {
            catalogDirectory.set(extension.catalogDirectory);compilerClasspath.from(compiler);compilerVersion.set(ReleaseCoordinates.VERSION)
            reportFile.set(layout.buildDirectory.file("outputs/ktstrings/coverage.txt"))
        } }
        val collectReact=tasks.register("collectKtstringsReact",Sync::class.java) { task -> task.run {
            from(generation.flatMap { it.outputDirectory.dir("react") })
            into(layout.buildDirectory.dir("outputs/ktstrings/react"))
        } }
        extension.react.packageDirectory.set(layout.dir(collectReact.map { it.destinationDir }))
        val archiveReact=tasks.register("archiveKtstringsReact",Zip::class.java) { task ->
            task.from(extension.react.packageDirectory)
            task.destinationDirectory.set(layout.buildDirectory.dir("outputs/ktstrings/react-distribution"))
            task.archiveFileName.set("ktstrings-react.zip")
            task.isPreserveFileTimestamps=false
            task.isReproducibleFileOrder=true
        }
        extension.react.archiveFile.set(archiveReact.flatMap { it.archiveFile })
        val verification=tasks.register("verifyKtstringsPackaging",VerifyKtstringsPackaging::class.java)
        pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
            generation.configure { it.kotlinIntegrated.set(true) }
            val kotlin=extensions.getByType(KotlinMultiplatformExtension::class.java)
            kotlin.sourceSets.named("commonMain").configure { it.kotlin.srcDir(generation.flatMap { it.outputDirectory.dir("kotlin") }) }
            dependencies.add("commonMainApi","${ReleaseCoordinates.GROUP}:ktstrings:${ReleaseCoordinates.VERSION}")
            kotlin.sourceSets.matching { it.name == "jsMain" }.configureEach { it.kotlin.srcDir(generation.flatMap { it.outputDirectory.dir("js") }) }
            tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class.java).configureEach { it.dependsOn(generation) }
            configureApple(project,extension,generation)
        }
        pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
            generation.configure { it.kotlinIntegrated.set(true) }
            pluginManager.apply("java-library")
            extensions.getByType(KotlinJvmProjectExtension::class.java).sourceSets.named("main").configure { it.kotlin.srcDir(generation.flatMap { it.outputDirectory.dir("kotlin") }) }
            dependencies.add("api","${ReleaseCoordinates.GROUP}:ktstrings:${ReleaseCoordinates.VERSION}")
        }
        pluginManager.withPlugin("org.jetbrains.kotlin.android") { generation.configure { it.kotlinIntegrated.set(true) } }
        pluginManager.withPlugin("com.android.library") { configureAndroid(project,extension,generation) }
        pluginManager.withPlugin("com.android.application") { configureAndroid(project,extension,generation) }
        pluginManager.withPlugin("com.google.devtools.ksp") {
            tasks.matching { it.name.startsWith("ksp") }.configureEach { it.dependsOn(generation) }
        }
        pluginManager.withPlugin("maven-publish") {
            afterEvaluate {
                val publications=extensions.getByType(PublishingExtension::class.java).publications
                if(extension.react.publicationName.isPresent) {
                    val publication=publications.maybeCreate(extension.react.publicationName.get(),MavenPublication::class.java)
                    publication.artifact(extension.react.archiveFile) { artifact -> artifact.classifier="ktstrings-react"; artifact.extension="zip"; artifact.builtBy(archiveReact) }
                }
                if(extension.apple.publicationName.isPresent) {
                    val publication=publications.maybeCreate(extension.apple.publicationName.get(),MavenPublication::class.java)
                    publication.artifact(extension.apple.releaseArchive) { artifact -> artifact.classifier="ktstrings-xcframework"; artifact.extension="zip" }
                }
            }
        }
        afterEvaluate {
            extension.react.packageVersion.convention(version.toString())
            collectReact.configure { it.enabled=extension.react.enabled.get() }
            archiveReact.configure { it.enabled=extension.react.enabled.get() }
            if (extension.react.publicationName.isPresent && !extension.react.enabled.get()) throw GradleException("ktstrings.react.publicationName requires react.enabled")
            if (extension.apple.publicationName.isPresent && !extension.apple.enabled.get()) throw GradleException("ktstrings.apple.publicationName requires apple.enabled")
            if ((extension.react.publicationName.isPresent || extension.apple.publicationName.isPresent) && !pluginManager.hasPlugin("maven-publish")) throw GradleException("Configured ktstrings distribution publications require the maven-publish plugin")
            if(extension.react.enabled.get()) verification.configure {
                it.dependsOn(collectReact)
                it.reactPackage.set(layout.buildDirectory.dir("outputs/ktstrings/react"))
            }
        }
    }
}
