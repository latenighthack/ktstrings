package com.latenighthack.ktstrings.compiler

import com.fasterxml.jackson.databind.ObjectMapper
import com.latenighthack.ktstrings.compiler.react.ReactEmitter
import com.latenighthack.ktstrings.compiler.react.KotlinJsGenerator
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

object Compiler {
    fun generate(catalog: CompiledCatalog, options: GenerationOptions, output: Path) {
        options.kotlinPackage?.let(Names::validateKotlinPackage)
        val absolute = output.toAbsolutePath()
        Files.createDirectories(absolute.parent)
        val staging = Files.createTempDirectory(absolute.parent, ".ktstrings-")
        try {
            val emitters = listOf(KotlinEmitter(), AndroidEmitter(), AppleEmitter(), ReactEmitter())
            for (emitter in emitters) if (emitter.name in options.platforms) emitter.emit(catalog, options, staging)
            if ("react" in options.platforms && options.kotlinPackage != null) KotlinJsGenerator().emit(catalog, options, staging)
            val metadata = linkedMapOf<String, Any>(
                "schemaVersion" to 1, "namespace" to catalog.source.namespace, "sourceLocale" to catalog.source.sourceLocale,
                "cldrVersion" to catalog.cldrVersion, "locales" to catalog.locales,
                "availability" to catalog.source.messages.keys.associateWith(catalog::availableLocales)
            )
            Files.createDirectories(staging.resolve("metadata"))
            Files.writeString(staging.resolve("metadata/catalog.json"), ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(metadata) + "\n")
            val backup = absolute.resolveSibling(".${absolute.fileName}.previous")
            if (Files.exists(backup)) deleteTree(backup)
            if (Files.exists(absolute)) Files.move(absolute, backup)
            try {
                try { Files.move(staging, absolute, StandardCopyOption.ATOMIC_MOVE) }
                catch (_: java.nio.file.AtomicMoveNotSupportedException) { Files.move(staging, absolute) }
                if (Files.exists(backup)) deleteTree(backup)
            } catch (failure: Exception) {
                if (Files.exists(backup) && !Files.exists(absolute)) Files.move(backup, absolute)
                throw failure
            }
        } finally { if (Files.exists(staging)) deleteTree(staging) }
    }
    private fun deleteTree(path: Path) { Files.walk(path).use { stream -> stream.sorted(Comparator.reverseOrder()).forEach(Files::delete) } }
}
fun main(arguments: Array<String>) {
    try {
        val args = arguments.toMutableList()
        val command = if (args.firstOrNull()?.startsWith("--") == false) args.removeAt(0) else "generate"
        val values = linkedMapOf<String, String>()
        while (args.isNotEmpty()) {
            val key = args.removeAt(0)
            if (!key.startsWith("--") || args.isEmpty()) throw CatalogException("CLI", "Expected --option value; received '$key'")
            if (values.put(key, args.removeAt(0)) != null) throw CatalogException("CLI", "Duplicate option '$key'")
        }
        val allowed = setOf("--catalog", "--output", "--kotlin-package", "--android-package", "--react-package", "--react-version", "--framework-name", "--framework-bundle-id", "--platforms")
        if ((values.keys - allowed).isNotEmpty()) throw CatalogException("CLI", "Unknown options ${values.keys - allowed}")
        values["--platforms"]?.split(',')?.filter { it.isNotEmpty() && it !in setOf("kotlin", "android", "apple", "react") }?.takeIf { it.isNotEmpty() }?.let { throw CatalogException("CLI", "Unsupported platforms $it") }
        val catalog = CatalogParser().load(Path.of(values["--catalog"] ?: "localization"))
        when (command) {
            "validate" -> println("Validated ${catalog.source.messages.size} messages; CLDR ${catalog.cldrVersion}")
            "coverage" -> catalog.locales.forEach { locale -> println("$locale: ${catalog.bodies(locale).size}/${catalog.source.messages.size}") }
            "generate" -> Compiler.generate(catalog, GenerationOptions(
                kotlinPackage = values["--kotlin-package"], androidPackage = values["--android-package"],
                frameworkName = values["--framework-name"] ?: "Shared", frameworkBundleIdentifier = values["--framework-bundle-id"] ?: "com.example.Shared",
                reactPackageName = values["--react-package"], reactPackageVersion = values["--react-version"] ?: "1.0.0",
                platforms = values["--platforms"]?.split(',')?.toSet() ?: setOf("kotlin", "android", "apple", "react")
            ), Path.of(values["--output"] ?: "build/generated/ktstrings"))
            else -> throw CatalogException("CLI", "Unknown command '$command'")
        }
    } catch (failure: CatalogException) {
        System.err.println(failure.message)
        kotlin.system.exitProcess(1)
    }
}
