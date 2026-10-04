package fixture.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.mutableStateOf
import com.latenighthack.ktstrings.UiText
import fixture.localization.Messages
import fixture.localization.resolveText

class ComposeActivity : ComponentActivity() {
    val reference: UiText = Messages.itemsCount(2)
    val requestedLocale = mutableStateOf("en")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { BasicText(reference.resolveText(requestedLocale.value)) }
    }
}
