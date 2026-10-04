# ktstrings

Typed localization catalogs compiled into Android, Apple, and React application artifacts.

Coordinates and plugin ID: `com.latenighthack.ktstrings`; initial version: `0.1.0`.
The specification is [ktstrings-handoff.md](ktstrings-handoff.md).

Use JDK 17, Kotlin 2.3.10, Gradle 9.5.1, AGP 8.13.2, and macOS/Xcode for Apple builds.
All application translations are generated from the consumer catalog; they are not bundled in the universal runtime.

Local candidate publication uses `publishAllPublicationsToCandidateRepository`, never global Maven Local.
Central upload is an explicit release operation after verification and requires credentials and signing material.
