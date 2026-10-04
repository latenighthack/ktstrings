package com.latenighthack.ktstrings.gradle
import org.gradle.api.Plugin
import org.gradle.api.Project
class KtstringsPlugin : Plugin<Project> {
    override fun apply(project: Project) { project.pluginManager.apply("base") }
}
