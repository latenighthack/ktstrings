package com.latenighthack.ktstrings.gradle

import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import com.android.build.api.variant.Variant
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal fun configureAndroid(
    project: Project,
    extension: KtstringsExtension,
    generation: TaskProvider<GenerateKtstringsTask>,
) {
    generation.configure { it.androidEnabled.set(true) }
    project.tasks.withType(VerifyKtstringsPackaging::class.java).configureEach {
        it.androidCatalogMetadata.set(
            generation.flatMap { task ->
                task.outputDirectory.file("metadata/catalog.json")
            },
        )
    }

    fun variant(variant: Variant) {
        val resources =
            project.tasks.register(
                "stageKtstrings${variant.name.replaceFirstChar(Char::uppercaseChar)}Resources",
                StageAndroidResources::class.java,
            ) {
                it.sourceDirectory.set(generation.flatMap { task -> task.outputDirectory.dir("android/resources") })
                it.outputDirectory.convention(project.layout.buildDirectory.dir("generated/ktstrings/android/${variant.name}/resources"))
            }
        variant.sources.res?.addGeneratedSourceDirectory(resources) { it.outputDirectory }
    }

    project.extensions.findByType(LibraryAndroidComponentsExtension::class.java)?.let { components ->
        components.finalizeDsl { dsl -> generation.configure { it.androidPackage.set(dsl.namespace) } }
        components.onVariants(components.selector().all()) { androidVariant ->
            variant(androidVariant)
            if (androidVariant.buildType ==
                "release"
            ) {
                project.tasks.withType(VerifyKtstringsPackaging::class.java).configureEach {
                    it.androidPackages.from(androidVariant.artifacts.get(com.android.build.api.artifact.SingleArtifact.AAR))
                }
            }
        }
    }

    project.extensions.findByType(ApplicationAndroidComponentsExtension::class.java)?.let { components ->
        components.finalizeDsl { dsl -> generation.configure { it.androidPackage.set(dsl.namespace) } }
        components.onVariants(components.selector().all()) { androidVariant ->
            variant(androidVariant)
            if (androidVariant.buildType ==
                "release"
            ) {
                project.tasks.withType(VerifyKtstringsPackaging::class.java).configureEach {
                    it.androidPackages.from(
                        androidVariant.artifacts.get(com.android.build.api.artifact.SingleArtifact.APK),
                        androidVariant.artifacts.get(com.android.build.api.artifact.SingleArtifact.BUNDLE),
                    )
                }
            }
        }
    }

    project.pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
        val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
        kotlin.sourceSets.matching { it.name == "androidMain" }.configureEach {
            it.kotlin.srcDir(
                generation.flatMap { task ->
                    task.outputDirectory.dir("android/kotlin")
                },
            )
        }

        project.afterEvaluate {
            if (extension.android.compose.get()) {
                kotlin.sourceSets.matching { it.name == "androidMain" }.configureEach {
                    it.kotlin.srcDir(
                        generation.flatMap { task ->
                            task.outputDirectory.dir("android/compose")
                        },
                    )
                }

                project.dependencies.add(
                    "androidMainImplementation",
                    "${ReleaseCoordinates.GROUP}:ktstrings-compose:${ReleaseCoordinates.VERSION}",
                )
            }
        }
    }

    project.pluginManager.withPlugin("org.jetbrains.kotlin.android") {
        val kotlin = project.extensions.getByType(org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension::class.java)
        kotlin.sourceSets.named("main").configure {
            it.kotlin.srcDir(
                generation.flatMap { task ->
                    task.outputDirectory.dir("kotlin")
                },
            )
            it.kotlin.srcDir(
                generation.flatMap { task ->
                    task.outputDirectory.dir("android/kotlin")
                },
            )
        }

        project.dependencies.add("api", "${ReleaseCoordinates.GROUP}:ktstrings:${ReleaseCoordinates.VERSION}")
        project.afterEvaluate {
            if (extension.android.compose.get()) {
                project.extensions
                    .findByType(
                        com.android.build.api.dsl.LibraryExtension::class.java,
                    )?.sourceSets
                    ?.getByName("main")
                    ?.java
                    ?.srcDir(
                        generation.flatMap {
                            it.outputDirectory.dir("android/compose")
                        },
                    )
                project.extensions
                    .findByType(
                        com.android.build.api.dsl.ApplicationExtension::class.java,
                    )?.sourceSets
                    ?.getByName("main")
                    ?.java
                    ?.srcDir(
                        generation.flatMap {
                            it.outputDirectory.dir("android/compose")
                        },
                    )
                project.dependencies.add("implementation", "${ReleaseCoordinates.GROUP}:ktstrings-compose:${ReleaseCoordinates.VERSION}")
            }
        }
    }
}

