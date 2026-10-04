# ktstrings

Typed localization catalogs compiled into Android, Apple, and React application artifacts.

Coordinates and plugin ID: `com.latenighthack.ktstrings`; initial version: `0.1.0`.
The specification is [ktstrings-handoff.md](ktstrings-handoff.md).

Use JDK 17, Kotlin 2.3.10, Gradle 9.5.1, AGP 8.13.2, and macOS/Xcode for Apple builds.
All application translations are generated from the consumer catalog; they are not bundled in the universal runtime.

Supported presentation frameworks: **Android Views and Compose, SwiftUI on iOS and macOS, UIKit, AppKit, and React**. See the [support matrix and examples](docs/platform-support.md) for their APIs, locale updates, and acceptance fixtures.

## Usage

For an existing Kotlin Android project, apply the ktstrings plugin in the module that owns your strings and choose a package for the generated API:

```kotlin
// build.gradle.kts (alongside your existing Kotlin and Android plugins)
plugins {
    id("com.latenighthack.ktstrings") version "0.1.0"
}

ktstrings {
    kotlinPackage.set("com.example.localization")
}
```

Tagged releases are published to Maven Central through GitHub Actions. For local candidate setup and artifact resolution, see the [published-consumer guide](integration/published-consumer/README.md). The plugin adds the runtime dependency and wires generated Kotlin and Android resources into your build.

Create `localization/catalog.json` in that module:

```json
{
  "schemaVersion": 1,
  "namespace": "app",
  "sourceLocale": "en",
  "messages": {
    "welcome": {
      "arguments": { "name": "string" },
      "body": { "text": "Welcome, {name}" }
    },
    "items.count": {
      "arguments": { "count": "int" },
      "body": {
        "plural": "count",
        "cases": {
          "one": "{count} item",
          "other": "{count} items"
        }
      }
    }
  }
}
```

Add translations under `localization/locales/`, for example `fr.json`:

```json
{
  "schemaVersion": 1,
  "locale": "fr",
  "messages": {
    "welcome": { "text": "Bienvenue, {name}" }
  }
}
```

Build normally, or run `./gradlew :app:generateKtstrings` explicitly (replace `app` with your module name). Use `:app:validateKtstrings` to validate the catalog and `:app:reportKtstringsCoverage` to report translation coverage.

Use the generated messages in your Kotlin code and resolve them when displaying text:

```kotlin
import android.content.Context
import com.example.localization.AndroidTextResolver
import com.example.localization.Messages

fun sample(context: Context) {
    val greeting = Messages.welcome(name = "Ada")
    val itemCount = Messages.itemsCount(count = 3)
    val resolver = AndroidTextResolver(context)

    resolver.resolve(greeting, "en")    // "Welcome, Ada"
    resolver.resolve(greeting, "fr-CA") // "Bienvenue, Ada" (falls back to fr)
    resolver.resolve(itemCount, "fr")   // "3 items" (falls back to source en)
}
```

Message values retain their typed arguments until presentation, so you can keep them in a ViewModel and resolve them again after a locale change. Omit the locale argument to use the current Android configuration. Plural counts must be nonnegative integers.

See the [sample catalog](examples/localization/catalog.json) and platform guides for [Android and Compose](docs/android.md), [Apple](docs/apple.md), and [React](docs/react.md). The [catalog guide](docs/catalogs.md) covers placeholders, plural rules, and server message decoding.

## Development

Run `./gradlew lint` to check Kotlin sources and Gradle scripts (including integration fixtures) and run Android lint.
Run `./gradlew ktlintFormat` to format them using the shared `.editorconfig` settings.
Module `check` tasks include ktlint; `./gradlew check` runs lint and all module checks.

Local candidate publication uses `publishAllPublicationsToCandidateRepository`, never global Maven Local.
Central upload is an explicit release operation after verification and requires credentials and signing material.
