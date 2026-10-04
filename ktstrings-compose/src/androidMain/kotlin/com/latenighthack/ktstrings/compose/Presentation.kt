package com.latenighthack.ktstrings.compose

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import com.latenighthack.ktstrings.UiText

/** Configuration reads subscribe presentation to app-language and device-locale changes. */
@Suppress("DEPRECATION")
@Composable
fun currentKtstringsLocale(): String {
    val configuration = LocalConfiguration.current
    return if (Build.VERSION.SDK_INT >= 24) configuration.locales[0].toLanguageTag() else configuration.locale.toLanguageTag()
}
@Composable
fun resolveKtstrings(text: UiText, resolver: (UiText, String) -> String, requestedLocale: String = currentKtstringsLocale()): String =
    remember(text, resolver, requestedLocale) { resolver(text, requestedLocale) }
