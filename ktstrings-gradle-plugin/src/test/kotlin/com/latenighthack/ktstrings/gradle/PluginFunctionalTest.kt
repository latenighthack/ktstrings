package com.latenighthack.ktstrings.gradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContains
import kotlin.test.assertFalse

class PluginFunctionalTest {
    @TempDir lateinit var directory: Path
    private fun write(path: String,text: String) { val file=directory.resolve(path);Files.createDirectories(file.parent);Files.writeString(file,text) }
    private fun fixture(version:String="1.0.0") {
        write("settings.gradle", "rootProject.name='consumer'\nbuildCache { local { directory=file('cache') } }")
        val library=System.getProperty("ktstrings.compiler.lib").replace("\\","\\\\").replace("'","\\'")
        write("build.gradle", """
plugins { id 'com.latenighthack.ktstrings' }
version='$version'
repositories { mavenCentral() }
configurations.ktstringsCompiler.dependencies.clear()
dependencies { ktstringsCompiler fileTree('$library') { include '*.jar' } }
ktstrings { react { enabled.set(true); packageName.set('@fixture/messages') } }
""".trimIndent())
        write("localization/catalog.json", """{"schemaVersion":1,"namespace":"fixture","sourceLocale":"en","messages":{"hello":{"arguments":{},"body":{"text":"Hello"}}}}""")
        write("localization/locales/fr.json", """{"schemaVersion":1,"locale":"fr","messages":{"hello":{"text":"Bonjour"}}}""")
    }
    private fun run(vararg args:String)=GradleRunner.create().withProjectDir(directory.toFile()).withPluginClasspath().withArguments(*args,"--stacktrace").build()
    @Test fun jvmPluginOrderAndAutomaticCompilation() {
        for (localizationFirst in listOf(true,false)) {
            fixture()
            write("settings.gradle", "pluginManagement { repositories { gradlePluginPortal(); mavenCentral() } }\nrootProject.name='consumer'")
            val initial=Files.readString(directory.resolve("build.gradle"))
            val kotlinPlugin="id 'org.jetbrains.kotlin.jvm'"
            val plugins=if(localizationFirst) "id 'com.latenighthack.ktstrings'; $kotlinPlugin" else "$kotlinPlugin; id 'com.latenighthack.ktstrings'"
            val runtime=System.getProperty("ktstrings.runtime.jar").replace("'","\\'")
            write("build.gradle",initial.replace("id 'com.latenighthack.ktstrings'",plugins)+"""
ktstrings.kotlinPackage.set('fixture.messages')
configurations.api.dependencies.removeIf { it.group == 'com.latenighthack.ktstrings' }
dependencies { api files('$runtime') }
""")
            write("src/main/kotlin/Consumer.kt","package fixture\nval heading = fixture.messages.Messages.hello()")
            val result=run("compileKotlin")
            assertEquals(TaskOutcome.SUCCESS,result.task(":compileKotlin")?.outcome)
            directory.resolve("build").toFile().deleteRecursively()
        }
    }
    @Test fun catalogOnlyTaskGraphAndConfigurationCache() {
        fixture()
        val initial=run("collectKtstringsReact","verifyKtstringsPackaging","check","--configuration-cache")
        assertEquals(TaskOutcome.SUCCESS,initial.task(":generateKtstrings")?.outcome)
        assertEquals(TaskOutcome.SUCCESS,initial.task(":validateKtstrings")?.outcome)
        val repeated=run("collectKtstringsReact","verifyKtstringsPackaging","check","--configuration-cache")
        assertEquals(TaskOutcome.UP_TO_DATE,repeated.task(":generateKtstrings")?.outcome)
        assertContains(repeated.output,"Reusing configuration cache")
    }
    @Test fun buildCacheDeletionAndPackageConfigurationInvalidation() {
        fixture()
        run("collectKtstringsReact","--build-cache")
        directory.resolve("build").toFile().deleteRecursively()
        val restored=run("collectKtstringsReact","--build-cache")
        assertEquals(TaskOutcome.FROM_CACHE,restored.task(":generateKtstrings")?.outcome)
        fixture("2.0.0")
        val changed=run("collectKtstringsReact","--build-cache")
        assertEquals(TaskOutcome.SUCCESS,changed.task(":generateKtstrings")?.outcome)
        assertContains(Files.readString(directory.resolve("build/outputs/ktstrings/react/package.json")),"2.0.0")
        Files.delete(directory.resolve("localization/locales/fr.json"))
        run("collectKtstringsReact","--build-cache")
        assertFalse(Files.exists(directory.resolve("build/outputs/ktstrings/react/locales/fr.json")))
    }
}
