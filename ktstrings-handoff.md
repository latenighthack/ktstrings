# ktstrings implementation handoff

**Project:** `ktstrings`  
**Maven group:** `com.latenighthack.ktstrings`  
**Gradle plugin ID:** `com.latenighthack.ktstrings`  
**Status:** Implementation specification for a new standalone project.

## 1. Objective and locked decisions

Build a localization toolset that lets applications define one canonical catalog and obtain typed message APIs, native localization resources, React localization resources, and validated server-message decoding.

The following decisions are locked:

| Area | Decision |
|---|---|
| Ownership | A standalone project, independent of basekit |
| Name | `ktstrings` |
| Maven group | `com.latenighthack.ktstrings` |
| Publication | Publish libraries, compiler, Gradle plugin, and plugin marker to Maven Central |
| Canonical source | Structured JSON with explicit argument declarations and text/plural bodies |
| Adoption | A published Gradle plugin configures generation and build wiring |
| Compilation model | Ordinary source/resource generation; no Kotlin compiler plugin or KSP requirement |
| Shared state | Typed message references, resolved at presentation time |
| Android | Native `strings.xml` and `plurals.xml`, resolved through Android resource APIs |
| iOS | Native `.strings` and `.stringsdict`, resolved through Foundation |
| Apple packaging | Localization resources inside the generated framework slices of the distributed XCFramework |
| Static frameworks | Support resource-bearing static frameworks with modern Xcode |
| React | i18next and react-i18next integration |
| React packaging | Locale JSON files and usable runtime imports inside the generated React/npm package |
| Server text | Both verbatim literals and validated references to known local messages |
| V1 scope | Plain text, string/integer arguments, integer cardinal plurals |
| Downloads | Bundled catalogs; no runtime translation download required |
| Basekit | Optional integration for carrying and converting ktstrings types through generated bindings |

The packaging requirement is fundamental: **consumers must receive the translations in the artifacts they ship.** Generated files sitting in a build directory are insufficient.

The remaining design details in this document are implementation defaults chosen to make the handoff actionable. They are not claims that those details were individually agreed earlier.

## 2. Ownership and boundaries

### ktstrings owns

- Catalog schema and validation.
- Typed message constructors and text contracts.
- Compilation into platform resources.
- Locale availability metadata.
- Native resource lookup wrappers.
- i18next resources and typed React helpers.
- Server-reference validation and decoding.
- Gradle integration and artifact packaging.
- Independent platform tests and publication.

### Basekit owns

- Recognizing ktstrings types in ViewModel state.
- Converting those values into generated Apple and React binding contracts.
- Coordinating its binding-generation tasks with ktstrings generation.
- Demonstrating adoption in a shared application.

Neither project depends on the other to function. ktstrings must have independent consumers and tests.

The public text contracts belong to ktstrings. Do not create competing `UiText` definitions in basekit.

## 3. Repository and published artifacts

Use this structure:

```text
ktstrings/
  ktstrings/                    # KMP runtime and public contracts
  ktstrings-compiler/           # JVM catalog compiler and command-line entry point
  ktstrings-gradle-plugin/      # Published consumer Gradle plugin
  ktstrings-compose/            # Optional Android Compose presentation helpers
  build-logic/                  # Internal build conventions
  schema/                       # Versioned catalog and translation JSON Schemas
  examples/                     # Small catalog examples
  integration/
    kotlin/
    android/
    apple/
    react/
    published-consumer/
  docs/
  scripts/
  gradle/
```

Publish:

| Artifact | Purpose |
|---|---|
| `com.latenighthack.ktstrings:ktstrings:<version>` | KMP runtime and text contracts |
| `com.latenighthack.ktstrings:ktstrings-compiler:<version>` | JVM compiler |
| `com.latenighthack.ktstrings:ktstrings-gradle-plugin:<version>` | Gradle plugin implementation |
| `com.latenighthack.ktstrings:ktstrings-compose:<version>` | Optional Android Compose helpers |
| `com.latenighthack.ktstrings:com.latenighthack.ktstrings.gradle.plugin:<version>` | Plugin marker |

