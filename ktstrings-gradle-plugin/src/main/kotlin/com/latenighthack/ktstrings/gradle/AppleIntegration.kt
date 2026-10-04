package com.latenighthack.ktstrings.gradle

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.Framework
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.tasks.FatFrameworkTask

/** Connect only opted-in, existing frameworks; never apply native tooling for the consumer. */
internal fun configureApple(
    project: Project,
    extension: KtstringsExtension,
    generation: TaskProvider<GenerateKtstringsTask>,
) {
    project.pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
        project.afterEvaluate {
            if (!extension.apple.enabled.get()) return@afterEvaluate

            requireAppleHost(System.getProperty("os.name"))
            val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
            val targets = kotlin.targets.withType(KotlinNativeTarget::class.java).filter { it.konanTarget.family.isAppleFamily }
            val allFrameworks = targets.flatMap { it.binaries.withType(Framework::class.java).toList() }
            val names = allFrameworks.map { it.baseName }.distinct()
            if (!extension.apple.frameworkName.isPresent) {
                extension.apple.frameworkName.set(inferAppleFrameworkName(names))
            }

            val frameworkName = extension.apple.frameworkName.get()
            val frameworks = allFrameworks.filter { it.baseName == frameworkName }
            if (frameworks.isEmpty()) throw GradleException("No Kotlin Apple frameworks named $frameworkName")

            val existingBundleIds = frameworks.mapNotNull { it.binaryOptions["bundleId"] }.distinct()
            if (existingBundleIds.size >
                1
            ) {
                throw GradleException("Selected Kotlin frameworks have conflicting bundle identifiers: $existingBundleIds")
            }

            existingBundleIds.singleOrNull()?.let { extension.apple.frameworkBundleIdentifier.convention(it) }
            val bundleId = extension.apple.frameworkBundleIdentifier.get()
            frameworks.forEach { framework ->
                if (framework.binaryOptions["bundleId"]?.let { it != bundleId } ==
                    true
                ) {
                    throw GradleException("ktstrings frameworkBundleIdentifier conflicts with Kotlin framework bundleId")
                }

                framework.binaryOption("bundleId", bundleId)
            }

            val generatedApple = generation.flatMap { it.outputDirectory.dir("apple") }
            targets.filter { target -> frameworks.any { it.target == target } }.forEach { target ->
                val platform = applePlatform(target)
                val suffix = target.name.replaceFirstChar(Char::uppercaseChar)
                val helper =
                    project.tasks.register("compileKtstrings${suffix}Helper", CompileAppleHelper::class.java) { task ->
                        task.generatedApple.set(generatedApple)
                        task.sdk.set(platform.first)
                        task.targetTriple.set(platform.second)
                        task.toolchainVersion.set(
                            project.providers
                                .exec { it.commandLine("xcodebuild", "-version") }
                                .standardOutput.asText,
                        )
                        task.outputDirectory.set(project.layout.buildDirectory.dir("generated/ktstrings/native/${target.name}"))
                    }
                val compilation = target.compilations.getByName("main")
                compilation.defaultSourceSet.kotlin.srcDir(generation.map { it.outputDirectory.dir("apple/kotlin") })
                val interop =
                    compilation.cinterops.create("ktstrings") { settings ->
                        settings.definitionFile.set(helper.flatMap { it.outputDirectory.file("Ktstrings.def") })
                        settings.includeDirs(helper.flatMap { it.outputDirectory })
                    }
                project.tasks.named(interop.interopProcessingTaskName).configure { task -> task.dependsOn(helper) }
                frameworks.filter { it.target == target }.forEach { framework ->
                    framework.export("${ReleaseCoordinates.GROUP}:ktstrings:${ReleaseCoordinates.VERSION}")
                }
            }

            frameworks.groupBy { it.buildType }.forEach { (buildType, binaries) ->
                val buildName = buildType.name.lowercase().replaceFirstChar(Char::uppercaseChar)
                val assemble =
                    project.tasks.register("assembleKtstrings${buildName}XCFramework", AssembleAppleXCFramework::class.java) { task ->
                        task.group = "ktstrings"
                        task.description = "Assemble resource-bearing $buildName $frameworkName XCFramework"
                        task.frameworkName.set(frameworkName)
                        task.outputDirectory.set(
                            project.layout.buildDirectory.dir(
                                "outputs/ktstrings/apple/${buildType.name.lowercase()}/$frameworkName.xcframework",
                            ),
                        )
                    }
                if (buildType.name == "DEBUG") {
                    extension.apple.debugXCFramework.set(assemble.flatMap { it.outputDirectory })
                } else {
                    extension.apple.releaseXCFramework.set(assemble.flatMap { it.outputDirectory })
                    val archive =
                        project.tasks.register("archiveKtstringsReleaseXCFramework", ArchiveAppleXCFramework::class.java) { task ->
                            task.group = "ktstrings"
                            task.description = "Archive the resource-bearing release XCFramework for publication"
                            task.xcframework.set(assemble.flatMap { it.outputDirectory })
                            task.archiveFile.set(
                                project.layout.buildDirectory.file("outputs/ktstrings/apple/release/$frameworkName.xcframework.zip"),
                            )
                        }
                    extension.apple.releaseArchive.set(archive.flatMap { it.archiveFile })
                }

                binaries.groupBy { applePlatform(it.target).first }.forEach { (sdk, slices) ->
                    val suffix = sdk.replaceFirstChar(Char::uppercaseChar) + buildName
                    val stage =
                        project.tasks.register("stageKtstrings${suffix}Framework", StageAppleFramework::class.java) { task ->
                            task.generatedApple.set(generatedApple)
                            task.outputDirectory.set(project.layout.buildDirectory.dir("generated/ktstrings/staged/$suffix"))
                        }
                    if (slices.size == 1) {
                        val framework = slices.single()
                        stage.configure { task ->
                            task.dependsOn(framework.linkTaskProvider)
                            task.framework.set(project.layout.dir(framework.linkTaskProvider.flatMap { it.outputFile }))
                        }
                    } else {
                        val fat =
                            project.tasks.register("assembleKtstrings${suffix}FatFramework", FatFrameworkTask::class.java) { task ->
                                task.baseName = frameworkName
                                task.destinationDirProperty.set(project.layout.buildDirectory.dir("generated/ktstrings/fat/$suffix"))
                                task.from(slices)
                            }
                        stage.configure { task ->
                            task.framework.set(fat.flatMap { it.destinationDirProperty.dir("$frameworkName.framework") })
                            task.dependsOn(fat)
                        }
                    }

                    assemble.configure { task -> task.stagedFrameworks.from(stage.flatMap { it.outputDirectory }) }
                }

                project.tasks.withType(VerifyKtstringsPackaging::class.java).configureEach {
                    it.applePackages.from(
                        assemble.flatMap { task ->
                            task.outputDirectory
                        },
                    )
                }
            }
        }
    }
}

