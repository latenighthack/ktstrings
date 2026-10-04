package com.latenighthack.ktstrings.gradle

import org.gradle.api.Action
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

abstract class AppleOptions {
    abstract val enabled: Property<Boolean>
    abstract val frameworkName: Property<String>
    abstract val frameworkBundleIdentifier: Property<String>
    abstract val debugXCFramework: DirectoryProperty
    abstract val releaseXCFramework: DirectoryProperty
    abstract val releaseArchive: RegularFileProperty
    abstract val publicationName: Property<String>
}

abstract class ReactOptions {
    abstract val enabled: Property<Boolean>
    abstract val packageName: Property<String>
    abstract val packageVersion: Property<String>
    abstract val packageDirectory: DirectoryProperty
    abstract val archiveFile: RegularFileProperty
    abstract val publicationName: Property<String>
}

abstract class AndroidOptions {
    abstract val compose: Property<Boolean>
}

abstract class KtstringsExtension
    @Inject
    constructor(
        objects: ObjectFactory,
    ) {
        abstract val catalogDirectory: DirectoryProperty
        abstract val kotlinPackage: Property<String>
        val apple: AppleOptions = objects.newInstance(AppleOptions::class.java).apply { enabled.convention(false) }
        val react: ReactOptions = objects.newInstance(ReactOptions::class.java).apply { enabled.convention(false) }
        val android: AndroidOptions = objects.newInstance(AndroidOptions::class.java).apply { compose.convention(false) }

        fun apple(action: Action<AppleOptions>) = action.execute(apple)

        fun react(action: Action<ReactOptions>) = action.execute(react)

        fun android(action: Action<AndroidOptions>) = action.execute(android)
    }
