# Keep externally invoked instrumentation APIs while still optimizing code and shrinking resources.
-keep class fixture.localization.** { public *; }
-keep class com.latenighthack.ktstrings.** { public *; }
-keep class androidx.tracing.** { *; }
# Release instrumentation executes library APIs that the application's own graph need not use.
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }
-keep class androidx.** { *; }
-keep class fixture.app.ComposeActivity { public *; }
-keep class fixture.app.ViewsActivity { public *; }
