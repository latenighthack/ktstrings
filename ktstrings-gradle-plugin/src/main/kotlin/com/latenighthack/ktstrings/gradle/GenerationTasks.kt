package com.latenighthack.ktstrings.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import javax.inject.Inject

@CacheableTask
abstract class GenerateKtstringsTask @Inject constructor(private val exec: ExecOperations) : DefaultTask() {
    @get:InputDirectory @get:PathSensitive(PathSensitivity.RELATIVE) abstract val catalogDirectory: DirectoryProperty
    @get:Input abstract val compilerVersion: Property<String>
    @get:Classpath abstract val compilerClasspath: ConfigurableFileCollection
    @get:Input @get:Optional abstract val kotlinPackage: Property<String>
    @get:Input @get:Optional abstract val androidPackage: Property<String>
    @get:Input abstract val kotlinIntegrated: Property<Boolean>
    @get:Input abstract val appleEnabled: Property<Boolean>
    @get:Input abstract val androidEnabled: Property<Boolean>
    @get:Input abstract val reactEnabled: Property<Boolean>
    @get:Input @get:Optional abstract val frameworkName: Property<String>
    @get:Input @get:Optional abstract val frameworkBundleIdentifier: Property<String>
    @get:Input @get:Optional abstract val reactPackageName: Property<String>
    @get:Input abstract val reactPackageVersion: Property<String>
    @get:Internal abstract val androidResourcesDirectory: DirectoryProperty
    @get:Internal abstract val androidSourcesDirectory: DirectoryProperty
    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty
    @TaskAction fun generate() {
        if (kotlinIntegrated.get() && !kotlinPackage.isPresent) throw GradleException("ktstrings.kotlinPackage is required when Kotlin generation is integrated")
        if (reactEnabled.get() && (!reactPackageName.isPresent || reactPackageVersion.get() == "unspecified")) throw GradleException("ktstrings.react requires a packageName and packageVersion (or an assigned project version)")
        val arguments = mutableListOf("generate", "--catalog", catalogDirectory.get().asFile.absolutePath,"--output",outputDirectory.get().asFile.absolutePath)
        fun option(name: String, value: Property<String>) { if(value.isPresent) arguments.addAll(listOf(name,value.get())) }
        option("--kotlin-package",kotlinPackage)
        option("--android-package",androidPackage)
        option("--framework-name",frameworkName)
        option("--framework-bundle-id",frameworkBundleIdentifier)
        if (reactEnabled.get()) { option("--react-package",reactPackageName); option("--react-version",reactPackageVersion) }
        val platforms = mutableListOf<String>()
        if(kotlinPackage.isPresent) platforms += "kotlin"
        if(androidEnabled.get()) platforms += "android"
        if(appleEnabled.get()) platforms += "apple"
        if(reactEnabled.get()) platforms += "react"
        arguments.addAll(listOf("--platforms",platforms.joinToString(",")))
        exec.javaexec { spec -> spec.run { classpath(compilerClasspath); mainClass.set("com.latenighthack.ktstrings.compiler.MainKt"); args(arguments) } }
    }
}

@CacheableTask
abstract class ValidateKtstringsTask @Inject constructor(private val exec: ExecOperations) : DefaultTask() {
    @get:InputDirectory @get:PathSensitive(PathSensitivity.RELATIVE) abstract val catalogDirectory: DirectoryProperty
    @get:Input abstract val compilerVersion: Property<String>
    @get:Classpath abstract val compilerClasspath: ConfigurableFileCollection
    @get:OutputFile abstract val validationMarker: RegularFileProperty
    @TaskAction fun validate() {
        exec.javaexec { spec -> spec.run { classpath(compilerClasspath); mainClass.set("com.latenighthack.ktstrings.compiler.MainKt"); args("validate","--catalog",catalogDirectory.get().asFile.absolutePath) } }
        validationMarker.get().asFile.apply { parentFile.mkdirs(); writeText("valid\n") }
    }
}

@CacheableTask
abstract class CoverageKtstringsTask @Inject constructor(private val exec: ExecOperations) : DefaultTask() {
    @get:InputDirectory @get:PathSensitive(PathSensitivity.RELATIVE) abstract val catalogDirectory: DirectoryProperty
    @get:Input abstract val compilerVersion: Property<String>
    @get:Classpath abstract val compilerClasspath: ConfigurableFileCollection
    @get:OutputFile abstract val reportFile: RegularFileProperty
    @TaskAction fun report() {
        val bytes=ByteArrayOutputStream()
        exec.javaexec { spec -> spec.run { classpath(compilerClasspath); mainClass.set("com.latenighthack.ktstrings.compiler.MainKt"); args("coverage","--catalog",catalogDirectory.get().asFile.absolutePath); standardOutput=bytes } }
        reportFile.get().asFile.apply { parentFile.mkdirs(); writeBytes(bytes.toByteArray()) }
        logger.lifecycle(bytes.toString())
    }
}

abstract class VerifyKtstringsPackaging : DefaultTask() {
    @get:InputDirectory @get:Optional @get:PathSensitive(PathSensitivity.RELATIVE) abstract val reactPackage: DirectoryProperty
    @get:InputFile @get:Optional @get:PathSensitive(PathSensitivity.RELATIVE) abstract val androidCatalogMetadata: RegularFileProperty
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE) abstract val androidPackages: ConfigurableFileCollection
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE) abstract val applePackages: ConfigurableFileCollection
    @TaskAction fun verify() {
        if(reactPackage.isPresent) {
            val root=reactPackage.get().asFile
            listOf("package.json","index.js","index.d.ts","react.js","react.d.ts","metadata/catalog.json").forEach { if(!root.resolve(it).isFile) throw GradleException("Incomplete ktstrings React distribution: $it") }
            if(root.resolve("locales").listFiles()?.none { it.extension == "json" } != false) throw GradleException("React package has no locale JSON")
        }
        androidPackages.files.forEach { verifyAndroidArtifact(it,androidCatalogMetadata.get().asFile) }
        applePackages.files.forEach(::verifyAppleFrameworks)
    }
}
