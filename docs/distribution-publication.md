# Publishing generated application distributions

Application catalogs produce consumer-specific artifacts. To attach their packaged outputs to a Maven publication, apply `maven-publish` and opt in by publication name:

```kotlin
plugins { id("maven-publish") }
ktstrings {
    react {
        enabled.set(true)
        packageName.set("@example/localization")
        packageVersion.set("1.0.0")
        publicationName.set("localization")
    }
    apple {
        enabled.set(true)
        frameworkName.set("Shared")
        publicationName.set("localization")
    }
}
```

The plugin creates the named Maven publication when absent, or enriches an existing publication while preserving its coordinates and artifacts. Standard Maven publication coordinates come from the consumer project or its publication configuration. React uses the `ktstrings-react` ZIP classifier; Apple uses `ktstrings-xcframework`. Publication tasks depend on generation, collection, native linking, resource staging and archive creation through output providers. Consumers do not write manual task dependencies.

React output providers are `react.packageDirectory` and `react.archiveFile`. Apple output providers are `apple.debugXCFramework`, `apple.releaseXCFramework`, and `apple.releaseArchive`. The Apple archive preserves the resource-bearing XCFramework and macOS framework symlinks. React ZIP entries use reproducible ordering and timestamps.

Configure publication repositories and credentials through ordinary Gradle publishing configuration. `check`, generation, collection and assembly do not invoke publication. Maven publication of application distributions, npm publication, and release of ktstrings itself are separate explicit operations.
