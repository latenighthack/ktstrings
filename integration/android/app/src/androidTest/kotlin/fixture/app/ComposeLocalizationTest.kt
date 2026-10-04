package fixture.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ComposeLocalizationTest {
    @get:Rule val compose = createAndroidComposeRule<ComposeActivity>()

    @Test
    fun localeOverrideUpdatesPresentationWithoutReplacingReference() {
        val original = compose.activity.reference
        compose.onNodeWithText("2 items").assertIsDisplayed()
        compose.runOnUiThread { compose.activity.requestedLocale.value = "fr" }
        compose.onNodeWithText("2 éléments").assertIsDisplayed()
        assertSame(original, compose.activity.reference)
    }
}
