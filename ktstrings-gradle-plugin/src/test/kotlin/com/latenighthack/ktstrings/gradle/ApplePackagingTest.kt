package com.latenighthack.ktstrings.gradle

import org.gradle.api.GradleException
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ApplePackagingTest {
    @Test
    fun rejectsUnqualifiedHostAndAmbiguousFramework() {
        assertContains(assertFailsWith<GradleException> { requireAppleHost("Linux") }.message.orEmpty(), "requires macOS")
        requireAppleHost("Mac OS X")
        assertEquals("Shared", inferAppleFrameworkName(listOf("Shared", "Shared")))
        assertContains(
            assertFailsWith<GradleException> {
                inferAppleFrameworkName(listOf("First", "Second"))
            }.message.orEmpty(),
            "apple.frameworkName",
        )
    }

    @Test
    fun checksEverySliceAgainstMetadataAndTranslationContents() {
        val root = Files.createTempDirectory("apple-packaging-test").toFile()
        try {
            val frameworks =
                listOf("ios-arm64", "ios-arm64-simulator").map { slice ->
                    root.resolve("$slice/Shared.framework").apply {
                        resolve("en.lproj").mkdirs()
                        resolve("en.lproj/Ktstrings_app.strings").writeText("\"hello\" = \"Hello\";\n")
                        resolve("en.lproj/Ktstrings_app.stringsdict").writeText("<plist><dict/></plist>")
                        resolve(
                            "Info.plist",
                        ).writeText(
                            "<plist><dict><key>CFBundleDevelopmentRegion</key><string>en</string><key>CFBundleLocalizations</key><array><string>en</string></array></dict></plist>",
                        )
                    }
                }
            verifyAppleFrameworks(root)
            frameworks.last().resolve("en.lproj/Ktstrings_app.strings").writeText("different")
            assertContains(assertFailsWith<GradleException> { verifyAppleFrameworks(root) }.message.orEmpty(), "contents differ")
            frameworks
                .last()
                .resolve(
                    "en.lproj/Ktstrings_app.strings",
                ).writeText(frameworks.first().resolve("en.lproj/Ktstrings_app.strings").readText())
            frameworks.last().resolve("Info.plist").writeText("<plist><dict/></plist>")
            assertContains(assertFailsWith<GradleException> { verifyAppleFrameworks(root) }.message.orEmpty(), "metadata")
            frameworks.last().resolve("en.lproj/Ktstrings_app.stringsdict").delete()
            assertContains(assertFailsWith<GradleException> { verifyAppleFrameworks(root) }.message.orEmpty(), "missing")
        } finally {
            root.deleteRecursively()
        }
    }
}