Publish the KMP runtime's generated target publications and metadata as well as its root publication.

Use one release version for all artifacts, including the marker and compiler/runtime coordinates embedded in the plugin. Default initial version: `0.1.0`.

The generated application React package and application XCFramework are **consumer build outputs**. They are not a universal catalog shipped inside the ktstrings runtime artifact.

The tool generates each application's own catalog into that application's artifacts.

## 4. Canonical catalog

### File layout

Each catalog-owning consumer module contains:

```text
localization/
  catalog.json
  locales/
    fr.json
    ar.json
    ja.json
```

V1 supports one logical catalog per owning Gradle project. Different projects can own different namespaced catalogs.

`catalog.json` is authoritative for:

- Schema version.
- Namespace.
- Source locale.
- Stable message IDs.
- Translator descriptions.
- Argument names and types.
- Source-language bodies.

Translation files contain only locale identity and translated bodies. They cannot redefine argument types or independently introduce IDs.

### Example source catalog

```json
{
  "schemaVersion": 1,
  "namespace": "app",
  "sourceLocale": "en",
  "messages": {
    "welcome": {
      "description": "Greeting on the home screen",
      "arguments": {
        "name": "string"
      },
      "body": {
        "text": "Welcome, {name}"
      }
    },
    "items.count": {
      "description": "Number of items shown in the list",
      "arguments": {
        "count": "int"
      },
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

### Example translation

```json
{
  "schemaVersion": 1,
  "locale": "fr",
  "messages": {
    "welcome": {
      "text": "Bienvenue, {name}"
    },
    "items.count": {
      "plural": "count",
      "cases": {
        "one": "{count} élément",
        "many": "{count} éléments",
        "other": "{count} éléments"
      }
    }
  }
}
```

### Message grammar

Support:

- A text body.
- A plural body with exactly one selector and text branches.
- Named placeholders: `{name}`.
- Escaped literal braces: `{{` and `}}`.

Parse into an internal representation before generating platform outputs. Do not implement generation with successive string replacements.

V1 excludes:

- Nested plurals or selects.
- Exact-number cases such as `=0`.
- Ordinal or fractional plurals.
- Arbitrary ICU syntax.
- Rich text and markup.
- Date, currency, or configurable number-format expressions.
- References to other messages.

A literal percent sign, quote, dollar sign, backslash, newline, or i18next nesting-looking string must remain literal after compilation.

### Argument rules

Support:

- `string`: a non-null string.
- `int`: a signed 32-bit integer.

Plural selectors must reference an `int` argument and accept values from zero through `Int.MAX_VALUE`.

Other integer arguments may be negative.

TypeScript constructors must validate integer range and integrality at runtime because `number` does not enforce either.

Translations may reorder, repeat, or omit interpolation placeholders. Unknown placeholders are errors. The declared argument contract remains the same across translations.

Use argument declaration order as the stable native formatting order. Preserve that order when parsing.

For plural messages whose selector is not named `count`, prohibit a separate argument named `count`, avoiding ambiguity with i18next's reserved plural option.

### Identity and validation

- Namespace: lowercase identifier suitable for resource and package generation.
- Message IDs: stable dot-separated identifier segments.
- Argument names: identifiers suitable for generated APIs.
- Normalize locale identifiers to a documented BCP 47 representation.
- Reject duplicate JSON properties rather than silently retaining the last value.
- Reject unsupported schema versions, unknown fields, invalid locales, unknown translation IDs, and conflicting generated names.
- Detect collisions after Kotlin, Swift, TypeScript, Android, and Objective-C name conversion.
- Reject prototype-sensitive names at JavaScript object boundaries or emit dictionaries that cannot interpret them as prototypes.

Generate collision errors with both original IDs and the conflicting generated identifier.

Do not rename IDs automatically to repair collisions.

### Plural validation

Use a pinned CLDR data version in the compiler. Record that version in generated metadata.

Require `other` and the locale's supported cardinal categories. Category availability is locale-level, including categories that may be uncommon for the permitted integer range.

Reject unsupported category names and categories that would introduce platform-specific semantics.

In particular, do not generate an English `zero` branch as an exact-zero shortcut. i18next treats an available `_zero` key specially, whereas Android's `zero` category is grammatical. Native semantics must remain consistent. [i18next plural documentation](https://www.i18next.com/translation-function/plurals)

Partial locale files are allowed, but each supplied message must be valid and complete.

## 5. Runtime and generated public APIs

### Shared text contract

The runtime exposes immutable text values:

- `UiText`: the common presentation-text type.
- `LiteralText`: verbatim content.
- `LocalMessage`: the base contract for catalog-backed references.
- Typed argument boundary values used by server adapters.

Generate a catalog-specific closed message hierarchy beneath `LocalMessage`, with concrete types for each message.

Application code constructs messages through generated APIs:

```kotlin
Messages.welcome(name = "Ada")
Messages.itemsCount(count = 3)
```

Do not expose a public application constructor taking an arbitrary ID and untyped argument map.

Generated constructors preserve arguments as values. They do not format or translate them.

A ViewModel can carry:

```kotlin
data class State(
    val heading: UiText,
    val itemCount: UiText,
    val serverNotice: UiText
)
```

Locale changes affect presentation, not ViewModel state or business logic.

Implement value equality and hashing for message references and arguments. This is necessary for state comparisons and deduplication.

### Apple API

Export runtime text types and generated typed constructors through the consumer's Kotlin framework.

The required functionality must be available from the XCFramework itself. Consumers must not need to compile separately collected Swift files to obtain basic constructors or native resolution.

Swift convenience source can be an additional output, but it is not a prerequisite for the packaged localization feature.

Avoid Kotlin value classes and unsupported generic exports in the public Apple boundary.

### React API

Generate:

- A literal-text type.
- A discriminated union of local messages.
- Argument types specific to each ID.
- Typed constructors.
- A pure resolver accepting an i18next instance and a text value.
- A react-i18next hook.
- A generated Kotlin/JS conversion helper for shared Kotlin text values.

Example public usage:

```tsx
const heading = messages.welcome({ name: "Ada" });

