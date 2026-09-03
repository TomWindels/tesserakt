package dev.tesserakt.rdf.serialization.util

import kotlin.experimental.or

object EscapeSequenceHelper {

    // we want to prevent temporary object allocations
    @Suppress("ConvertTwoComparisonsToRangeCheck")
    fun hexToInt(char: Char): Int {
        if (char >= '0' && char <= '9') {
            return char - '0'
        }
        if (char >= 'A' && char <= 'F') {
            return char - 'A' + 10
        }
        if (char >= 'a' && char <= 'f') {
            return char - 'a' + 10
        }
        throw IllegalArgumentException("Invalid hexadecimal value: `${char}`")
    }

    /**
     * Returns the byte value (0..255) that is represented by the `0x00` representation [first] & [second].
     */
    fun hexToInt(first: Char, second: Char): Int {
        return (hexToInt(first) shl 4) or hexToInt(second)
    }

    /**
     * Converts the hexadecimal representation represented by `\uXXXX` (passed in as [one] through [four] characters),
     *  returning the decoded code point value
     */
    fun hexToInt(one: Char, two: Char, three: Char, four: Char): Int {
        val upper = hexToInt(one, two)
        val lower = hexToInt(three, four)
        return (upper shl 8) or lower
    }

    /**
     * Converts the hexadecimal representation represented by `\UXXXXXXXX` (passed in as [one] through [eight] characters),
     *  returning the decoded code point value
     */
    fun hexToInt(
        one: Char,
        two: Char,
        three: Char,
        four: Char,
        five: Char,
        six: Char,
        seven: Char,
        eight: Char,
    ): Int {
        val upper = hexToInt(one, two, three, four)
        val lower = hexToInt(five, six, seven, eight)
        return (upper shl 16) or lower
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
        // we first do a quick scan - if there aren't any `\` characters present, we don't need to copy the
        //  contents into a new string
        var i = 0
        while (i < input.length - 1 && input[i] != '\\') {
            ++i
        }
        // if this condition holds, i = input.length - 1, meaning we checked the entire length successfully
        if (input[i] != '\\') {
            return input
        }
        // we have to decode the input
        // input[i] is at `\\`, so we copy everything leading up to it, letting the rest of the logic take over
        val result = StringBuilder(input.length)
        result.appendRange(input, 0, i)
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
                        result.appendCodePoint(code)
                    }
                    'U' -> {
                        if (i + 10 > input.length) {
                            throw IllegalArgumentException("Incomplete escape sequence at ${i + 1} for input `${input}`")
                        }
                        val code = input.substring(i + 2, i + 10).toInt(16)
                        i += 10
                        result.appendCodePoint(code)
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

    /**
     * Encodes mapped character escapes into their target representation in the resulting string.
     */
    fun encodeMappedCharacterEscapes(
        input: String,
        mapping: Map<Char, Char> = ReversedDefaultReservedCharacterEscapes
    ): String {
        val result = StringBuilder()
        input.forEach { c ->
            if (c in mapping) {
                result.append('\\')
            }
            result.append(c)
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

    val ReversedDefaultReservedCharacterEscapes =
        DefaultReservedCharacterEscapes.asIterable().associate { it.value to it.key }

}

internal expect fun StringBuilder.appendCodePoint(codepoint: Int)

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
