package dev.tesserakt.rdf.serialization.util

import kotlin.experimental.or

object EscapeSequenceHelper {

    /**
     * Decodes numeric escape sequences into their code point values and mapped character escapes into their target
     *  representation in the resulting string.
     *
     * IMPORTANT: this method **throws an IllegalArgumentException** upon encountering unknown or invalid escape
     *  sequences
     */
    fun decodeNumericEscapes(input: String): String {
        val result = StringBuilder()
        var i = 0
        while (i < input.length - 1) {
            val first = input[i]
            if (first == '\\') {
                when (input[i + 1]) {
                    'u' -> {
                        if (i + 6 > input.length) {
                            throw IllegalArgumentException("Incomplete escape sequence at ${i + 1} for input `${input}`")
                        }
                        val code = input.substring(i + 2, i + 6).toInt(16)
                        i += 6
                        result.append(decode(code))
                    }
                    'U' -> {
                        if (i + 10 > input.length) {
                            throw IllegalArgumentException("Incomplete escape sequence at ${i + 1} for input `${input}`")
                        }
                        val code = input.substring(i + 2, i + 10).toInt(16)
                        i += 10
                        result.append(decode(code))
                    }
                    else -> {
                        throw IllegalArgumentException("Invalid escape sequence at ${i + 1} for input `${input}`: \\${input[i + 1]}")
                    }
                }
            } else {
                result.append(first)
                ++i
            }
        }
        if (i == input.length - 1) {
            result.append(input.last())
        }
        return result.toString()
    }

    /**
     * Decodes numeric escape sequences into their code point values and mapped character escapes into their target
     *  representation in the resulting string.
     *
     * IMPORTANT: this method **throws an IllegalArgumentException** upon encountering unknown or invalid escape
     *  sequences
     */
    fun decodeNumericAndMappedCharacterEscapes(
        input: String,
        mapping: Map<Char, Char> = DefaultReservedCharacterEscapes
    ): String {
        val result = StringBuilder()
        var i = 0
        while (i < input.length - 1) {
            val first = input[i]
            if (first == '\\') {
                val second = input[i + 1]
                when (second) {
                    'u' -> {
                        if (i + 6 > input.length) {
                            throw IllegalArgumentException("Incomplete escape sequence at ${i + 1} for input `${input}`")
                        }
                        val code = input.substring(i + 2, i + 6).toInt(16)
                        i += 6
                        result.append(decode(code))
                    }
                    'U' -> {
                        if (i + 10 > input.length) {
                            throw IllegalArgumentException("Incomplete escape sequence at ${i + 1} for input `${input}`")
                        }
                        val code = input.substring(i + 2, i + 10).toInt(16)
                        i += 10
                        result.append(decode(code))
                    }
                    else -> {
                        val mapped = mapping[second]
                            ?: throw IllegalArgumentException("Invalid escape sequence at ${i + 1} for input `${input}`: \\${input[i + 1]}")
                        result.append(mapped)
                        i += 2
                    }
                }
            } else {
                result.append(first)
                ++i
            }
        }
        if (i == input.length - 1) {
            result.append(input.last())
        }
        return result.toString()
    }

    val DefaultReservedCharacterEscapes = mapOf(
        't' to  Char(0x09),
        'b' to  Char(0x08),
        'n' to  Char(0x0A),
        'r' to  Char(0x0D),
        'f' to  Char(0x0C),
        '"' to  Char(0x22),
        '\'' to Char(0x27),
        '\\' to Char(0x5C),
    )

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
            val data =
                byteArrayOf(0b1111_0000.toByte(), 0b1000_0000.toByte(), 0b1000_0000.toByte(), 0b1000_0000.toByte())
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