function Header() {
  const { text } = useKtstrings();
  return <h1>{text(heading)}</h1>;
}
```

Expose the resolver separately from the hook so it can be used in tests, SSR, and non-component code.

## 6. Locale selection and fallback

Generate an availability manifest listing the locales in which each message actually exists.

Resolve a local message using:

1. Exact requested locale.
2. Progressively less-specific locale.
3. Source locale.

Select the **whole message** before invoking the platform localization system.

Do not combine branches from different languages. Do not fill an incomplete translated plural with source-language branches.

Use the selected message language for grammatical selection. Missing Arabic text falling back to English must use English plural rules.

On Android, create or use the native resource context for the selected locale.

On Apple, select the appropriate localized resource bundle/table and pass the selected locale into native formatting.

On React, invoke i18next with the selected language and namespace explicitly for that message.

Retain normal i18next integration, but do not rely on its implicit per-key fallback to establish ktstrings' whole-message behavior.

Device locale, app language overrides, museum previews, and tests must be able to supply the requested locale explicitly.

Native and browser CLDR versions may differ. Use platform engines at runtime, maintain a documented compatibility baseline, and test representative values. Do not claim byte-identical locale formatting across arbitrary OS/browser versions.

## 7. Gradle plugin

### Primary consumer configuration

```kotlin
plugins {
    kotlin("multiplatform") version "<supported-kotlin-version>"
    id("com.android.library") version "<supported-agp-version>"
    id("com.latenighthack.ktstrings") version "0.1.0"
}

ktstrings {
    catalogDirectory.set(layout.projectDirectory.dir("localization"))
    kotlinPackage.set("com.example.app.localization")

    apple {
        enabled.set(true)
        frameworkName.set("Shared")
    }

    react {
        enabled.set(true)
        packageName.set("@example/localization")
        packageVersion.set("1.0.0")
    }

    android {
        compose.set(true)
    }
}
```

Plugin resolution must work with Maven Central:

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
```

Consumers also configure the normal dependency repositories for Kotlin and Android.

### Defaults

