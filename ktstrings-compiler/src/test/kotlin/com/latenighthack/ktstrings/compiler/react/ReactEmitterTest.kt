package com.latenighthack.ktstrings.compiler.react

import com.latenighthack.ktstrings.compiler.*
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class ReactEmitterTest {
    @Test fun completePackageAndAcceptanceFixture() {
        val greeting = Message("welcome", null, listOf(Argument("name", ArgumentType.STRING)), Body.Text(listOf(Token.Literal("Welcome, "),Token.Placeholder("name"))))
        val plural = Message("items.count",null,listOf(Argument("count",ArgumentType.INT)),Body.Plural("count",linkedMapOf("one" to listOf(Token.Placeholder("count"),Token.Literal(" item")),"other" to listOf(Token.Placeholder("count"),Token.Literal(" items")))))
        val literal = Message("literal.syntax",null,emptyList(),Body.Text(listOf(Token.Literal("{{value}} ${'$'}t(welcome) % \" \\ ${'$'}"))))
        val catalog = CompiledCatalog(Catalog("acceptance","en",linkedMapOf("welcome" to greeting,"items.count" to plural,"literal.syntax" to literal)), linkedMapOf("fr" to LocaleCatalog("fr",mapOf("welcome" to Body.Text(listOf(Token.Literal("Bienvenue, "),Token.Placeholder("name"))))),"ar" to LocaleCatalog("ar",mapOf("welcome" to Body.Text(listOf(Token.Literal("أهلاً، "),Token.Placeholder("name")))))),"47")
        val output=Path.of("build/reactAcceptance")
        ReactEmitter().emit(catalog,GenerationOptions(reactPackageName="@ktstrings/acceptance",reactPackageVersion="1.0.0"),output)
        assertTrue(Files.exists(output.resolve("react/locales/en.json")))
        assertContains(Files.readString(output.resolve("react/index.js")),"skipOnVariables:true")
        assertContains(Files.readString(output.resolve("react/package.json")),"./react")
    }
}
