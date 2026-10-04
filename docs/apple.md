# Apple localization distribution

Enable `ktstrings.apple.enabled` on an existing Kotlin Multiplatform framework project. Set `frameworkName` when the project declares multiple framework names. `frameworkBundleIdentifier` identifies the resource-bearing framework and must remain stable across builds. Generated common message constructors and `AppleMessagesResolver` are exported from the framework; consumers do not compile separate Swift support files.

Distribute `assembleKtstringsDebugXCFramework` or `assembleKtstringsReleaseXCFramework` outputs under `build/outputs/ktstrings/apple`. Raw Kotlin framework outputs are intermediates and lack packaged application catalog resources. Each slice contains the catalog's namespaced Foundation tables and development-region/localization metadata.

`apple.debugXCFramework`, `apple.releaseXCFramework`, and `apple.releaseArchive` expose task-backed packaged outputs. `archiveKtstringsReleaseXCFramework` creates a publication ZIP while preserving macOS framework symlinks. When a consumer already applies `maven-publish`, set `apple.publicationName` to an existing Maven publication name; publishing that publication builds and attaches the enriched release archive with classifier `ktstrings-xcframework`. No manual task dependency or raw framework-directory artifact is required.

Apple documents resource-bearing static frameworks for Xcode 15 or later. The acceptance fixture has been run on Xcode 26.6 with iOS 26.5; other Xcode versions require the same qualification before claiming support. In Xcode, add the packaged XCFramework to Frameworks, Libraries, and Embedded Content and select **Embed & Sign**, for static as well as dynamic frameworks. Xcode removes the static main archive while preserving its resource-bearing framework. Xcode 26 may inject an empty codeless-framework dylib stub; the Kotlin implementation remains linked into the application. No separate resource-copy phase or localization package is required. Sign distribution artifacts after packaging; Xcode signs their final embedded form.

Swift usage follows the Objective-C names exported by Kotlin:

```swift
let heading = Messages.shared.welcome(name: "Ada")
let resolver = AppleMessagesResolver(bundlePath: nil)
let text = resolver.resolve(text: heading, requestedLocale: "fr-CA")
```

The resolver selects a complete message using exact locale, progressively less-specific locale, then the catalog source locale. Foundation performs native plural selection and formatting with the selected language. Native formatting may use locale-specific grouping and digits; for example, English Foundation formats `Int.MAX_VALUE` as `2,147,483,647`.

SwiftUI on iOS and macOS can retain a typed reference and resolve it using the view's locale environment. Changing that environment rerenders the same reference. The complete [LocalizedHeading example](../integration/apple/LocalizedHeading.swift) is compiled and rendered by the Apple consumer proof:

```swift
struct Heading: View {
    let message: UiText
    let resolver: AppleMessagesResolver
    @Environment(\.locale) private var locale
    var body: some View {
        Text(verbatim: resolver.resolve(text: message, requestedLocale: locale.identifier))
    }
}
// Construct once; update the app's locale environment when its language changes.
let message = Messages.shared.welcome(name: "Ada")
Heading(message: message, resolver: resolver)
    .environment(\.locale, Locale(identifier: "fr"))
```

UIKit and AppKit use the resolved string directly. The controller reruns its presentation update when the application's selected locale changes; the generated message remains unchanged:

```swift
let message = Messages.shared.welcome(name: "Ada")
// UIKit UILabel:
label.text = resolver.resolve(text: message, requestedLocale: selectedLocale)
// AppKit NSTextField:
textField.stringValue = resolver.resolve(text: message, requestedLocale: selectedLocale)
```

The basic exported resolver needs no separate Swift support library or UI framework dependency in the Kotlin runtime. The acceptance proof creates actual `UIHostingController` and `NSHostingView` presentations, checks a rendered size change after changing SwiftUI's locale environment, and checks UIKit/AppKit label values before and after a locale change while preserving the message reference.

Resource lookup checks framework identity and the host application's framework directory. Tests and unusual hosting can provide an explicit framework path through `bundlePath`; an invalid explicit override fails rather than silently finding another bundle. Missing packaged resources cause a clear configuration error rather than displaying a raw message ID.

Run the independent consumer fixture with:

```sh
./gradlew -p integration/apple assembleKtstringsReleaseXCFramework -PstaticFramework=true
python3 scripts/verify-apple-distribution.py integration/apple/build/outputs/ktstrings/apple/release/Shared.xcframework --kind static
./gradlew -p integration/apple assembleKtstringsReleaseXCFramework -PstaticFramework=false
python3 scripts/verify-apple-distribution.py integration/apple/build/outputs/ktstrings/apple/release/Shared.xcframework --kind dynamic
```

The proof uses XcodeGen, a configured simulator, and Xcode's actual release archive path. It checks all slice contents, exported Swift argument types, native helper retention, simulator lookup, embedded resources, static binary removal, macOS resolution, and final ad-hoc signature integrity. Production distribution signing and provisioning remain the application's responsibility. SwiftPM binary-target embedding is not qualified by this direct-Xcode proof.
