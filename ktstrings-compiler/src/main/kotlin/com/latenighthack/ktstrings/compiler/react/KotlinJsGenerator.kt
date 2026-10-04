package com.latenighthack.ktstrings.compiler.react

import com.latenighthack.ktstrings.compiler.CatalogEmitter
import com.latenighthack.ktstrings.compiler.CompiledCatalog
import com.latenighthack.ktstrings.compiler.GenerationOptions
import java.nio.file.Files
import java.nio.file.Path

/** Encodes shared state into the exact discriminated union exported by the React package. */
class KotlinJsGenerator : CatalogEmitter {
    override val name = "kotlin-js"

    override fun emit(
        catalog: CompiledCatalog,
        options: GenerationOptions,
        outputDirectory: Path,
    ) {
        if (options.reactPackageName == null) return

        val pkg = options.kotlinPackage ?: return
        val directory = outputDirectory.resolve("js").resolve(pkg.replace('.', '/'))
        Files.createDirectories(directory)
        Files.writeString(
            directory.resolve("KtstringsJs.kt"),
            """
@file:Suppress("UnsafeCastFromDynamic")
package $pkg

import com.latenighthack.ktstrings.UiText
import com.latenighthack.ktstrings.LiteralText
import com.latenighthack.ktstrings.LocalMessage
import com.latenighthack.ktstrings.ArgumentValue

fun encodeKtstringsText(value: UiText): dynamic {
    val result = js("Object.create(null)")
    when (value) {
        is LiteralText -> { result.kind = "literal"; result.text = value.text }
        is LocalMessage -> {
            require(value is CatalogMessage && value.namespace == ${quote(catalog.source.namespace)}) { "Unknown ktstrings namespace" }
            result.kind = "message"
            result.namespace = value.namespace
            result.id = value.id
            val arguments = js("Object.create(null)")
            value.arguments.forEach { (name, argument) ->
                arguments[name] = when (argument) {
                    is ArgumentValue.StringValue -> argument.value
                    is ArgumentValue.IntValue -> argument.value
                    else -> error("Unsupported ktstrings argument value")
                }
            }
            result.arguments = arguments
        }
    }
    return result
}
            """.trimIndent() + "\n",
        )
    }
}
