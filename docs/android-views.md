# Android Views and Compose

Generated messages work with Android Views and Compose. The Android resolver uses native `Resources` for both; no View binding dependency is required.

For a `TextView`, retain the message in your state and resolve it when binding the view:

```kotlin
val title: UiText = Messages.itemsCount(2)
val resolver = AndroidTextResolver(textView.context)
textView.text = resolver.resolve(title)
textView.contentDescription = resolver.resolve(Messages.welcome("Ada"))
```

Without an explicit locale, the resolver reads its context's current `Resources.Configuration`. This includes Android app-language configuration and a context created with `createConfigurationContext`. Use the view/activity context rather than an application context when the activity has a locale override. To choose a locale for an individual binding, call `resolver.resolve(title, "fr-CA")`.

Rebind Views when the selected language changes. Normal activity recreation creates and binds new views; activities that handle locale configuration changes themselves should rebind in `onConfigurationChanged` after calling `super`. A resolver does not subscribe to a View lifecycle or update an already assigned string. Message references remain unchanged while presentation changes. The runnable [ViewsActivity](../integration/android/app/src/main/kotlin/fixture/app/ViewsActivity.kt) demonstrates binding, explicit language switching, accessibility text, and configuration-change rebinding.

For Compose, enable `ktstrings.android { compose.set(true) }` and apply Kotlin's Compose compiler plugin. Resolve inside composition:

```kotlin
Text(title.resolveText())
// A Compose state value can supply an app-specific override:
Text(title.resolveText(requestedLocale))
```

The generated helper observes `LocalConfiguration`; changing configuration or the Compose locale parameter resolves the same message again. The [ComposeActivity](../integration/android/app/src/main/kotlin/fixture/app/ComposeActivity.kt) demonstrates the override path.

Installed release instrumentation covers actual TextView values, content descriptions, explicit French/Japanese locale changes, native configured-context defaults, and Compose English/French changes while retaining the same message references. Run `python3 scripts/verify-android.py` to build and install the shrunk fixture and run these tests.