| Setting | Default |
|---|---|
| Catalog directory | `localization/` |
| Namespace | Read from catalog |
| Source locale | Read from catalog |
| Kotlin package | Required when Kotlin generation is integrated |
| Android native resources | Enabled when a supported Android target/plugin is present |
| Compose integration | Disabled |
| Apple packaging | Explicit opt-in |
| Framework name | Infer if exactly one framework name exists; otherwise require configuration |
| React packaging | Explicit opt-in |
| React package name | Required when enabled |
| React package version | Explicit value, otherwise consumer project version; reject `unspecified` |

Do not silently overwrite conflicting catalog namespace/source-locale settings.

Do not apply Kotlin, Android, KSP, SKIE, Node, or React tooling on the consumer's behalf.

Support catalog generation in a plain Gradle project, including React-only consumers. Kotlin and native compilation are required only when those outputs are integrated.

### Tasks and outputs

Register:

| Task | Responsibility |
|---|---|
| `validateKtstrings` | Validate catalog and translations |
| `generateKtstrings` | Generate shared types, contracts, metadata, and platform source resources |
| `reportKtstringsCoverage` | Report message coverage per locale |
| `collectKtstringsReact` | Produce the complete npm package |
| `assembleKtstringsDebugXCFramework` | Produce a resource-bearing debug XCFramework |
| `assembleKtstringsReleaseXCFramework` | Produce a resource-bearing release XCFramework |
| `verifyKtstringsPackaging` | Inspect enabled distribution outputs for required resources |

Target-specific native helper-compilation and resource-staging tasks may sit beneath these public tasks.

Use owned output directories under `build/generated/ktstrings` and `build/outputs/ktstrings`.

The plugin must:

- Wire generated common Kotlin before compilation and KSP.
- Add the matching runtime as an API dependency when it appears in generated public contracts.
- Export the runtime through selected Apple frameworks.
- Register Android sources and resources through supported build APIs.
- Add Compose dependencies only when enabled.
- Connect selected framework link/assembly outputs to resource-bearing distribution tasks.
- Wire publication of configured ktstrings distribution artifacts to packaging tasks.
- Attach validation to `check`.
- Add a lifecycle `check` task through Gradle's base plugin when necessary.

