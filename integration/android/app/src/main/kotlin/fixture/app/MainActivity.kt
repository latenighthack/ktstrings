package fixture.app

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import fixture.localization.AndroidTextResolver
import fixture.localization.Messages

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply { text = AndroidTextResolver(this@MainActivity).resolve(Messages.welcome("Ada"), "fr") })
    }
}
