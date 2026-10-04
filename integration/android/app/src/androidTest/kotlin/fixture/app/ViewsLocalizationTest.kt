package fixture.app

import android.content.res.Configuration
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import fixture.localization.AndroidTextResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class ViewsLocalizationTest {
    @Test
    fun textViewRebindsTheSameMessageForAppOverridesAndNativeConfiguration() {
        ActivityScenario.launch(ViewsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val original = activity.reference
                assertEquals("2 items", activity.label.text.toString())
                activity.showLocale("fr-CA")
                assertEquals("2 éléments", activity.label.text.toString())
                activity.showLocale("ja")
                assertEquals("2 個", activity.label.text.toString())
                assertEquals("Items", activity.label.contentDescription.toString())
                assertSame(original, activity.reference)

                // No explicit override: Resources.Configuration determines the locale.
                val config = Configuration(activity.resources.configuration)
                config.setLocale(Locale.FRENCH)
                val localizedContext = activity.createConfigurationContext(config)
                activity.label.text = AndroidTextResolver(localizedContext).resolve(original)
                assertEquals("2 éléments", activity.label.text.toString())
                config.setLocale(Locale.JAPANESE)
                activity.label.text = AndroidTextResolver(activity.createConfigurationContext(config)).resolve(original)
                assertEquals("2 個", activity.label.text.toString())
                assertSame(original, activity.reference)
            }
        }
    }
}