Use task providers and declared input/output relationships. Do not make task ordering depend on incidental task-name ordering or consumers writing `dependsOn` manually. [Gradle plugin guidance](https://docs.gradle.org/current/userguide/implementing_gradle_plugins_binary.html)

### Implementation quality

- Cacheable deterministic generation.
- Configuration-cache-compatible task actions.
- No configuration-time compiler execution or network access.
- No references to `Project` captured in task execution closures.
- No absolute machine paths or timestamps in generated content.
- Atomic replacement of owned generation directories.
- Removal of stale outputs when a message or locale is deleted.
- Plugin-order-independent integration through plugin callbacks.
- Compiler execution through an isolated classpath/process.
- Plugin, compiler, and runtime versions derived from one release source.
- Explicit local-development substitution; no default global Maven Local reliance.

Use compile-time dependencies on Kotlin/Android integration APIs without introducing conflicting consumer toolchain versions.

Publish and test a compatibility matrix rather than promising support for every Gradle/Kotlin/AGP release.

## 8. Android generation and packaging

### Native outputs

Generate namespaced:

- `strings.xml`.
- `plurals.xml`.
- Locale-qualified directories.
- Typed resource-resolution wrappers.

Use resource identifiers beginning with a catalog-specific prefix, such as `ktstrings_app_`.

Generate the full source catalog in default `values/` resources and in the source locale's qualified resources. This provides Android-required defaults while allowing explicit source-locale fallback.

Generate other locales only for messages actually translated there.

Use BCP 47 qualifiers where appropriate.

### Native resolution

Wrappers call:

- `Resources.getString`.
- `Resources.getQuantityString`.

Keep plural quantity separate from interpolation arguments.

Compile named placeholders into positional native format specifiers. Preserve placeholder repetition and reordering.

Handle Android XML escaping, quotes, apostrophes, percent signs, whitespace, newlines, and literal text without introducing markup behavior.

Do not select plural branches in Kotlin or package a replacement message engine.

Provide:

- A native Context/Resources resolver.
- An optional Compose helper following locale/configuration changes.

### Artifact requirements

For a library consumer:

1. Generation precedes resource processing.
2. The AAR contains the generated native resource definitions.
3. The consuming APK/AAB contains the compiled resources.
4. Resolution works without the original catalog directory.

Register generated resources through the supported Android variant APIs. [Android build integration](https://developer.android.com/build/extend-agp)

Exercise release resource shrinking and include preservation rules when supported raw-resource access would otherwise be vulnerable. Generated typed accessors should reference concrete resource IDs.

## 9. Apple generation and direct XCFramework packaging

### Required native resources

For every distributed framework slice, include:

```text
Shared.framework/
  Info.plist
  Shared
  Headers/
  Modules/
  en.lproj/
    Ktstrings_app.strings
    Ktstrings_app.stringsdict
  fr.lproj/
    Ktstrings_app.strings
    Ktstrings_app.stringsdict
```

This illustrates the iOS layout. Respect the actual resource location and versioned structure of macOS frameworks.

Use a namespaced table instead of claiming the consumer's general `Localizable` table.

Merge localization metadata into the framework's `Info.plist`, preserving unrelated Kotlin-generated properties.

Set the intended development region and record available localizations.

### Native execution

The generated resolver must perform Foundation bundle lookup and native formatting.

Use a generated Objective-C helper for the native formatting boundary:

- Generate fixed-signature functions for each message's string/integer arguments.
- Resolve and format within the native helper.
- Perform plural formatting through `.stringsdict` and Foundation.
- Pass an explicit selected locale.
- Use matching C/Objective-C integer types and native format specifiers.
- Link the helper into each Apple framework architecture.
- Expose it internally through generated Kotlin/Native interop.

This avoids an untyped dynamic varargs bridge and avoids requiring separately compiled Swift source.

Keep lookup and plural formatting together in the native helper rather than converting intermediate plural format strings through Kotlin and assuming all Foundation metadata survives.

The helper is formatting glue, not a custom plural engine.

### Packaging pipeline

1. Validate and generate the catalog.
2. Build target-specific native helper code.
3. Compile/link the selected Kotlin frameworks.
4. Obtain framework slices, including universal slices produced by the Kotlin toolchain.
5. Stage copies in directories owned by ktstrings tasks.
6. Add localization files and merged localization metadata to each staged framework.
7. Assemble the final resource-bearing XCFramework.
8. Verify all slices.
9. Sign final distribution artifacts only after enrichment.

Do not modify another task's cached output directory in an unrelated `doLast`.

Do not inject resources into an already-signed artifact without recreating its signature.

The plugin's resource-bearing XCFramework output is the documented artifact for distribution. Raw Kotlin framework outputs may exist as intermediate inputs; publishing or documenting those as the finished localization artifact is a defect.

Expose packaged-output providers so publishing tasks and Xcode build integration consume the correct artifact.

### Static and dynamic frameworks

Support both.

For dynamic frameworks, Xcode embeds the framework containing code and resources.

For static frameworks, require modern Xcode resource-bearing static-framework embedding. Xcode 15+ can embed the resources while omitting the statically linked main binary. [Apple static framework documentation](https://developer.apple.com/documentation/xcode/creating-a-static-framework)

This must be verified for the Kotlin-generated framework structure, not assumed solely from Apple's general support.

### Resource bundle lookup

A static framework's code is linked into the application. Therefore, `Bundle(for:)` on a Kotlin-exported class is not sufficient to identify the resource-bearing framework.

Generate bundle-location metadata and use:

- Framework bundle identifier.
- Framework bundle name.
- Lookup among loaded bundles where applicable.
- Explicit lookup in the host bundle's framework directory.
- An explicit bundle override for tests and unusual embedding.

Validate a located bundle's identity. Do not silently substitute `Bundle.main` and return a raw message key when the expected framework resources are absent.

A missing resource-bearing framework is a packaging/configuration failure with a clear diagnostic.

### Consumer experience

The iOS consumer adds and embeds the packaged XCFramework once.

They must not:

- Copy translations separately.
- Add a separate localization resource package.
- Maintain a second string catalog.
- Compile a custom message engine.

Provide Xcode instructions for direct XCFramework integration. Optional SwiftPM distribution support must be tested separately; do not assume a binary target automatically reproduces direct Xcode embedding behavior.

## 10. React/i18next generation and package contents

### Required generated package

```text
package.json
index.js
index.d.ts
react.js
react.d.ts
locales/
  en.json
  fr.json
  ar.json
metadata/
  catalog.json
```

`index.js` exports typed constructors, resolver, registration helpers, and eagerly imported resource objects.

`react.js` exports the hook and integration helpers.

Both JavaScript entry points have corresponding declarations.

The locale JSON files are physically included in the npm package, not merely referenced through relative paths outside it.

### Package behavior

- ESM.
- Explicit exports map.
- Root entry point usable without importing React.
- React subpath with React/react-i18next peer dependencies.
- Resource subpaths for direct catalog consumption.
- Package version from the consumer's configured application/package version.
- No automatic npm publishing.
- No downloading translations at runtime.
- No dependency on a checkout-local catalog directory.

Generate JavaScript resource modules for the eager entry point so consumers do not depend on inconsistent JSON import-attribute support. Retain the actual JSON files as package assets.

Default to eagerly registered bundled catalogs. Locale chunking can be added later without changing message constructors.

### i18next mapping

- Emit JSON v4 cardinal suffixes.
- Keep message IDs stable as flat resource keys.
- Set key and namespace separators explicitly for generated lookups.
- Pass the catalog namespace explicitly.
- Pass the selected language explicitly.
- Set the numeric `count` option for plural selection.
- Map interpolation arguments to compiler-controlled names.
- Never spread a server argument map into i18next options.
- Ensure message arguments cannot override `lng`, `ns`, postprocessors, or fallback configuration.

Keep interpolation values literal:

- Disable nesting for generated lookups.
- Keep interpolation-on-variable expansion disabled.
- Disable HTML entity escaping for React text rendering.
- Render through normal React text nodes.
- Do not use `dangerouslySetInnerHTML` or rich-text components.

i18next documents interpolation escaping and the importance of keeping variable contents from being recursively interpreted. [Interpolation documentation](https://www.i18next.com/translation-function/interpolation)

### Application integration

Consumers retain their existing i18next instance and provider.

A generated registration helper adds catalog resources to that instance. A convenience initializer may exist for new applications, but it must not mutate a global singleton during import.

The React hook uses react-i18next subscriptions so language changes rerender text without changing underlying message references.

For SSR:

- Use an instance per request or the application's existing isolation mechanism.
- Initialize the intended locale before rendering.
- Use the same locale and resources during hydration.
- Do not infer a different browser locale during initial hydration.

### Package verification

Run `npm pack`, install the resulting tarball in a clean fixture, and build/render that fixture.

Checking the package directory directly is insufficient: the package's `files` and `exports` definitions can exclude required resources.

## 11. Server-provided text

### Semantic boundary contract

Support two forms:

```json
{
  "kind": "literal",
  "text": "This note was written by a user."
}
```

```json
{
  "kind": "message",
  "namespace": "app",
  "id": "items.count",
  "arguments": {
    "count": 3
  },
  "fallbackText": "3 items"
}
```

This documents the transport-neutral shape. Do not impose a JSON transport dependency on all runtime consumers.

Consumers adapt protobuf, HTTP, or other transport contracts into ktstrings boundary types.

### Generated decoding

The compiler generates a registry that:

1. Checks namespace.
2. Finds the known ID.
3. Validates exact argument names and value types.
4. Validates integer range and plural quantity.
5. Constructs the corresponding typed message.

No coercion from `"3"` to `3`. No templates or executable formatting expressions received from the server.

For an unknown namespace/ID or invalid arguments:

1. Use `fallbackText` when present.
2. Otherwise use a required caller-supplied generic `UiText`.

An explicitly provided empty literal remains empty; absence and empty content are different.

Do not display raw IDs or diagnostic exception text.

### Content ownership

- Server/user literals are data.
- Historical text remains immutable content.
- Message references are appropriate for app-owned presentation messages that should resolve in the client's current language.
- Old clients may encounter new server message IDs; fallback text is the compatibility mechanism.
- Server references do not authorize remote catalog changes.

Diagnostic callbacks report error codes and message identity, not user argument values or literal content.

## 12. Basekit adoption

After ktstrings is independently published and validated:

- Add optional runtime-type recognition to basekit's ViewModel processor.
- Preserve `UiText`, nullable values, and supported lists in generated bindings.
- Use the catalog-generated Kotlin/JS encoder to emit the generated TypeScript union.
- Import the configured generated React package contract.
- Keep message values intact on Apple so native resolution remains in the consumer.
- Wire basekit generation after ktstrings generated Kotlin.
- Preserve existing `String` fields and behavior.
- Avoid requiring per-field custom React adapters for ktstrings values.

Do not introduce ktstrings as a mandatory dependency for applications that do not use localization.

Dogfood a shared demo with:

- A greeting with a string argument.
- An item-count plural.
- A known server message reference.
- An unknown server reference with fallback.
- A historical literal.

The inspected basekit repository currently uses Kotlin `2.3.10`, AGP `8.13.2`, Gradle `9.5.1`, and JDK 17. Use these as the first compatibility fixture. The demo's Apple framework is static; qualify that path explicitly.

Preserve existing unrelated repository documentation changes during later integration.

## 13. Maven Central publication

### Publication requirements

Publish through the Central Portal with:

- Verified namespace authorization.
- Correct coordinates.
- Sources and documentation artifacts as applicable.
- Required POM name, description, URL, license, developer, and SCM metadata.
- Checksums and PGP signatures.
- Complete KMP metadata and target publications.
- Plugin implementation and marker publication.
- Release dependencies that are themselves available.

Follow current Central requirements. [Sonatype publication requirements](https://central.sonatype.org/publish/requirements/)

Use Apache-2.0 as the initial project license, matching the surrounding library ecosystem. Include notices for redistributed CLDR data and other dependencies.

### Release engineering

- One authoritative version property.
- Tag/version consistency check.
- Compiler/runtime coordinates generated from that version.
- No snapshots or workspace paths in release metadata.
- Credentials and signing keys supplied through CI secrets.
- Release preparation on macOS so native artifacts are not silently omitted.
- Build and publication validation before Central upload.
- Explicit final release operation; do not publish during ordinary builds or tests.
- Verify resolution from Central after release.

Document artifact availability separately from app-generated XCFramework/npm publishing.

### Isolated published-consumer test

Before release:

1. Publish the full candidate artifact set into an isolated file repository.
2. Create a consumer without composite substitutions.
3. Resolve the Gradle plugin through its marker.
4. Let the plugin resolve the compiler/runtime by published coordinates.
5. Generate and build Kotlin, Android, Apple, and React outputs.
6. Inspect POMs, Gradle metadata, and package contents.
7. Repeat with a changed candidate version to catch stale embedded coordinates.

A source-build demo using project dependencies is not proof that the published plugin works.

## 14. Verification and acceptance criteria

### Catalog/compiler tests

Cover:

- Invalid schema versions.
- Duplicate properties and IDs.
- Unknown translation IDs.
- Invalid locales.
- Unknown placeholders.
- Invalid selector argument types.
- Missing plural categories.
- Generated-name collisions.
- Reserved-name handling.
- Argument reordering, repetition, and omission.
- Unicode, emoji, RTL text, whitespace, quotes, percent signs, braces, and backslashes.
- Literal i18next-looking text.
- Deterministic output across clean builds.
- Deletion of messages and locales.
- Stable argument ordering.

Compile negative Kotlin/Swift/TypeScript fixtures to prove incorrect argument types and missing arguments fail.

### Gradle tests

Use TestKit to prove:

- Different plugin application orders.
- KMP, Android, and plain catalog-only projects.
- No manual `dependsOn` or source-directory wiring.
- Correct source-generation ordering before KSP.
- Up-to-date repeated builds.
- Build-cache reuse.
- Configuration-cache reuse.
- Parallel target builds without output races.
- Correct invalidation when catalog, package configuration, compiler version, or release version changes.
- Removal of stale output.
- Clear failure when Apple tooling is requested on an unsupported host.
- Detection of ambiguous framework selection.
- Published plugin marker resolution.

### Android tests

- Compile resources with the supported Android toolchain.
- Inspect the AAR.
- Build release APK/AAB.
- Exercise release shrinking.
- Verify native resolution on a device/emulator.
- Verify configuration changes and locale overrides.
- Verify Compose updates when enabled.
- Run without the source catalog directory present.

### Apple tests

Verify both static and dynamic outputs:

- Every XCFramework slice contains the required localization tables.
- Framework localization metadata is correct.
- Native helper is present and callable.
- Swift compiles against exported typed APIs.
- Simulator application runs through Foundation lookup.
- Device release archive contains the resource-bearing framework.
- Static binary is linked appropriately and not incorrectly retained as an embedded executable.
- Signatures remain valid after packaging.
- Bundle lookup works without relying on a statically linked class's bundle.
- Lookup works when tests run from a different bundle.
- No separately copied resource package is necessary.
- No build-directory paths are used at runtime.
- Device/simulator slices have equivalent localization contents.
- macOS resource layout works for supported macOS outputs.

The Apple release gate must inspect an actual archive. A simulator run or XCFramework directory listing alone is insufficient.

### React tests

- Compile declarations and negative typing fixtures.
- Pack and install the npm tarball.
- Confirm locale JSON files are included.
- Import root and React subpaths.
- Build a production web bundle.
- Render local messages and literals.
- Switch language.
- Test SSR and hydration.
- Confirm interpolation arguments remain literal.
- Confirm no translation network request is needed.

### Cross-platform semantic tests

Use English, French, Arabic, Russian, Japanese, and a regional locale.

Test:

- Zero.
- One.
- Two.
- Representative few/many cases.
- Larger counts.
- `Int.MAX_VALUE`.
- Missing exact-region translation.
- Missing whole message.
- Source-language plural fallback.
- Literal and reference server text.
- Unknown/invalid server references.
- Locale changes while message references remain equal.

Compare message selection and argument preservation across platforms. Permit documented native formatting differences where platforms differ in digit or presentation conventions.

## 15. Delivery sequence

1. **Independent skeleton and publication**
   - Establish modules, coordinates, versioning, plugin marker, and isolated publishing fixture.

2. **Catalog and typed contracts**
   - Implement parser, schema validation, internal representation, message constructors, and server decoding.

3. **Android native path**
   - Generate resources, register them in the build, and verify release packaging.

4. **Apple packaging proof**
   - Prove native formatting and resource-bearing static/dynamic XCFrameworks through app embedding and archive inspection.

5. **React package**
   - Generate JSON v4 resources, typed APIs, hook integration, and verified npm tarballs.

6. **Build robustness**
   - Complete task wiring, caching, configuration-cache support, stale-output cleanup, and published-consumer tests.

7. **Basekit integration**
   - Add optional type recognition and generated binding conversions; dogfood the full shared demo.

8. **Release**
   - Publish all artifacts to Maven Central after all platform and publication gates pass.

Do not declare V1 complete with only generated files or empty platform shells.

## 16. Definition of done

A clean consumer can apply `com.latenighthack.ktstrings`, configure its catalog and enabled outputs, and build without manually wiring generation tasks.

The resulting artifacts contain:

- **Android:** native strings/plurals packaged through the AAR into the release application.
- **Apple:** native `.strings`/`.stringsdict` inside each distributed framework slice, retained in the embedded application framework.
- **React:** locale JSON files, statically importable resources, declarations, and helpers inside the installed npm package.

Typed constructors enforce message argument contracts. Native systems and i18next perform runtime localization. Server literals remain content; known references become typed messages; unknown references fall back predictably.

The standalone library works without basekit, its plugin resolves through Maven Central, and its actual release artifacts—not only source-build substitutes—pass the consumer tests.
