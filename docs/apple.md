# Apple localization distribution

Enable `ktstrings.apple.enabled` on an existing Kotlin Multiplatform framework project. Set `frameworkName` when the project declares multiple framework names. `frameworkBundleIdentifier` identifies the resource-bearing framework and must remain stable across builds. Generated common message constructors and `AppleMessagesResolver` are exported from the framework; consumers do not compile separate Swift support files.

Distribute `assembleKtstringsDebugXCFramework` or `assembleKtstringsReleaseXCFramework` outputs under `build/outputs/ktstrings/apple`. Raw Kotlin framework outputs are intermediates and lack packaged application catalog resources. Each slice contains the catalog's namespaced Foundation tables and development-region/localization metadata.

Apple documents resource-bearing static frameworks for Xcode 15 or later. The acceptance fixture has been run on Xcode 26.6 with iOS 26.5; other Xcode versions require the same qualification before claiming support. In Xcode, add the packaged XCFramework to Frameworks, Libraries, and Embedded Content and select **Embed & Sign**, for static as well as dynamic frameworks. Xcode removes the static main archive while preserving its resource-bearing framework. Xcode 26 may inject an empty codeless-framework dylib stub; the Kotlin implementation remains linked into the application. No separate resource-copy phase or localization package is required. Sign distribution artifacts after packaging; Xcode signs their final embedded form.

Swift usage follows the Objective-C names exported by Kotlin:

```swift
let heading = Messages.shared.welcome(name: "Ada")
let resolver = AppleMessagesResolver(bundlePath: nil)
let text = resolver.resolve(text: heading, requestedLocale: "fr-CA")
```

The resolver selects a complete message using exact locale, progressively less-specific locale, then the catalog source locale. Foundation performs native plural selection and formatting with the selected language. Native formatting may use locale-specific grouping and digits; for example, English Foundation formats `Int.MAX_VALUE` as `2,147,483,647`.

Resource lookup checks framework identity and the host application's framework directory. Tests and unusual hosting can provide an explicit framework path through `bundlePath`; an invalid explicit override fails rather than silently finding another bundle. Missing packaged resources cause a clear configuration error rather than displaying a raw message ID.

Run the independent consumer fixture with:

```sh
./gradlew -p integration/apple assembleKtstringsReleaseXCFramework -PstaticFramework=true
python3 scripts/verify-apple-distribution.py integration/apple/build/outputs/ktstrings/apple/release/Shared.xcframework --kind static
./gradlew -p integration/apple assembleKtstringsReleaseXCFramework -PstaticFramework=false
python3 scripts/verify-apple-distribution.py integration/apple/build/outputs/ktstrings/apple/release/Shared.xcframework --kind dynamic
```

The proof uses XcodeGen, a configured simulator, and Xcode's actual release archive path. It checks all slice contents, exported Swift argument types, native helper retention, simulator lookup, embedded resources, static binary removal, macOS resolution, and final ad-hoc signature integrity. Production distribution signing and provisioning remain the application's responsibility. SwiftPM binary-target embedding is not qualified by this direct-Xcode proof.
