package com.latenighthack.ktstrings

import kotlin.test.*
class RuntimeTest {
    private data class Known(val count: Int) : LocalMessage("app", "items") {
        override val arguments = mapOf("count" to ArgumentValue.IntValue(count))
    }
    private val decoder = object : ServerDecoder("app") {
        override fun decodeKnown(reference: ServerMessage): LocalMessage {
            if (reference.id != "items") throw DecodeFailure(DecodeError.UNKNOWN_ID)
            DecoderValidation.names(reference, setOf("count"))
            return Known(DecoderValidation.integer(reference, "count", plural = true))
        }
    }
    @Test fun referencesHaveValueEquality() { assertEquals(Known(3), Known(3)); assertEquals(Known(3).hashCode(), Known(3).hashCode()) }
    @Test fun emptyLiteralAndFallbackArePreserved() {
        assertEquals(LiteralText(""), decoder.decode(ServerLiteral(""), LiteralText("generic")))
        assertEquals(LiteralText(""), decoder.decode(ServerMessage("new", "new", emptyMap(), ""), LiteralText("generic")))
    }
    @Test fun invalidNumbersAndArgumentNamesFallbackWithoutContentDiagnostics() {
        val fallback = LiteralText("generic")
        for (number in listOf(-1.0, 1.5, Double.NaN, Double.POSITIVE_INFINITY, 2147483648.0)) {
            val diagnostics = mutableListOf<DecodeDiagnostic>()
            assertEquals(fallback, decoder.decode(ServerMessage("app", "items", mapOf("count" to ArgumentValue.NumberValue(number))), fallback, DiagnosticSink { diagnostics += it }))
            assertEquals(1, diagnostics.size)
        }
        assertEquals(fallback, decoder.decode(ServerMessage("app", "items", mapOf("count" to ArgumentValue.StringValue("3"))), fallback))
        assertEquals(Known(Int.MAX_VALUE), decoder.decode(ServerMessage("app", "items", mapOf("count" to ArgumentValue.IntValue(Int.MAX_VALUE))), fallback))
    }
    @Test fun selectsCompleteMessageAndProgressiveFallback() {
        assertEquals("fr", LocaleSelection.select("fr-CA", listOf("en", "fr"), "en"))
        assertEquals("en", LocaleSelection.select("ar-EG", listOf("en"), "en"))
        assertEquals("zh-Hant", LocaleSelection.select("zh-Hant-TW-u-nu-latn", listOf("en", "zh-Hant"), "en"))
    }
}
