package com.latenighthack.ktstrings.compiler

import java.nio.file.Path

enum class ArgumentType { STRING, INT }

data class Argument(
    val name: String,
    val type: ArgumentType,
)

sealed interface Token {
    data class Literal(
        val value: String,
    ) : Token

    data class Placeholder(
        val name: String,
    ) : Token
}

sealed interface Body {
    data class Text(
        val tokens: List<Token>,
    ) : Body

    data class Plural(
        val selector: String,
        val cases: LinkedHashMap<String, List<Token>>,
    ) : Body
}

data class Message(
    val id: String,
    val description: String?,
    val arguments: List<Argument>,
    val body: Body,
)

data class Catalog(
    val namespace: String,
    val sourceLocale: String,
    val messages: LinkedHashMap<String, Message>,
)

data class LocaleCatalog(
    val locale: String,
    val messages: Map<String, Body>,
)

data class CompiledCatalog(
    val source: Catalog,
    val translations: Map<String, LocaleCatalog>,
    val cldrVersion: String,
) {
    val locales: List<String> get() = listOf(source.sourceLocale) + translations.keys.sorted()

    fun bodies(locale: String): Map<String, Body> =
        if (locale ==
            source.sourceLocale
        ) {
            source.messages.mapValues { it.value.body }
        } else {
            translations[locale]?.messages.orEmpty()
        }

    fun availableLocales(id: String): List<String> = locales.filter { id in bodies(it) }
}

data class GenerationOptions(
    val kotlinPackage: String? = null,
    val androidPackage: String? = null,
    val frameworkName: String = "Shared",
    val frameworkBundleIdentifier: String = "com.example.Shared",
    val reactPackageName: String? = null,
    val reactPackageVersion: String = "1.0.0",
    val platforms: Set<String> = setOf("kotlin", "android", "apple", "react"),
)

interface CatalogEmitter {
    val name: String

    fun emit(
        catalog: CompiledCatalog,
        options: GenerationOptions,
        outputDirectory: Path,
    )
}

class CatalogException(
    val code: String,
    message: String,
) : IllegalArgumentException("$code: $message")

object Names {
    private val kotlinKeywords =
        setOf(
            "as",
            "break",
            "class",
            "continue",
            "do",
            "else",
            "false",
            "for",
            "fun",
            "if",
            "in",
            "interface",
            "is",
            "null",
            "object",
            "package",
            "return",
            "super",
            "this",
            "throw",
            "true",
            "try",
            "typealias",
            "typeof",
            "val",
            "var",
            "when",
            "while",
        )

    fun validateKotlinPackage(value: String) {
        if (!Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)*").matches(value) ||
            value.split('.').any { it in kotlinKeywords }
        ) {
            throw CatalogException("KOTLIN_PACKAGE", "Invalid Kotlin package '$value'")
        }
    }

    fun pascal(id: String): String = id.split('.', '_').joinToString("") { it.replaceFirstChar(Char::uppercaseChar) }

    fun camel(id: String): String = pascal(id).replaceFirstChar(Char::lowercaseChar)

    fun resource(
        namespace: String,
        id: String,
    ): String = "ktstrings_${namespace}_${id.replace('.', '_').lowercase()}"
}
