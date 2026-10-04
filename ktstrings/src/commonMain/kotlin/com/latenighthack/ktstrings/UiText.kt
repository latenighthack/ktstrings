package com.latenighthack.ktstrings

/** Presentation values retain arguments until the current locale is resolved. */
sealed interface UiText
data class LiteralText(val text: String) : UiText
abstract class LocalMessage(val namespace: String, val id: String) : UiText {
    abstract val arguments: Map<String, ArgumentValue>
}
sealed interface ArgumentValue {
    data class StringValue(val value: String) : ArgumentValue
    data class IntValue(val value: Int) : ArgumentValue
    /** Transport adapters may retain a JSON/protobuf numeric value for strict validation. */
    data class NumberValue(val value: Double) : ArgumentValue
    data object InvalidValue : ArgumentValue
}
sealed interface ServerText
data class ServerLiteral(val text: String) : ServerText
class ServerMessage(
    val namespace: String,
    val id: String,
    arguments: Map<String, ArgumentValue>,
    val fallbackText: String? = null
) : ServerText {
    val arguments: Map<String, ArgumentValue> = arguments.toMap()
}
enum class DecodeError { UNKNOWN_NAMESPACE, UNKNOWN_ID, ARGUMENT_NAMES, ARGUMENT_TYPE, INTEGER_RANGE, PLURAL_QUANTITY }
data class DecodeDiagnostic(val code: DecodeError, val namespace: String, val id: String)
fun interface DiagnosticSink { fun report(diagnostic: DecodeDiagnostic) }
class DecodeFailure(val code: DecodeError) : IllegalArgumentException(code.name)
abstract class ServerDecoder(private val namespace: String) {
    protected abstract fun decodeKnown(reference: ServerMessage): LocalMessage
    fun decode(value: ServerText, genericFallback: UiText, diagnostics: DiagnosticSink? = null): UiText {
        if (value is ServerLiteral) return LiteralText(value.text)
        value as ServerMessage
        return try {
            if (value.namespace != namespace) throw DecodeFailure(DecodeError.UNKNOWN_NAMESPACE)
            decodeKnown(value)
        } catch (failure: DecodeFailure) {
            diagnostics?.report(DecodeDiagnostic(failure.code, value.namespace, value.id))
            value.fallbackText?.let(::LiteralText) ?: genericFallback
        }
    }
}
object DecoderValidation {
    fun names(reference: ServerMessage, expected: Set<String>) {
        if (reference.arguments.keys != expected) throw DecodeFailure(DecodeError.ARGUMENT_NAMES)
    }
    fun string(reference: ServerMessage, name: String): String =
        (reference.arguments[name] as? ArgumentValue.StringValue)?.value ?: throw DecodeFailure(DecodeError.ARGUMENT_TYPE)
    fun integer(reference: ServerMessage, name: String, plural: Boolean = false): Int {
        val value = when (val argument = reference.arguments[name]) {
            is ArgumentValue.IntValue -> argument.value
            is ArgumentValue.NumberValue -> {
                val number = argument.value
                if (!number.isFinite() || number < Int.MIN_VALUE.toDouble() || number > Int.MAX_VALUE.toDouble() || number % 1.0 != 0.0)
                    throw DecodeFailure(DecodeError.INTEGER_RANGE)
                number.toInt()
            }
            else -> throw DecodeFailure(DecodeError.ARGUMENT_TYPE)
        }
        if (plural && value < 0) throw DecodeFailure(DecodeError.PLURAL_QUANTITY)
        return value
    }
}
/** Availability selection is whole-message; platform engines use this selected language. */
object LocaleSelection {
    fun select(requestedLocale: String, availableLocales: List<String>, sourceLocale: String): String {
        val available = availableLocales.associateBy { it.lowercase() }
        var candidate = requestedLocale.replace('_', '-').trim()
        // Extension/private-use tags do not identify a separate catalog language.
        val parts = candidate.split('-')
        val extension = parts.indexOfFirst { it.length == 1 }
        if (extension >= 0) candidate = parts.take(extension).joinToString("-")
        while (candidate.isNotEmpty()) {
            available[candidate.lowercase()]?.let { return it }
            candidate = candidate.substringBeforeLast('-', "")
        }
        return sourceLocale
    }
}
