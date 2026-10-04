package com.latenighthack.ktstrings.compiler

import com.latenighthack.ktstrings.UiText
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

class KotlinTypingTest {
    @TempDir lateinit var directory: Path
    @Test fun generatedFactoriesAndDecoderCompileAndInvalidArgumentsFail() {
        val catalog = CompiledCatalog(Catalog("app", "en", linkedMapOf(
            "welcome" to Message("welcome", null, listOf(Argument("name", ArgumentType.STRING)), Body.Text(listOf(Token.Placeholder("name")))),
            "items.count" to Message("items.count", null, listOf(Argument("count", ArgumentType.INT)), Body.Plural("count", linkedMapOf("one" to listOf(Token.Placeholder("count")), "other" to listOf(Token.Placeholder("count")))))
        )), emptyMap(), "48")
        KotlinEmitter().emit(catalog, GenerationOptions(kotlinPackage = "fixture"), directory)
        val generated = directory.resolve("kotlin/fixture/Messages.kt")
        val stdlib = Unit::class.java.protectionDomain.codeSource.location.path
        val runtime = UiText::class.java.protectionDomain.codeSource.location.path
        val usage = directory.resolve("Usage.kt")
        fun compile(source: String): Pair<ExitCode, String> {
            Files.writeString(usage, source)
            val diagnostics = ByteArrayOutputStream()
            val result = K2JVMCompiler().exec(PrintStream(diagnostics), "-no-stdlib", "-no-reflect", "-classpath", "$stdlib${java.io.File.pathSeparator}$runtime", "-d", directory.resolve("classes").toString(), generated.toString(), usage.toString())
            return result to diagnostics.toString()
        }
        val positive = compile("import fixture.*\nval greeting = Messages.welcome(name = \"Ada\")\nval count = Messages.itemsCount(count = 3)\n")
        assertEquals(ExitCode.OK, positive.first, positive.second)
        for (negative in listOf("Messages.welcome(name = 3)", "Messages.welcome()", "Messages.itemsCount(count = \"3\")")) {
            val result = compile("import fixture.*\nval bad = $negative\n")
            assertEquals(ExitCode.COMPILATION_ERROR, result.first, result.second)
        }
    }
}
