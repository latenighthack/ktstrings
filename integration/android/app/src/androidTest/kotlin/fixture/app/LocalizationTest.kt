package fixture.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.latenighthack.ktstrings.LiteralText
import com.latenighthack.ktstrings.ServerMessage
import fixture.localization.AndroidTextResolver
import fixture.localization.Messages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalizationTest {
    @Test
    fun nativeMessagesResolveFromShrunkArtifact() {
        val resolver = AndroidTextResolver(InstrumentationRegistry.getInstrumentation().targetContext)
        assertEquals("Bienvenue, Ada", resolver.resolve(Messages.welcome("Ada"), "fr"))
        assertEquals("Bienvenue au Canada, Ada", resolver.resolve(Messages.welcome("Ada"), "fr-CA"))
        assertEquals("Bienvenue, Ada", resolver.resolve(Messages.welcome("Ada"), "fr-FR"))
        assertEquals("Welcome, Ada", resolver.resolve(Messages.welcome("Ada"), "ar"))
        assertEquals("1 item", resolver.resolve(Messages.itemsCount(1), "en"))
        assertEquals("2 items", resolver.resolve(Messages.itemsCount(2), "en"))
        assertEquals("1 élément", resolver.resolve(Messages.itemsCount(1), "fr-CA"))
        assertEquals("3 предмета", resolver.resolve(Messages.itemsCount(3), "ru"))
        assertEquals("5 предметов", resolver.resolve(Messages.itemsCount(5), "ru"))
        assertEquals("0 個", resolver.resolve(Messages.itemsCount(0), "ja"))
        assertTrue(resolver.resolve(Messages.itemsCount(Int.MAX_VALUE), "en").contains("2147483647"))
        assertEquals("History stays literal: 100% {name} \$t(no) 🦉", resolver.resolve(Messages.history(), "ar"))
        assertEquals(
            "  \"quotes\" 'apostrophe' & <plain> \\ newline\n" +
                "$" + "t(injected) {{name}} | -3 | 100% | {name} | " + "$" + "t(no) | 🦉  ",
            resolver.resolve(
                Messages.syntax("$" + "t(injected) {{name}}", -3),
                "en",
            ),
        )
        assertEquals("B | -2 | A | B | 100%", resolver.resolve(Messages.argumentOrder("A", -2, "B"), "en"))
        assertEquals("A | A", resolver.resolve(Messages.argumentOrder("A", -2, "B"), "fr"))
        assertTrue(resolver.resolve(Messages.itemsCount(0), "ar").contains("لا عناصر"))
        assertTrue(resolver.resolve(Messages.itemsCount(1), "ar").contains("عنصر"))
        assertTrue(resolver.resolve(Messages.itemsCount(2), "ar").contains("عنصران"))
        assertTrue(resolver.resolve(Messages.itemsCount(3), "ar").contains("عناصر"))
        assertTrue(resolver.resolve(Messages.itemsCount(11), "ar").contains("عنصرًا"))
        assertEquals("", resolver.resolve(LiteralText(""), "fr"))
        val ref = Messages.itemsCount(2)
        assertEquals(ref, Messages.itemsCount(2))
        val generic = LiteralText("generic")
        assertEquals("fallback", resolver.resolve(Messages.decode(ServerMessage("new", "new", emptyMap(), "fallback"), generic), "fr"))
    }
}
