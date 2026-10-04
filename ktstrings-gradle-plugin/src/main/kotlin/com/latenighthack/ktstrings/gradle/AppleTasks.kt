package com.latenighthack.ktstrings.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.process.ExecOperations
import java.io.File
import java.util.Properties
import javax.inject.Inject

/** Native toolchain output is deliberately not remote-cacheable across Xcode installations. */
abstract class CompileAppleHelper @Inject constructor(private val exec: ExecOperations) : DefaultTask() {
    @get:InputDirectory @get:PathSensitive(PathSensitivity.RELATIVE) abstract val generatedApple: DirectoryProperty
    @get:Input abstract val sdk: Property<String>
    @get:Input abstract val targetTriple: Property<String>
    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty

    @TaskAction fun compile() {
        requireMac()
        val input = generatedApple.get().asFile
        val metadata = metadata(input)
        val stem = "Ktstrings_${metadata.getProperty("namespace")}"
        val output = outputDirectory.get().asFile.apply { deleteRecursively(); mkdirs() }
        val sdkPath = java.io.ByteArrayOutputStream().also { bytes -> exec.exec { spec -> spec.commandLine("xcrun", "--sdk", sdk.get(), "--show-sdk-path"); spec.standardOutput = bytes } }.toString().trim()
        exec.exec { spec -> spec.commandLine("xcrun", "--sdk", sdk.get(), "clang", "-fobjc-arc", "-target", targetTriple.get(), "-isysroot", sdkPath, "-c", File(input, "native/$stem.m"), "-o", File(output, "$stem.o")) }
        exec.exec { spec -> spec.commandLine("xcrun", "libtool", "-static", "-o", File(output, "lib$stem.a"), File(output, "$stem.o")) }
        File(input, "native/$stem.h").copyTo(File(output, "$stem.h"), overwrite = true)
        File(output, "Ktstrings.def").writeText("language = Objective-C\nheaders = $stem.h\npackage = com.latenighthack.ktstrings.nativeinterop.${metadata.getProperty("namespace")}\nstaticLibraries = lib$stem.a\nlibraryPaths = ${output.absolutePath}\n")
    }
}

abstract class StageAppleFramework @Inject constructor(private val exec: ExecOperations) : DefaultTask() {
    @get:InputDirectory @get:PathSensitive(PathSensitivity.RELATIVE) abstract val framework: DirectoryProperty
    @get:InputDirectory @get:PathSensitive(PathSensitivity.RELATIVE) abstract val generatedApple: DirectoryProperty
    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty

    @TaskAction fun stage() {
        requireMac()
        val source = framework.get().asFile
        val output = outputDirectory.get().asFile.apply { deleteRecursively(); mkdirs() }
        val staged = File(output, source.name)
        exec.exec { spec -> spec.commandLine("ditto", source, staged) }
        // Remove the old signature before touching resources. Consumers sign final embedded products.
        File(staged, "_CodeSignature").deleteRecursively()
        File(staged, "Versions/A/_CodeSignature").deleteRecursively()
        val resources = if (File(staged, "Versions").isDirectory) File(staged, "Versions/Current/Resources") else staged
        resources.mkdirs()
        val generated = generatedApple.get().asFile
        File(generated, "resources").listFiles()?.sortedBy { it.name }?.forEach { locale ->
            exec.exec { spec -> spec.commandLine("ditto", locale, File(resources, locale.name)) }
        }
        val properties = metadata(generated)
        val info = File(resources, "Info.plist")
        if (!info.isFile) throw GradleException("Kotlin framework has no Info.plist: $info")
        exec.exec { spec -> spec.commandLine("plutil", "-replace", "CFBundleIdentifier", "-string", properties.getProperty("bundleIdentifier"), info) }
        exec.exec { spec -> spec.commandLine("plutil", "-replace", "CFBundleDevelopmentRegion", "-string", properties.getProperty("sourceLocale"), info) }
        val locales = properties.getProperty("locales").split(',').joinToString(",", "[", "]") { "\"$it\"" }
        exec.exec { spec -> spec.commandLine("plutil", "-replace", "CFBundleLocalizations", "-json", locales, info) }
    }
}

abstract class AssembleAppleXCFramework @Inject constructor(private val exec: ExecOperations) : DefaultTask() {
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE) abstract val stagedFrameworks: ConfigurableFileCollection
    @get:Input abstract val frameworkName: Property<String>
    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty
    @TaskAction fun assemble() {
        requireMac()
        val output = outputDirectory.get().asFile
        output.deleteRecursively(); output.parentFile.mkdirs()
        val arguments = mutableListOf<Any>("xcodebuild", "-create-xcframework")
        stagedFrameworks.files.sortedBy { it.path }.forEach { root ->
            val framework = if (root.extension == "framework") root else File(root, "${frameworkName.get()}.framework")
            if (!framework.isDirectory) throw GradleException("Missing staged framework: $framework")
            arguments.addAll(listOf("-framework", framework))
        }
        arguments.addAll(listOf("-output", output))
        exec.exec { spec -> spec.commandLine(arguments) }
        verifyAppleFrameworks(output)
    }
}

internal fun verifyAppleFrameworks(root: File) {
    val frameworks = root.walkTopDown().filter { it.isDirectory && it.extension == "framework" }.toList()
    if (frameworks.isEmpty()) throw GradleException("No framework slices in $root")
    frameworks.forEach { framework ->
        val resources = if (File(framework, "Versions").isDirectory) File(framework, "Versions/Current/Resources") else framework
        val locales = resources.listFiles()?.filter { it.extension == "lproj" }.orEmpty()
        if (locales.isEmpty() || locales.any { locale -> locale.listFiles()?.none { it.extension == "strings" } != false || locale.listFiles()?.none { it.extension == "stringsdict" } != false }) {
            throw GradleException("Localization tables missing from XCFramework slice $framework")
        }
    }
}

private fun requireMac() { if (!System.getProperty("os.name").startsWith("Mac")) throw GradleException("ktstrings Apple packaging requires macOS with Xcode") }
private fun metadata(root: File) = Properties().apply { File(root, "catalog.properties").inputStream().use { load(it) } }
