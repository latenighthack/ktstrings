package com.latenighthack.ktstrings.gradle

import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import com.android.build.api.variant.Variant
import org.gradle.api.Project
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.*
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

internal fun configureAndroid(project: Project, extension: KtstringsExtension, generation: TaskProvider<GenerateKtstringsTask>) {
    generation.configure { it.androidEnabled.set(true) }
    fun variant(variant: Variant) {
        val resources = project.tasks.register("stageKtstrings${variant.name.replaceFirstChar(Char::uppercaseChar)}Resources", StageAndroidResources::class.java) {
            it.sourceDirectory.set(generation.flatMap { task -> task.outputDirectory.dir("android/resources") })
            it.outputDirectory.convention(project.layout.buildDirectory.dir("generated/ktstrings/android/${variant.name}/resources"))
        }
        variant.sources.res?.addGeneratedSourceDirectory(resources) { it.outputDirectory }
    }
    project.extensions.findByType(LibraryAndroidComponentsExtension::class.java)?.let { components ->
        components.finalizeDsl { dsl -> generation.configure { it.androidPackage.set(dsl.namespace) } }
        components.onVariants(components.selector().all()) { androidVariant ->
            variant(androidVariant)
            if (androidVariant.buildType == "release") project.tasks.withType(VerifyKtstringsPackaging::class.java).configureEach { it.androidPackages.from(androidVariant.artifacts.get(com.android.build.api.artifact.SingleArtifact.AAR)) }
        }
    }
    project.extensions.findByType(ApplicationAndroidComponentsExtension::class.java)?.let { components ->
        components.finalizeDsl { dsl -> generation.configure { it.androidPackage.set(dsl.namespace) } }
        components.onVariants(components.selector().all()) { androidVariant ->
            variant(androidVariant)
            if (androidVariant.buildType == "release") project.tasks.withType(VerifyKtstringsPackaging::class.java).configureEach { it.androidPackages.from(androidVariant.artifacts.get(com.android.build.api.artifact.SingleArtifact.APK), androidVariant.artifacts.get(com.android.build.api.artifact.SingleArtifact.BUNDLE)) }
        }
    }
    project.pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
        val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
        kotlin.sourceSets.matching { it.name == "androidMain" }.configureEach { it.kotlin.srcDir(generation.flatMap { task -> task.outputDirectory.dir("android/kotlin") }) }
        project.afterEvaluate {
            if (extension.android.compose.get()) {
                kotlin.sourceSets.matching { it.name == "androidMain" }.configureEach { it.kotlin.srcDir(generation.flatMap { task -> task.outputDirectory.dir("android/compose") }) }
                project.dependencies.add("androidMainImplementation", "${ReleaseCoordinates.GROUP}:ktstrings-compose:${ReleaseCoordinates.VERSION}")
            }
        }
    }
    project.pluginManager.withPlugin("org.jetbrains.kotlin.android") {
        val kotlin = project.extensions.getByType(org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension::class.java)
        kotlin.sourceSets.named("main").configure { it.kotlin.srcDir(generation.flatMap { task -> task.outputDirectory.dir("kotlin") }); it.kotlin.srcDir(generation.flatMap { task -> task.outputDirectory.dir("android/kotlin") }) }
        project.dependencies.add("api", "${ReleaseCoordinates.GROUP}:ktstrings:${ReleaseCoordinates.VERSION}")
        project.afterEvaluate {
            if (extension.android.compose.get()) {
                project.extensions.findByType(com.android.build.api.dsl.LibraryExtension::class.java)?.sourceSets?.getByName("main")?.java?.srcDir(generation.flatMap { it.outputDirectory.dir("android/compose") })
                project.extensions.findByType(com.android.build.api.dsl.ApplicationExtension::class.java)?.sourceSets?.getByName("main")?.java?.srcDir(generation.flatMap { it.outputDirectory.dir("android/compose") })
                project.dependencies.add("implementation", "${ReleaseCoordinates.GROUP}:ktstrings-compose:${ReleaseCoordinates.VERSION}")
            }
        }
    }
}

@CacheableTask
abstract class StageAndroidResources : DefaultTask() {
    @get:InputDirectory @get:PathSensitive(PathSensitivity.RELATIVE) abstract val sourceDirectory: DirectoryProperty
    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty
    @TaskAction fun stage() {
        val source = sourceDirectory.get().asFile.toPath()
        val output = outputDirectory.get().asFile.toPath()
        if (Files.exists(output)) Files.walk(output).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
        Files.walk(source).use { paths -> paths.forEach { path ->
            val target = output.resolve(source.relativize(path))
            if (Files.isDirectory(path)) Files.createDirectories(target)
            else { Files.createDirectories(target.parent); Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING) }
        } }
    }
}

internal fun verifyAndroidArtifact(artifact: java.io.File) {
    if (artifact.isDirectory) {
        val apks = artifact.walkTopDown().filter { it.isFile && it.extension == "apk" }.toList()
        if (apks.isEmpty()) throw org.gradle.api.GradleException("Android distribution contains no APK: $artifact")
        apks.forEach(::verifyAndroidArtifact)
        return
    }
    java.util.zip.ZipFile(artifact).use { archive ->
        val entries = archive.entries().asSequence().map { it.name }.toList()
        when (artifact.extension) {
            "aar" -> {
                val resources = entries.filter { it.startsWith("res/values") && it.endsWith(".xml") }
                if (resources.none { name -> archive.getInputStream(archive.getEntry(name)).bufferedReader().use { it.readText() }.contains("ktstrings_") })
                    throw org.gradle.api.GradleException("AAR has no generated native ktstrings resource definitions: $artifact")
            }
            "apk", "aab" -> {
                val table = if (artifact.extension == "apk") "resources.arsc" else "base/resources.pb"
                if (table !in entries) throw org.gradle.api.GradleException("Android distribution has no compiled native resource table: $artifact")
                val data = archive.getInputStream(archive.getEntry(table)).use { it.readBytes() }
                if (!data.toString(Charsets.ISO_8859_1).contains("ktstrings_") && !data.toString(Charsets.UTF_16LE).contains("ktstrings_"))
                    throw org.gradle.api.GradleException("Android distribution has no compiled ktstrings resource entries: $artifact")
            }
            else -> throw org.gradle.api.GradleException("Unsupported Android distribution artifact: $artifact")
        }
    }
}
