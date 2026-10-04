# Implementation and release gates

The implementation follows ktstrings-handoff.md. Central uploads are paused pending credentials and signing material; no ordinary build or test uploads remotely.

| Gate | Evidence / status |
| --- | --- |
| Foundation | Gradle 9.5.1, Kotlin 2.3.10, AGP 8.13.2, JDK 17; shared 0.1.0 coordinates, plugin marker and isolated candidate repository. |
| Catalog/runtime | Compiler and runtime JVM tests pass, including strict JSON/schema/locale/plural/name validation, typed Kotlin positive/negative compilation, atomic/stale output behavior, server argument validation/fallback, equality and whole-message selection. |
| Android | Library AAR, R8/resource-shrunk non-debuggable release APK and AAB build. Installed API 34 emulator tests pass native English/French/Arabic/Russian/Japanese/regional resolution, literals/escaping/repeated/reordered/omitted arguments/server fallbacks, Android Views binding/configured-context defaults, and Compose language switching with the same message reference. Reversed plugin order builds under configuration cache. |
| Apple independent consumer | Static/dynamic resource-bearing XCFramework, universal slices, Foundation native formatting, direct-Xcode simulator/actual device release archive inspection, macOS lookup and signature checks passed. See docs/apple.md and verification scripts. |
| React | Installed npm tarball declaration/type-negative/runtime/SSR/hydration/language-switching and production bundle gates passed. Actual locale assets and eager modules are in the package. |
| Published consumers | Isolated plugin-marker/compiler/runtime resolution and enabled platform builds pass at baseline and changed candidate versions. No composite substitution or Maven Local is used. |
| Basekit | Optional recognition/conversion and real KSP/JVM/JS/TypeScript/browser plus packaged static Apple simulator/release archive dogfood pass in the isolated `../basekit-ktstrings-adoption` worktree, branch `feat/ktstrings-adoption`, commit `b0f345f`. The original basekit checkout remains untouched. |
| Central release | Metadata/checksums and signed local candidates for all 14 publications are verified with a disposable test key. Production namespace credentials/signing material, authorized Central upload, and clean resolution from Central remain paused and outstanding. |

Reproduce local gates with the commands documented in docs/release.md. Instrumented Android release fixtures retain externally invoked test APIs while still running R8 and native resource shrinking; these retention rules belong to the acceptance fixture, not consumer catalogs.
