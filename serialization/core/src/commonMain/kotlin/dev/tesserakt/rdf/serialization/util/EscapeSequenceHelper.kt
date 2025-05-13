package dev.tesserakt.rdf.serialization.util

import kotlin.experimental.or

object EscapeSequenceHelper {

    fun decodeNumericEscapes(input: String): String {
        return input
            .replace(UnicodeSequence) { match -> decode(match.groups[1]!!.value.toInt(16)) }
            .replace(LongUnicodeSequence) { match -> decode(match.groups[1]!!.value.toInt(16)) }
    }

}

internal expect fun decode(codepoint: Int): String

/**
 * A helper function to transform [codepoint] into a [String] by transforming it into its UTF-8 representation and
 *  decoding that into a string; should only be used if the target platform does not otherwise support direct
 *  codepoint-to-string conversion.
 */
internal fun codepointToString(codepoint: Int): String {
    val encoded = when {
        codepoint <= 0x00007F -> {
            byteArrayOf(codepoint.toByte())
        }
        codepoint <= 0x0007FF -> {
            val data = byteArrayOf(0b1100_0000.toByte(), 0b1000_0000.toByte())
            data[0] = data[0] or (codepoint shr 6).toByte()
            data[1] = data[1] or (codepoint and 0x3F).toByte()
            data
        }
        codepoint <= 0x00FFFF -> {
            val data = byteArrayOf(0b1110_0000.toByte(), 0b1000_0000.toByte(), 0b1000_0000.toByte())
            data[0] = data[0] or (codepoint shr 12).toByte()
            data[1] = data[1] or ((codepoint shr 6) and 0x3F).toByte()
            data[2] = data[2] or (codepoint and 0x3F).toByte()
            data
        }
        codepoint <= 0x10FFFF -> {
            val data = byteArrayOf(0b1111_0000.toByte(), 0b1000_0000.toByte(), 0b1000_0000.toByte(), 0b1000_0000.toByte())
            data[0] = data[0] or (codepoint shr 18).toByte()
            data[1] = data[1] or ((codepoint shr 12) and 0x3F).toByte()
            data[2] = data[2] or ((codepoint shr 6) and 0x3F).toByte()
            data[3] = data[3] or (codepoint and 0x3F).toByte()
            data
        }
        else -> throw IllegalArgumentException("Codepoint exceeds bounds: $codepoint > 0x10FFFF")
    }
    return encoded.decodeToString()
}

private val UnicodeSequence = Regex("\\\\u([0-9a-fA-F]{4})")

private val LongUnicodeSequence = Regex("\\\\U([0-9a-fA-F]{8})")
