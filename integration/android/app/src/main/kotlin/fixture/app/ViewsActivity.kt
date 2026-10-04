package fixture.app

import android.app.Activity
import android.content.res.Configuration
import android.os.Bundle
import android.widget.TextView
import com.latenighthack.ktstrings.LiteralText
import com.latenighthack.ktstrings.UiText
import fixture.localization.AndroidTextResolver
import fixture.localization.Messages

/** Plain Android Views use the same immutable message as Compose. */
class ViewsActivity : Activity() {
    val reference: UiText = Messages.itemsCount(2)
    lateinit var label: TextView
        private set
    private var requestedLocale: String? = "en"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        label = TextView(this)
        setContentView(label)
        render()
    }

    fun showLocale(locale: String?) {
        requestedLocale = locale
        render()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        render()
    }

    private fun render() {
        val resolver = AndroidTextResolver(this)
        label.text = requestedLocale?.let { resolver.resolve(reference, it) } ?: resolver.resolve(reference)
        label.contentDescription = resolver.resolve(LiteralText("Items"))
    }
}
