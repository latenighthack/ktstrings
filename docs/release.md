# Release preparation

Central publication is currently paused. Ordinary builds, tests, and the verification workflow publish only into an isolated file repository. The manual preparation workflow produces signed candidate artifacts without uploading them remotely.

`gradle.properties` owns `VERSION_NAME` and `GROUP`. All libraries, native target publications, compiler, plugin implementation, and marker use that version; the plugin's embedded compiler/runtime coordinates are generated from it. The root project is a nonpublished build aggregator.

The compatibility fixture uses Kotlin 2.3.10, Gradle 9.5.1, AGP 8.13.2, JDK 17, Android compile SDK 35, and Node 22. Apple direct-Xcode qualification was performed with Xcode 26.6 and iOS 26.5; static and dynamic resource-bearing framework behavior must be requalified on other Xcode versions. Native runtime formatting versions can differ from the compiler's pinned ICU/CLDR data.

Before a release, run the macOS verification workflow or reproduce its gates locally:

```sh
./gradlew :ktstrings:jvmTest :ktstrings-compiler:test :ktstrings-gradle-plugin:test
python3 scripts/verify-android.py
python3 scripts/verify-published-consumer.py --all-platforms
python3 scripts/verify-release-metadata.py --version 0.1.0
python3 scripts/verify-release-metadata.py --version 0.1.1-candidate-check
```

The published-consumer script builds every KMP target publication on macOS, resolves the plugin marker/compiler/runtime without composite substitutions or Maven Local, and builds Kotlin/JVM/JS, Android AAR, Apple XCFrameworks, and installed React/npm packages. It repeats at a changed candidate version and checks configuration-cache reuse. Apple archive proof scripts and Android installed release tests qualify application artifact embedding separately.

The release audit requires all native, JVM, JS, Android, compiler, Compose, plugin, and marker publications. It checks coordinates, POM metadata, Gradle metadata, sources and usable documentation archives, and checksum contents. `--require-signatures --public-key PATH` verifies every deployed file's PGP signature in an isolated keyring. The unsigned local development candidates are not signed Central releases. `python3 scripts/verify-signing.py` proves the complete signing path with a disposable private key in a temporary isolated home; it removes that key afterward and never contacts Central.

For signed preparation, supply `RELEASE_SIGNING_KEY`, `RELEASE_SIGNING_PASSWORD`, and `RELEASE_PUBLIC_KEY` to the manual GitHub workflow. The signing key is an ASCII-armored in-memory PGP private key; it is never committed. Use a `v<version>` tag whose source `VERSION_NAME` agrees with the requested release. The workflow checks tag/version equality, runs the platform verification gates, and archives the complete signed file repository.

When Central publication resumes, supply a Central Portal token through `ORG_GRADLE_PROJECT_mavenCentralUsername` and `ORG_GRADLE_PROJECT_mavenCentralPassword`; the namespace must be authorized. The explicit upload operation is:

```sh
./gradlew -PreleaseSigning=true publishToMavenCentral
```

That operation stages and validates the deployment. Finish publication explicitly in the Central Portal, then test resolution using a clean consumer whose repositories include Maven Central and no candidate repository. Check the plugin marker and all KMP targets, compiler, runtime, and Compose artifacts. This post-release resolution gate remains outstanding while publication is paused.

Application-specific npm packages and resource-bearing XCFrameworks belong to consumer applications; they are not universal catalogs inside the ktstrings runtime release.

Publication metadata follows [Sonatype's requirements](https://central.sonatype.org/publish/requirements/). Upload and manual release behavior follows the [Maven publishing plugin's Central documentation](https://vanniktech.github.io/gradle-maven-publish-plugin/central/). GitHub runner selection follows the [hosted runner reference](https://docs.github.com/en/actions/reference/runners/github-hosted-runners).
