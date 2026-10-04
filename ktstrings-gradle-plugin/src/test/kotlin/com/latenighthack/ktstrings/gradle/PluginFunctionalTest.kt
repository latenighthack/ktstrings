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
import kotlin.test.assertTrue

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
    @Test fun realKspReadsGeneratedCatalogInBothPluginOrders() {
        for (localizationFirst in listOf(true,false)) {
            fixture()
            write("settings.gradle", "rootProject.name='consumer'\ninclude 'processor'")
            val plugins=if(localizationFirst) "id 'com.latenighthack.ktstrings'; id 'org.jetbrains.kotlin.jvm'; id 'com.google.devtools.ksp'" else "id 'org.jetbrains.kotlin.jvm'; id 'com.google.devtools.ksp'; id 'com.latenighthack.ktstrings'"
            val runtime=System.getProperty("ktstrings.runtime.jar").replace("'","\\'")
            val build=Files.readString(directory.resolve("build.gradle"))
            write("build.gradle",build.replace("id 'com.latenighthack.ktstrings'",plugins)+"""
ktstrings.kotlinPackage.set('fixture.messages')
configurations.api.dependencies.removeIf { it.group == 'com.latenighthack.ktstrings' }
dependencies { api files('$runtime'); ksp project(':processor') }
""")
            write("processor/build.gradle", "plugins { id 'org.jetbrains.kotlin.jvm' }; repositories { mavenCentral() }; dependencies { implementation 'com.google.devtools.ksp:symbol-processing-api:2.3.10' }")
            write("processor/src/main/resources/META-INF/services/com.google.devtools.ksp.processing.SymbolProcessorProvider", "fixture.ProbeProvider")
            write("processor/src/main/kotlin/fixture/ProbeProvider.kt", """
package fixture
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSAnnotated
class ProbeProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor = object : SymbolProcessor {
        override fun process(resolver: Resolver): List<KSAnnotated> {
            check(resolver.getClassDeclarationByName(resolver.getKSNameFromString("fixture.messages.Messages")) != null) { "Catalog missing before KSP" }
            return emptyList()
        }
    }
}
""")
            write("src/main/kotlin/Consumer.kt", "package fixture\nval heading = fixture.messages.Messages.hello()")
            val result=run("compileKotlin","--rerun-tasks")
            assertEquals(TaskOutcome.SUCCESS,result.task(":kspKotlin")?.outcome)
            assertTrue(result.tasks.indexOfFirst { it.path == ":generateKtstrings" } < result.tasks.indexOfFirst { it.path == ":kspKotlin" })
            directory.resolve("build").toFile().deleteRecursively()
        }
    }
    @Test fun catalogCompilerAndPackageSettingsInvalidateGeneration() {
        fixture()
        run("collectKtstringsReact")
        val catalog=Files.readString(directory.resolve("localization/catalog.json"))
        write("localization/catalog.json",catalog.replace("Hello","Changed catalog"))
        assertEquals(TaskOutcome.SUCCESS,run("collectKtstringsReact").task(":generateKtstrings")?.outcome)
        assertContains(Files.readString(directory.resolve("build/outputs/ktstrings/react/locales/en.json")),"Changed catalog")
        val build=Files.readString(directory.resolve("build.gradle"))
        write("build.gradle",build+"\ntasks.named('generateKtstrings') { compilerVersion.set('next-candidate') }\n")
        assertEquals(TaskOutcome.SUCCESS,run("collectKtstringsReact").task(":generateKtstrings")?.outcome)
        val updated=Files.readString(directory.resolve("build.gradle"))
        write("build.gradle",updated.replace("@fixture/messages","@fixture/renamed"))
        assertEquals(TaskOutcome.SUCCESS,run("collectKtstringsReact").task(":generateKtstrings")?.outcome)
        assertContains(Files.readString(directory.resolve("build/outputs/ktstrings/react/package.json")),"@fixture/renamed")
    }
    @Test fun ambiguousAppleFrameworkSelectionFailsAtConfiguration() {
        fixture()
        write("build.gradle", """
plugins { id 'org.jetbrains.kotlin.multiplatform'; id 'com.latenighthack.ktstrings' }
kotlin { iosArm64 { binaries { framework { baseName='One' }; framework('another') { baseName='Two' } } } }
ktstrings { kotlinPackage.set('fixture.messages'); apple { enabled.set(true) } }
""")
        val failed=GradleRunner.create().withProjectDir(directory.toFile()).withPluginClasspath().withArguments("help").buildAndFail()
        assertContains(failed.output,"framework is ambiguous")
    }
    @Test fun unsupportedAppleHostReportsClearDiagnostic() {
        fixture()
        write("build.gradle", """
plugins { id 'org.jetbrains.kotlin.multiplatform'; id 'com.latenighthack.ktstrings' }
def originalHost=System.getProperty('os.name')
gradle.buildFinished { System.setProperty('os.name',originalHost) }
System.setProperty('os.name','Linux')
ktstrings { kotlinPackage.set('fixture.messages'); apple { enabled.set(true) } }
""")
        val failed=GradleRunner.create().withProjectDir(directory.toFile()).withPluginClasspath().withArguments("help").buildAndFail()
        assertContains(failed.output,"Apple packaging requires macOS with Xcode")
    }
    @Test fun configuredReactPublicationBuildsPackagedArtifactAutomatically() {
        for(existing in listOf(false,true)) {
            fixture()
            val build=Files.readString(directory.resolve("build.gradle"))
            write("build.gradle",build.replace("id 'com.latenighthack.ktstrings'", "id 'maven-publish'; id 'com.latenighthack.ktstrings'")+"""
group='fixture'
ktstrings.react.publicationName.set('localization')
publishing {
    ${if(existing) "publications { localization(MavenPublication) { artifactId='custom-catalog' } }" else ""}
    repositories { maven { name='Output'; url=uri(layout.buildDirectory.dir('distribution-repository')) } }
}
""")
            val result=run("publishLocalizationPublicationToOutputRepository","--configuration-cache")
            assertEquals(TaskOutcome.SUCCESS,result.task(":generateKtstrings")?.outcome)
            assertEquals(TaskOutcome.SUCCESS,result.task(":archiveKtstringsReact")?.outcome)
            val artifact=if(existing) "custom-catalog" else "consumer"
            val zip=directory.resolve("build/distribution-repository/fixture/$artifact/1.0.0/$artifact-1.0.0-ktstrings-react.zip").toFile()
            java.util.zip.ZipFile(zip).use { assertTrue(it.getEntry("package.json") != null); assertTrue(it.getEntry("locales/en.json") != null) }
            val repeated=run("publishLocalizationPublicationToOutputRepository","--configuration-cache")
            assertContains(repeated.output,"Reusing configuration cache")
            assertEquals(TaskOutcome.UP_TO_DATE,repeated.task(":generateKtstrings")?.outcome)
            directory.resolve("build").toFile().deleteRecursively()
        }
    }
    @Test fun kotlinIntegrationRequiresExplicitPackage() {
        fixture()
        val build=Files.readString(directory.resolve("build.gradle"))
        write("build.gradle",build.replace("id 'com.latenighthack.ktstrings'", "id 'org.jetbrains.kotlin.jvm'; id 'com.latenighthack.ktstrings'"))
        val failed=GradleRunner.create().withProjectDir(directory.toFile()).withPluginClasspath().withArguments("generateKtstrings").buildAndFail()
        assertContains(failed.output,"ktstrings.kotlinPackage is required")
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
