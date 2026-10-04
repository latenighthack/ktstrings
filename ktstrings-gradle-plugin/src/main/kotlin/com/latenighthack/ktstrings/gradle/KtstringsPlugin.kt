package com.latenighthack.ktstrings.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Sync
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
            catalogDirectory.set(extension.catalogDirectory);compilerClasspath.from(compiler)
            kotlinPackage.set(extension.kotlinPackage);kotlinIntegrated.convention(false)
            appleEnabled.set(extension.apple.enabled);androidEnabled.convention(false);reactEnabled.set(extension.react.enabled)
            frameworkName.set(extension.apple.frameworkName);frameworkBundleIdentifier.set(extension.apple.frameworkBundleIdentifier)
            reactPackageName.set(extension.react.packageName);reactPackageVersion.set(extension.react.packageVersion)
            outputDirectory.set(layout.buildDirectory.dir("generated/ktstrings/catalog"))
            androidResourcesDirectory.convention(outputDirectory.dir("android/resources"))
            androidSourcesDirectory.convention(outputDirectory.dir("android/kotlin"))
        } }
        val validation=tasks.register("validateKtstrings",ValidateKtstringsTask::class.java) { task -> task.run {
            catalogDirectory.set(extension.catalogDirectory);compilerClasspath.from(compiler)
            validationMarker.set(layout.buildDirectory.file("outputs/ktstrings/validation.txt"))
        } }
        tasks.named("check").configure { it.dependsOn(validation) }
        tasks.register("reportKtstringsCoverage",CoverageKtstringsTask::class.java) { task -> task.run {
            catalogDirectory.set(extension.catalogDirectory);compilerClasspath.from(compiler)
            reportFile.set(layout.buildDirectory.file("outputs/ktstrings/coverage.txt"))
        } }
        val collectReact=tasks.register("collectKtstringsReact",Sync::class.java) { task -> task.run {
            onlyIf { extension.react.enabled.get() }
            from(generation.flatMap { it.outputDirectory.dir("react") })
            into(layout.buildDirectory.dir("outputs/ktstrings/react"))
        } }
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
        afterEvaluate {
            extension.react.packageVersion.convention(version.toString())
            if(extension.react.enabled.get()) verification.configure {
                it.dependsOn(collectReact)
                it.reactPackage.set(layout.buildDirectory.dir("outputs/ktstrings/react"))
            }
        }
    }
}
