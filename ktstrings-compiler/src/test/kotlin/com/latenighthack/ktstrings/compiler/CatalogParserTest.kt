package com.latenighthack.ktstrings.compiler

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

class CatalogParserTest {
    @TempDir lateinit var directory: Path
    private fun catalog(messages: String = "\"welcome\":{\"arguments\":{\"name\":\"string\"},\"body\":{\"text\":\"Hi {name}\"}}", sourceLocale: String = "en"): String = """{"schemaVersion":1,"namespace":"app","sourceLocale":"$sourceLocale","messages":{$messages}}"""
    private fun parse(json: String): CompiledCatalog { Files.writeString(directory.resolve("catalog.json"), json); return CatalogParser().load(directory) }
    private fun fails(code: String, json: String) { assertEquals(code, assertFailsWith<CatalogException> { parse(json) }.code) }
    @Test fun strictJsonAndSchema() {
        fails("JSON", """{"schemaVersion":1,"schemaVersion":1,"namespace":"app","sourceLocale":"en","messages":{}}""")
        fails("JSON", catalog() + " {}")
        fails("JSON", catalog("\"a\":{\"body\":{\"text\":\"a\"}},\"a\":{\"body\":{\"text\":\"b\"}}"))
        fails("SCHEMA_VERSION", catalog().replace("\"schemaVersion\":1", "\"schemaVersion\":2"))
        fails("SCHEMA_VERSION", catalog().replace("\"schemaVersion\":1", "\"schemaVersion\":4294967297"))
        fails("UNKNOWN_FIELD", catalog().replace("\"namespace\":", "\"surprise\":0,\"namespace\":"))
        fails("TYPE", catalog().replace("\"text\":\"Hi {name}\"", "\"text\":null"))
    }
    @Test fun localesAndNames() {
        fails("LOCALE", catalog(sourceLocale = "zz"))
        fails("LOCALE", catalog(sourceLocale = "en-!"))
        assertEquals("en-US", parse(catalog(sourceLocale = "en_us")).source.sourceLocale)
        fails("NAME_COLLISION", catalog("\"a.b\":{\"body\":{\"text\":\"a\"}},\"aB\":{\"body\":{\"text\":\"b\"}}"))
        fails("NAME_COLLISION", catalog("\"a_b\":{\"body\":{\"text\":\"a\"}},\"a.b\":{\"body\":{\"text\":\"b\"}}"))
        fails("RESERVED_NAME", catalog("\"constructor\":{\"body\":{\"text\":\"a\"}}"))
        fails("RESERVED_NAME", catalog("\"toString\":{\"body\":{\"text\":\"a\"}}"))
        fails("RESERVED_NAME", catalog("\"decode\":{\"body\":{\"text\":\"a\"}}"))
        fails("ARGUMENT_NAME", catalog().replace("\"name\":\"string\"", "\"id\":\"string\""))
    }
    @Test fun placeholdersParsedAsTokensAndRemainLiteral() {
        val parsed = CatalogParser().tokens("  \"'$ 100% \\ \n🦉 مرحبا {{name}} {name} {name} $" + "t(x)", setOf("name"))
        assertEquals(2, parsed.count { it is Token.Placeholder })
        assertTrue((parsed.first() as Token.Literal).value.contains("{name}"))
        fails("UNKNOWN_PLACEHOLDER", catalog().replace("Hi {name}", "Hi {unknown}"))
        fails("PLACEHOLDER", catalog().replace("Hi {name}", "Hi }"))
    }
    @Test fun declarationsKeepOrderAndTranslationsMayReorderOmitRepeat() {
        val source = catalog("\"a\":{\"arguments\":{\"last\":\"string\",\"first\":\"int\"},\"body\":{\"text\":\"{first} {last}\"}}")
        Files.createDirectories(directory.resolve("locales"))
        Files.writeString(directory.resolve("locales/fr.json"), """{"schemaVersion":1,"locale":"fr","messages":{"a":{"text":"{last} {last}"}}}""")
        val compiled = parse(source)
        assertEquals(listOf("last", "first"), compiled.source.messages.getValue("a").arguments.map { it.name })
        assertEquals(listOf(Token.Placeholder("last"), Token.Literal(" "), Token.Placeholder("last")), (compiled.translations.getValue("fr").messages.getValue("a") as Body.Text).tokens)
    }
    @Test fun translationsCannotIntroduceIdsOrChangeBodyContract() {
        Files.createDirectories(directory.resolve("locales"))
        Files.writeString(directory.resolve("locales/fr.json"), """{"schemaVersion":1,"locale":"fr","messages":{"new":{"text":"x"}}}""")
        fails("UNKNOWN_ID", catalog())
        Files.writeString(directory.resolve("locales/fr.json"), """{"schemaVersion":1,"locale":"fr","messages":{"welcome":{"text":"{missing}"}}}""")
        fails("UNKNOWN_PLACEHOLDER", catalog())
    }
    @Test fun normalizedDuplicateLocalesAndBodyContracts() {
        Files.createDirectories(directory.resolve("locales"))
        Files.writeString(directory.resolve("locales/fr-FR.json"), """{"schemaVersion":1,"locale":"fr-FR","messages":{}}""")
        Files.writeString(directory.resolve("locales/fr_fr.json"), """{"schemaVersion":1,"locale":"fr_fr","messages":{}}""")
        fails("DUPLICATE_LOCALE", catalog())
        Files.delete(directory.resolve("locales/fr_fr.json"))
        Files.delete(directory.resolve("locales/fr-FR.json"))
        Files.writeString(directory.resolve("locales/fr.json"), """{"schemaVersion":1,"locale":"fr","messages":{"items":{"text":"{quantity}"}}}""")
        val plural = """"items":{"arguments":{"quantity":"int"},"body":{"plural":"quantity","cases":{"one":"{quantity}","other":"{quantity}"}}}"""
        fails("BODY_CONTRACT", catalog(plural))
    }
    @Test fun grammaticalCategoriesAndSelectorContracts() {
        val plural = """"items":{"arguments":{"quantity":"int"},"body":{"plural":"quantity","cases":{"one":"{quantity} item","other":"{quantity} items"}}}"""
        assertIs<Body.Plural>(parse(catalog(plural)).source.messages.getValue("items").body)
        fails("PLURAL_CATEGORIES", catalog(plural, "fr")) // French many is required even for uncommon counts.
        fails("PLURAL_CATEGORIES", catalog(plural.replace("\"one\":", "\"zero\":")))
        fails("PLURAL_CATEGORIES", catalog(plural.replace("\"one\":", "\"=0\":")))
        fails("TYPE", catalog(plural.replace("\"one\":\"{quantity} item\"", "\"one\":{\"text\":\"nested\"}")))
        fails("PLURAL_SELECTOR", catalog(plural.replace("\"quantity\":\"int\"", "\"quantity\":\"string\"")))
        fails("RESERVED_COUNT", catalog(plural.replace("\"quantity\":\"int\"", "\"quantity\":\"int\",\"count\":\"int\"")))
    }
    @Test fun deterministicGenerationRemovesStaleOutputs() {
        val parsed = parse(catalog())
        val output = directory.resolve("generated")
        val options = GenerationOptions(kotlinPackage = "example.localization", platforms = setOf("kotlin"))
        Compiler.generate(parsed, options, output)
        val first = Files.readString(output.resolve("kotlin/example/localization/Messages.kt"))
        Files.writeString(output.resolve("stale.txt"), "obsolete")
        Compiler.generate(parsed, options, output)
        assertFalse(Files.exists(output.resolve("stale.txt")))
        assertEquals(first, Files.readString(output.resolve("kotlin/example/localization/Messages.kt")))
        assertEquals("KOTLIN_PACKAGE", assertFailsWith<CatalogException> { Compiler.generate(parsed, options.copy(kotlinPackage = "example.class"), output) }.code)
        assertEquals(first, Files.readString(output.resolve("kotlin/example/localization/Messages.kt")))
        assertFalse(first.contains(directory.toString()))
        val smaller = parse(catalog("\"goodbye\":{\"body\":{\"text\":\"Bye\"}}"))
        Compiler.generate(smaller, options, output)
        assertFalse(Files.readString(output.resolve("kotlin/example/localization/Messages.kt")).contains("welcome"))
    }
}
