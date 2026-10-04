# Optional Basekit adoption

ktstrings works independently of Basekit. Basekit’s optional integration recognizes runtime text types and catalog subclasses in ViewModel presentation state. Generated Apple bindings preserve native objects; generated React bindings call the catalog’s Kotlin/JS encoder and import the installed catalog package’s UiText union. Nullable fields and supported lists are preserved, and existing String fields keep their behavior.

Configure the catalog once in the consumer’s KSP options:

```kotlin
ksp {
    arg("basekit.viewmodel.ktstrings.reactModule", "@example/localization")
    arg("basekit.viewmodel.ktstrings.encoder", "com.example.localization.encodeKtstringsText")
}
```

The owning module applies both plugins. Their task wiring generates catalog Kotlin before KSP without per-field adapters. Use the generated React hook or packaged Apple resolver at presentation time. Mutator inputs require an explicit validated conversion adapter.

The tested Basekit adoption is available locally on branch `feat/ktstrings-adoption` in the sibling `basekit-ktstrings-adoption` worktree. It adds an opt-in `demo-localization` module, preserving builds of applications that do not use localization. Its catalog includes a greeting, item-count plural, known server reference, unknown reference with fallback, historical literal, nullable text and a list.

Inside that Basekit worktree, provide an explicit published candidate repository before the Central release:

```sh
./gradlew -PktstringsDemo=true -PktstringsRepository=/path/to/candidate-repository \
  :demo-localization:jvmTest :demo-localization:jsBrowserProductionLibraryDistribution \
  :demo-localization:collectBasekitReact :demo-localization:collectKtstringsReact \
  :demo-localization:assembleKtstringsReleaseXCFramework :demo-localization:collectBasekitAppleSwift
node integration/localization-react/verify.mjs
python3 scripts/verify-localization-apple.py
```

The demo resolved independently published plugin/compiler/runtime coordinates. Its npm tarballs passed TypeScript compilation and real Chromium language switching with intact shared message state. Its resource-bearing static XCFramework passed compilation of generated KVO bindings, Foundation resolution in an iOS simulator, simulator signature verification, and localization retention in an actual device release archive. No global Maven Local or per-field adapter was used.