@CacheableTask
abstract class StageAndroidResources : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceDirectory: DirectoryProperty

    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun stage() {
        val source = sourceDirectory.get().asFile.toPath()
        val output = outputDirectory.get().asFile.toPath()
        if (Files.exists(output)) Files.walk(output).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
        Files.walk(source).use { paths ->
            paths.forEach { path ->
                val target = output.resolve(source.relativize(path))
                if (Files.isDirectory(path)) {
                    Files.createDirectories(target)
                } else {
                    Files.createDirectories(target.parent)
                    Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }
    }
}

internal fun verifyAndroidArtifact(
    artifact: java.io.File,
    metadata: java.io.File,
) {
    val catalog =
        groovy.json.JsonSlurper().parse(metadata) as? Map<*, *>
            ?: throw org.gradle.api.GradleException("Invalid generated ktstrings metadata")
    val namespace = catalog["namespace"] as? String ?: throw org.gradle.api.GradleException("Missing generated catalog namespace")
    val availability =
        catalog["availability"] as? Map<*, *> ?: throw org.gradle.api.GradleException("Missing generated catalog availability")
    val expected = availability.keys.map { id -> "ktstrings_${namespace}_${(id as String).replace('.', '_').lowercase()}" }
    if (artifact.isDirectory) {
        val apks = artifact.walkTopDown().filter { it.isFile && it.extension == "apk" }.toList()
        if (apks.isEmpty()) throw org.gradle.api.GradleException("Android distribution contains no APK: $artifact")

        apks.forEach { verifyAndroidArtifact(it, metadata) }
        return
    }

    java.util.zip.ZipFile(artifact).use { archive ->
        val entries =
            archive
                .entries()
                .asSequence()
                .map { it.name }
                .toList()
        when (artifact.extension) {
            "aar" -> {
                if ("AndroidManifest.xml" !in entries) throw org.gradle.api.GradleException("Invalid Android library archive: $artifact")

                val sourceResources = entries.filter { it.startsWith("res/values/") && it.endsWith(".xml") }
                val xml =
                    sourceResources.joinToString("\n") { name ->
                        archive.getInputStream(archive.getEntry(name)).bufferedReader().use {
                            it.readText()
                        }
                    }
                val declared =
                    Regex(
                        "<(?:string|plurals)\\b[^>]*\\bname\\s*=\\s*[\"']([^\"']+)[\"']",
                    ).findAll(xml).map { it.groupValues[1] }.toSet()
                val missing = expected - declared
                if (missing.isNotEmpty()) {
                    throw org.gradle.api.GradleException(
                        "AAR is missing source catalog native definitions $missing: $artifact",
                    )
                }
            }

            "apk", "aab" -> {
                val manifest = if (artifact.extension == "apk") "AndroidManifest.xml" else "base/manifest/AndroidManifest.xml"
                if (manifest !in entries) throw org.gradle.api.GradleException("Invalid Android application archive: $artifact")

                if (expected.isNotEmpty()) {
                    val table = if (artifact.extension == "apk") "resources.arsc" else "base/resources.pb"
                    if (table !in
                        entries
                    ) {
                        throw org.gradle.api.GradleException("Android distribution has no compiled native resource table: $artifact")
                    }

                    val data = archive.getInputStream(archive.getEntry(table)).use { it.readBytes() }
                    val encoded = listOf(data.toString(Charsets.ISO_8859_1), data.toString(Charsets.UTF_16LE))
                    val missing =
                        expected.filter { name ->
                            encoded.none { Regex("${Regex.escape(name)}(?![a-z0-9_])").containsMatchIn(it) }
                        }
                    if (missing.isNotEmpty()) {
                        throw org.gradle.api.GradleException(
                            "Android distribution is missing compiled catalog entries $missing: $artifact",
                        )
                    }
                }
            }

            else -> {
                throw org.gradle.api.GradleException("Unsupported Android distribution artifact: $artifact")
            }
        }
    }
}
