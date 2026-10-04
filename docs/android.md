# Android integration

Apply the consumer Kotlin/Android plugins and `com.latenighthack.ktstrings`, then set `ktstrings.kotlinPackage`. Catalogs default to `localization/`. The plugin registers generated resources through Android variant APIs and connects generation to compilation automatically.

Use the generated `AndroidTextResolver(context).resolve(Messages.welcome("Ada"), "fr-CA")`. An explicit locale overrides device/app configuration. The resolver selects an available whole message first, creates a native locale resource context, and calls `Resources.getString` or `getQuantityString`; a fallback from missing Arabic text to English uses English grammar.

The plugin emits concrete resource references so Android resource shrinking can retain used localization resources. A consumer library's AAR contains the native definitions; the application APK/AAB contains compiled resources. Catalog JSON is a build input and is not needed on a device.

For Compose, set `ktstrings.android { compose.set(true) }` and apply the normal Kotlin Compose compiler plugin yourself. The plugin adds the optional Compose library and generated `UiText.resolveText(requestedLocale?)` presentation helper. It subscribes to `LocalConfiguration` and supports app-language overrides without replacing message references.

Run `python3 scripts/verify-android.py` with an Android SDK and a running emulator (or `ANDROID_SERIAL`). It builds the library AAR, optimized/shrunk release APK and AAB, inspects native resource packaging, installs the actual release packages, and exercises native resolution plus Compose language changes. The tested baseline is JDK 17, Kotlin 2.3.10, Gradle 9.5.1, AGP 8.13.2, compile SDK 35, and min SDK 23.
