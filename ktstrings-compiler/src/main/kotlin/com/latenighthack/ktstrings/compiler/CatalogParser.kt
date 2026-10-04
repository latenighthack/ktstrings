package com.latenighthack.ktstrings.compiler

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.StreamReadFeature
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.ibm.icu.text.PluralRules
import com.ibm.icu.util.LocaleData
import com.ibm.icu.util.ULocale
import java.nio.file.Files
import java.nio.file.Path

class CatalogParser {
    private val mapper = ObjectMapper(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build()).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
    fun load(directory: Path): CompiledCatalog {
        val root = read(directory.resolve("catalog.json"))
        fields(root, setOf("schemaVersion", "namespace", "sourceLocale", "messages"), "catalog")
        version(root)
        val namespace = text(root, "namespace")
        if (!Regex("[a-z][a-z0-9_]*").matches(namespace) || namespace in reserved) fail("NAMESPACE", "Invalid namespace '$namespace'")
        val sourceLocale = locale(text(root, "sourceLocale"))
        val messages = linkedMapOf<String, Message>()
        objectNode(root.get("messages"), "messages").fields().forEach { (id, value) ->
            if (!Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)*").matches(id)) fail("MESSAGE_ID", "Invalid message ID '$id'")
            fields(value, setOf("description", "arguments", "body"), "message '$id'")
            val description = value.get("description")?.let { if (!it.isTextual) fail("TYPE", "description for '$id' must be a string"); it.textValue() }
            val arguments = mutableListOf<Argument>()
            value.get("arguments")?.let { objectNode(it, "arguments for '$id'").fields().forEach { (name, type) ->
                if (!Regex("[A-Za-z][A-Za-z0-9_]*").matches(name) || name in reserved || name in setOf("arguments", "namespace", "id")) fail("ARGUMENT_NAME", "Invalid argument '$name' in '$id'")
                val argumentType = when (type.takeIf { it.isTextual }?.textValue()) { "string" -> ArgumentType.STRING; "int" -> ArgumentType.INT; else -> fail("ARGUMENT_TYPE", "Unsupported type for '$id.$name'") }
                arguments += Argument(name, argumentType)
            } }
            val body = body(value.get("body"), arguments, sourceLocale, id)
            messages[id] = Message(id, description, arguments, body)
        }
        collisions(namespace, messages.keys)
        val translations = sortedMapOf<String, LocaleCatalog>()
        val translationDirectory = directory.resolve("locales")
        if (Files.exists(translationDirectory)) Files.list(translationDirectory).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".json") }.sorted().forEach { file ->
                val translation = read(file)
                fields(translation, setOf("schemaVersion", "locale", "messages"), "translation '$file'")
                version(translation)
                val translatedLocale = locale(text(translation, "locale"))
                if (translatedLocale == sourceLocale || translatedLocale in translations) fail("DUPLICATE_LOCALE", "Duplicate locale '$translatedLocale'")
                if (locale(file.fileName.toString().removeSuffix(".json")) != translatedLocale) fail("LOCALE_FILENAME", "Locale '$translatedLocale' does not match '$file'")
                val bodies = linkedMapOf<String, Body>()
                objectNode(translation.get("messages"), "translated messages").fields().forEach { (id, translatedBody) ->
                    val message = messages[id] ?: fail("UNKNOWN_ID", "Translation '$translatedLocale' contains unknown message '$id'")
                    val parsed = body(translatedBody, message.arguments, translatedLocale, id)
                    if ((parsed is Body.Plural) != (message.body is Body.Plural) || parsed is Body.Plural && (message.body as Body.Plural).selector != parsed.selector)
                        fail("BODY_CONTRACT", "Translation '$translatedLocale.$id' changes the source body contract")
                    bodies[id] = parsed
                }
                translations[translatedLocale] = LocaleCatalog(translatedLocale, bodies)
            }
        }
        return CompiledCatalog(Catalog(namespace, sourceLocale, messages), translations, LocaleData.getCLDRVersion().toString())
    }
    private fun read(path: Path): JsonNode = try { mapper.readTree(path.toFile()) ?: fail("JSON", "Empty JSON in '$path'") } catch (e: CatalogException) { throw e } catch (e: Exception) { fail("JSON", "Cannot parse '$path': ${e.message}") }
    private fun version(node: JsonNode) { if (!node.path("schemaVersion").isIntegralNumber || !node.path("schemaVersion").canConvertToInt() || node.path("schemaVersion").intValue() != 1) fail("SCHEMA_VERSION", "Only schemaVersion 1 is supported") }
    private fun body(node: JsonNode?, arguments: List<Argument>, locale: String, id: String): Body {
        val value = objectNode(node, "body for '$id'")
        val names = arguments.map { it.name }.toSet()
        if (value.has("text")) {
            fields(value, setOf("text"), "body '$id'")
            return Body.Text(tokens(text(value, "text"), names, id))
        }
        fields(value, setOf("plural", "cases"), "body '$id'")
        val selector = text(value, "plural")
        if (arguments.find { it.name == selector }?.type != ArgumentType.INT) fail("PLURAL_SELECTOR", "Selector '$selector' in '$id' must be declared int")
        if (selector != "count" && "count" in names) fail("RESERVED_COUNT", "Plural '$id' with selector '$selector' cannot have a separate count argument")
        val cases = linkedMapOf<String, List<Token>>()
        objectNode(value.get("cases"), "plural cases '$id'").fields().forEach { (category, branch) ->
            if (!branch.isTextual) fail("TYPE", "Plural branch '$id.$category' must be text")
            cases[category] = tokens(branch.textValue(), names, id)
        }
        val required = PluralRules.forLocale(ULocale.forLanguageTag(locale), PluralRules.PluralType.CARDINAL).keywords
        val missing = required - cases.keys
        val unsupported = cases.keys - required
        if (missing.isNotEmpty() || unsupported.isNotEmpty()) fail("PLURAL_CATEGORIES", "'$locale.$id': missing $missing; unsupported $unsupported (CLDR ${LocaleData.getCLDRVersion()})")
        return Body.Plural(selector, cases)
    }
    fun tokens(value: String, arguments: Set<String>, id: String = "message"): List<Token> {
        val result = mutableListOf<Token>()
        val literal = StringBuilder()
        fun flush() { if (literal.isNotEmpty()) { result += Token.Literal(literal.toString()); literal.setLength(0) } }
        var index = 0
        while (index < value.length) {
            val ch = value[index]
            if ((ch == '{' || ch == '}') && index + 1 < value.length && value[index + 1] == ch) { literal.append(ch); index += 2; continue }
            if (ch == '{') {
                val end = value.indexOf('}', index + 1)
                if (end < 0) fail("PLACEHOLDER", "Unclosed placeholder in '$id'")
                val name = value.substring(index + 1, end)
                if (name !in arguments) fail("UNKNOWN_PLACEHOLDER", "Unknown placeholder '$name' in '$id'")
                flush(); result += Token.Placeholder(name); index = end + 1
            } else {
                if (ch == '}') fail("PLACEHOLDER", "Unescaped closing brace in '$id'")
                literal.append(ch); index++
            }
        }
        flush(); return result
    }
    private fun collisions(namespace: String, ids: Set<String>) {
        val conversions = linkedMapOf<String, (String) -> String>(
            "Kotlin" to Names::camel, "Swift" to Names::camel, "TypeScript" to Names::camel,
            "Objective-C" to { Names.pascal(it).lowercase() }, "Android" to { Names.resource(namespace, it).lowercase() }
        )
        for ((platform, convert) in conversions) {
            val generated = mutableMapOf<String, String>()
            for (id in ids) {
                val name = convert(id)
                if (Names.camel(id) in reserved || (Names.pascal(id) + "Message") in generatedTypeNames) fail("RESERVED_NAME", "Message '$id' generates reserved identifier '$name' for $platform")
                val previous = generated.put(name, id)
                if (previous != null) fail("NAME_COLLISION", "'$previous' and '$id' generate '$name' for $platform")
            }
        }
    }
    fun locale(value: String): String {
        val normalized = value.replace('_', '-')
        val parsed = try { ULocale.Builder().setLanguageTag(normalized).build() } catch (_: Exception) { fail("LOCALE", "Invalid locale '$value'") }
        if (parsed.language.isEmpty() || parsed.language == "und" || parsed.language !in languages || parsed.extensionKeys.isNotEmpty()) fail("LOCALE", "Unsupported locale '$value'")
        return parsed.toLanguageTag()
    }
    private fun fields(node: JsonNode, allowed: Set<String>, location: String) {
        objectNode(node, location)
        val extra = node.fieldNames().asSequence().toSet() - allowed
        if (extra.isNotEmpty()) fail("UNKNOWN_FIELD", "$location contains unknown fields $extra")
    }
    private fun text(node: JsonNode, key: String): String = node.get(key)?.takeIf { it.isTextual }?.textValue() ?: fail("TYPE", "'$key' must be a string")
    private fun objectNode(node: JsonNode?, location: String): JsonNode = node?.takeIf { it.isObject } ?: fail("TYPE", "$location must be an object")
    private fun fail(code: String, message: String): Nothing = throw CatalogException(code, message)
    companion object {
        private val languages = ULocale.getAvailableLocales().map { it.language }.toSet()
        private val generatedTypeNames = setOf("Messages", "CatalogMessage", "CatalogDecoder", "UiText", "LocalMessage", "LiteralText", "ArgumentValue", "ServerMessage", "ServerText")
        private val reserved = setOf("__proto__", "prototype", "constructor", "class", "object", "interface", "fun", "val", "var", "when", "is", "in", "as", "this", "super", "null", "true", "false", "return", "throw", "try", "catch", "finally", "break", "continue", "for", "while", "do", "if", "else", "new", "delete", "typeof", "void", "switch", "case", "default", "function", "let", "const", "export", "import", "extends", "implements", "package", "private", "public", "protected", "internal", "static", "enum", "await", "yield", "async", "repeat", "deinit", "init", "protocol", "struct", "extension", "associatedtype", "typealias", "nil", "self", "Self", "get", "set")
    }
}
