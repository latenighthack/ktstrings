# UI platform support

Keep generated `UiText` values in shared state and resolve them when displaying text. The catalog compiler supplies native Android resources, resource-bearing Apple frameworks, and a bundled React package. Each UI framework controls when to resolve again after a locale change.

| UI framework | Presentation API | Guide and example |
| --- | --- | --- |
| Android Views | Assign `AndroidTextResolver(context).resolve(message, locale)` to `TextView.text`. Resolve again when the app locale or view configuration changes. | [Views guide](android-views.md), [Views activity](../integration/android/app/src/main/kotlin/fixture/app/ViewsActivity.kt) |
| Android Compose | Enable `android.compose`; call the generated `message.resolveText(locale)` inside composition. Its default locale follows `LocalConfiguration`. | [Android guide](android.md), [Compose activity](../integration/android/app/src/main/kotlin/fixture/app/ComposeActivity.kt) |
| SwiftUI on iOS and macOS | Read `@Environment(\.locale)` and pass its identifier to `AppleMessagesResolver.resolve`; display the result with `Text(verbatim:)`. | [Apple guide](apple.md), [shared SwiftUI view](../integration/apple/LocalizedHeading.swift) |
| UIKit | Assign the Apple resolver's string to `UILabel.text`. Resolve again when the selected locale changes. | [Apple guide](apple.md) |
| AppKit | Assign the Apple resolver's string to `NSTextField.stringValue`. Resolve again when the selected locale changes. | [Apple guide](apple.md) |
| React | Call `useKtstrings().text(message)` under the application's `I18nextProvider`. `changeLanguage` triggers presentation updates; the hook also accepts an explicit locale override. | [React setup](react.md), [installed-package acceptance](../integration/react/runtime-fixture.mjs) |

The resolver returns the final localized string. Pass it to ordinary text properties or verbatim text initializers so the UI framework does not interpret it as another localization key. Literal text and typed messages use the same presentation path. Translation assets ship in the application artifact; resolving or switching locales requires no translation download.

React acceptance installs the generated npm tarball and checks TypeScript contracts, SSR/hydration, live language switching, and a production bundle. The Apple acceptance script compiles the shared SwiftUI view into an iOS app and a macOS executable: `UIHostingController` and `NSHostingView` render English and French while retaining the message reference, and UIKit/AppKit text controls receive the same resolver output. Android's installed release fixtures check native Views and Compose presentation. See the guides for the exact artifact and toolchain qualification boundaries.

The platform guides describe packaged-artifact requirements, tested toolchains, and commands to reproduce the acceptance fixtures. [Catalog syntax](catalogs.md), [consumer distribution publication](distribution-publication.md), and [optional Basekit bindings](basekit.md) are shared across these presentation frameworks.
