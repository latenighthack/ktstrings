package com.latenighthack.ktstrings.gradle

import org.gradle.api.GradleException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertFailsWith

class AndroidPackagingTest {
    @TempDir lateinit var directory: Path

    private fun metadata(empty: Boolean = false) =
        directory.resolve("metadata.json").toFile().apply {
            writeText(
                if (empty) {
                    """{"namespace":"app","availability":{}}"""
                } else {
                    """{"namespace":"app","availability":{"welcome":["en"],"items.count":["en"]}}"""
                },
            )
        }

    private fun archive(
        name: String,
        entries: Map<String, String>,
    ) = directory.resolve(name).toFile().apply {
        ZipOutputStream(outputStream()).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
    }

    @Test
    fun emptyCatalogsRequireArchiveStructureWithoutNonexistentResources() {
        val metadata = metadata(empty = true)
        verifyAndroidArtifact(archive("empty.aar", mapOf("AndroidManifest.xml" to "manifest")), metadata)
        verifyAndroidArtifact(archive("empty.apk", mapOf("AndroidManifest.xml" to "manifest")), metadata)
        verifyAndroidArtifact(archive("empty.aab", mapOf("base/manifest/AndroidManifest.xml" to "manifest")), metadata)
        assertFailsWith<GradleException> { verifyAndroidArtifact(archive("broken.aar", emptyMap()), metadata) }
    }

    @Test
    fun defaultAarDefinitionsMustIncludeEveryExpectedSourceId() {
        val metadata = metadata()
        val definitions =
            """""" +
                """<resources><string name="ktstrings_app_welcome">hello</string><plurals name="ktstrings_app_items_count"><item quantity="other">items</item></plurals></resources>"""
        verifyAndroidArtifact(
            archive("complete.aar", mapOf("AndroidManifest.xml" to "manifest", "res/values/values.xml" to definitions)),
            metadata,
        )
        assertFailsWith<GradleException> {
            verifyAndroidArtifact(
                archive(
                    "translation-only.aar",
                    mapOf(
                        "AndroidManifest.xml" to "manifest",
                        "res/values-fr/values.xml" to definitions,
                    ),
                ),
                metadata,
            )
        }
    }

    @Test
    fun compiledApkAndBundleInspectExpectedNamesRatherThanUnrelatedPrefixes() {
        val metadata = metadata()
        val names = "ktstrings_app_welcome\u0000ktstrings_app_items_count\u0000"
        verifyAndroidArtifact(archive("complete.apk", mapOf("AndroidManifest.xml" to "manifest", "resources.arsc" to names)), metadata)
        verifyAndroidArtifact(
            archive("complete.aab", mapOf("base/manifest/AndroidManifest.xml" to "manifest", "base/resources.pb" to names)),
            metadata,
        )
        assertFailsWith<GradleException> {
            verifyAndroidArtifact(
                archive(
                    "wrong.apk",
                    mapOf(
                        "AndroidManifest.xml" to "manifest",
                        "resources.arsc" to "ktstrings_other_welcome\u0000ktstrings_app_items_count\u0000",
                    ),
                ),
                metadata,
            )
        }
        assertFailsWith<GradleException> {
            verifyAndroidArtifact(
                archive(
                    "partial.apk",
                    mapOf(
                        "AndroidManifest.xml" to "manifest",
                        "resources.arsc" to "ktstrings_app_welcome_extra\u0000ktstrings_app_items_count\u0000",
                    ),
                ),
                metadata,
            )
        }
    }
}
