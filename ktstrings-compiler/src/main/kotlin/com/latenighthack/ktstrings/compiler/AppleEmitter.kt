package com.latenighthack.ktstrings.compiler

import java.nio.file.Files
import java.nio.file.Path

/** Foundation is the formatting engine; generated code only selects complete messages. */
class AppleEmitter : CatalogEmitter {
    override val name = "apple"

    override fun emit(catalog: CompiledCatalog, options: GenerationOptions, outputDirectory: Path) {
        val root = outputDirectory.resolve("apple")
        val namespace = catalog.source.namespace
        val table = "Ktstrings_$namespace"
        val locales = linkedMapOf(catalog.source.sourceLocale to catalog.source.messages.mapValues { it.value.body })
        locales.putAll(catalog.translations.mapValues { it.value.messages })
        write(root.resolve("catalog.properties"), "namespace=$namespace\nsourceLocale=${catalog.source.sourceLocale}\nlocales=${locales.keys.joinToString(",")}\nframeworkName=${options.frameworkName}\nbundleIdentifier=${options.frameworkBundleIdentifier}\n")
        locales.forEach { (locale, bodies) ->
            val strings = StringBuilder()
            val plurals = StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">\n<plist version=\"1.0\"><dict>\n")
            bodies.forEach { (id, body) ->
                val message = catalog.source.messages.getValue(id)
                when (body) {
                    is Body.Text -> strings.append("\"${quoted(id)}\" = \"${quoted(format(body.tokens, message))}\";\n")
                    is Body.Plural -> {
                        val slot = message.arguments.indexOfFirst { it.name == body.selector } + 1
                        plurals.append("<key>${xml(id)}</key><dict><key>NSStringLocalizedFormatKey</key><string>%${slot}\$#@quantity@</string>\n")
                        plurals.append("<key>quantity</key><dict><key>NSStringFormatSpecTypeKey</key><string>NSStringPluralRuleType</string><key>NSStringFormatValueTypeKey</key><string>d</string>\n")
                        body.cases.forEach { (category, tokens) -> plurals.append("<key>$category</key><string>${xml(format(tokens, message))}</string>\n") }
                        plurals.append("</dict></dict>\n")
                    }
                }
            }
            plurals.append("</dict></plist>\n")
            write(root.resolve("resources/$locale.lproj/$table.strings"), strings.toString())
            write(root.resolve("resources/$locale.lproj/$table.stringsdict"), plurals.toString())
        }
        val header = StringBuilder("#import <Foundation/Foundation.h>\n#include <stdint.h>\nNS_ASSUME_NONNULL_BEGIN\n")
        val implementation = StringBuilder("#import \"$table.h\"\n")
        implementation.append("""
            static NSBundle * _Nullable ktstrings_bundle(NSString * _Nullable overridePath) {
                NSBundle *candidate = overridePath ? [NSBundle bundleWithPath:overridePath] : nil;
                if (!overridePath && !candidate) candidate = [NSBundle bundleWithIdentifier:@"${quoted(options.frameworkBundleIdentifier)}"];
                if (!overridePath && !candidate) {
                    NSString *path = [[[NSBundle mainBundle] privateFrameworksPath] stringByAppendingPathComponent:@"${quoted(options.frameworkName)}.framework"];
                    candidate = path ? [NSBundle bundleWithPath:path] : nil;
                }
                if (!candidate || ![[candidate bundleIdentifier] isEqualToString:@"${quoted(options.frameworkBundleIdentifier)}"]) return nil;
                return candidate;
            }
            static NSString * _Nullable ktstrings_format(NSString * _Nullable bundlePath, NSString *locale, NSString *key, ...) {
                NSBundle *framework = ktstrings_bundle(bundlePath);
                NSString *path = [framework pathForResource:locale ofType:@"lproj"];
                NSBundle *localized = path ? [NSBundle bundleWithPath:path] : nil;
                if (!localized) return nil;
                NSString *missing = @"__ktstrings_missing_resource__";
                NSString *format = [localized localizedStringForKey:key value:missing table:@"$table"];
                if ([format isEqualToString:missing]) return nil;
                va_list arguments;
                va_start(arguments, key);
                NSString *result = [[NSString alloc] initWithFormat:format locale:[[NSLocale alloc] initWithLocaleIdentifier:locale] arguments:arguments];
                va_end(arguments);
                return result;
            }

        """.trimIndent()).append('\n')
        catalog.source.messages.forEach { (id, message) ->
            val function = "ktstrings_${namespace}_${id.replace('.', '_')}"
            val arguments = message.arguments.mapIndexed { index, arg -> "${if (arg.type == ArgumentType.STRING) "NSString *" else "int32_t "}arg$index" }
            val signature = "NSString * _Nullable $function(NSString * _Nullable bundlePath, NSString *locale${arguments.joinToString(separator = ", ", prefix = if (arguments.isEmpty()) "" else ", ")})"
            header.append(signature).append(";\n")
            implementation.append(signature).append(" {\n    return ktstrings_format(bundlePath, locale, @\"${quoted(id)}\"${message.arguments.indices.joinToString("") { ", arg$it" }});\n}\n")
        }
        header.append("NS_ASSUME_NONNULL_END\n")
        write(root.resolve("native/$table.h"), header.toString())
        write(root.resolve("native/$table.m"), implementation.toString())
        write(root.resolve("native/$table.def"), "language = Objective-C\nheaders = $table.h\npackage = com.latenighthack.ktstrings.nativeinterop.$namespace\n")
        options.kotlinPackage?.let { pkg ->
            val kotlin = StringBuilder("@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)\npackage $pkg\n\nimport com.latenighthack.ktstrings.*\nimport com.latenighthack.ktstrings.nativeinterop.$namespace.*\n\n")
            kotlin.append("class AppleMessagesResolver(private val bundlePath: String? = null) {\n    fun resolve(text: UiText, requestedLocale: String): String {\n        if (text is LiteralText) return text.text\n        require(text is LocalMessage && text.namespace == \"$namespace\") { \"Message belongs to a different catalog\" }\n        val locale = selectLocale(text.id, requestedLocale)\n        val result = when (text.id) {\n")
            catalog.source.messages.forEach { (id, message) ->
                kotlin.append("            \"$id\" -> ktstrings_${namespace}_${id.replace('.', '_')}(bundlePath, locale")
                message.arguments.forEach { argument -> kotlin.append(", (text.arguments.getValue(\"${argument.name}\") as ArgumentValue.${if (argument.type == ArgumentType.STRING) "StringValue" else "IntValue"}).value") }
                kotlin.append(")\n")
            }
            kotlin.append("            else -> error(\"Unknown catalog message\")\n        }\n        return result ?: error(\"ktstrings resources missing or framework identity mismatched: ${options.frameworkName}, locale=\$locale; embed the packaged XCFramework\")\n    }\n\n    private fun selectLocale(id: String, requested: String): String {\n        val available = when (id) {\n")
            catalog.source.messages.keys.forEach { id ->
                val available = locales.filterValues { id in it }.keys
                kotlin.append("            \"$id\" -> setOf(${available.joinToString(", ") { "\"$it\"" }})\n")
            }
            kotlin.append("            else -> emptySet()\n        }\n        return LocaleSelection.select(requested, available.toList(), \"${catalog.source.sourceLocale}\")\n    }\n}\n")
            write(root.resolve("kotlin/${pkg.replace('.', '/')}/AppleMessages.kt"), kotlin.toString())
        }
    }

    private fun format(tokens: List<Token>, message: Message): String = tokens.joinToString("") { token ->
        when (token) {
            is Token.Literal -> token.value.replace("%", "%%")
            is Token.Placeholder -> {
                val slot = message.arguments.indexOfFirst { it.name == token.name }
                "%${slot + 1}\$${if (message.arguments[slot].type == ArgumentType.STRING) "@" else "d"}"
            }
        }
    }
    private fun quoted(value: String) = value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")
    private fun xml(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    private fun write(path: Path, contents: String) { Files.createDirectories(path.parent); Files.writeString(path, contents) }
}