internal fun inferAppleFrameworkName(names: List<String>): String {
    val distinct = names.distinct()
    if (distinct.size != 1) throw GradleException("ktstrings Apple framework is ambiguous: $distinct; set apple.frameworkName")

    return distinct.single()
}

private fun applePlatform(target: KotlinNativeTarget): Pair<String, String> =
    when (target.konanTarget.name) {
        "ios_arm64" -> "iphoneos" to "arm64-apple-ios13.0"
        "ios_simulator_arm64" -> "iphonesimulator" to "arm64-apple-ios13.0-simulator"
        "ios_x64" -> "iphonesimulator" to "x86_64-apple-ios13.0-simulator"
        "macos_arm64" -> "macosx" to "arm64-apple-macos11.0"
        "macos_x64" -> "macosx" to "x86_64-apple-macos10.15"
        "tvos_arm64" -> "appletvos" to "arm64-apple-tvos13.0"
        "tvos_simulator_arm64" -> "appletvsimulator" to "arm64-apple-tvos13.0-simulator"
        "tvos_x64" -> "appletvsimulator" to "x86_64-apple-tvos13.0-simulator"
        else -> throw GradleException("Unsupported ktstrings Apple target: ${target.konanTarget.name}; supported: iOS, macOS, tvOS")
    }
