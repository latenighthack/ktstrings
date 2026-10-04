# Isolated published consumers

Run `python3 scripts/verify-published-consumer.py` on macOS to publish all candidate artifacts into an isolated file repository, resolve the plugin through its published marker, and build consumers without composite substitutions or Maven Local. It verifies POM metadata and repeats using a changed release version to detect stale compiler/runtime coordinates.

Each consumer builds generated common Kotlin for JVM and JS, packages React, exercises configuration-cache reuse, and installs the npm tarball in the independent React acceptance fixture. Add `--all-platforms` to also compile Android and build resource-bearing release XCFramework outputs. Android device and Apple archive acceptance remain additional gates; directory inspection alone is insufficient.

Candidate repositories and consumers live under `build/`. This script never uploads to Maven Central or publishes npm packages.
