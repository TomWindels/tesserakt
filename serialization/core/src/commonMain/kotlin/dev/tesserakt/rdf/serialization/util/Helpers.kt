package dev.tesserakt.rdf.serialization.util

import kotlin.text.isDigit as isDigitKt
import kotlin.text.isWhitespace as isWhitespaceKt

@Suppress("ConvertTwoComparisonsToRangeCheck")
inline fun Char.isHexDecimal(): Boolean {
    if (this >= '0' && this <= '9') {
        return true
    }
    if (this >= 'A' && this <= 'F') {
        return true
    }
    if (this >= 'a' && this <= 'f') {
        return true
    }
    return false
}

/**
 * Converts [this] into a [Char], checking to see if it represents a hexadecimal value,
 *  or `false` it represents EOF (= `-1`)
 */
inline fun Int.isHexDecimal(): Boolean {
    if (this == -1) {
        return false
    }
    return Char(this).isHexDecimal()
}

inline fun Char.isDigit(): Boolean {
    return isDigitKt()
}

inline fun Char?.isDigit(): Boolean {
    return this != null && isDigit()
}

inline fun Char.isWhitespace(): Boolean {
    return isWhitespaceKt()
}

inline fun Char?.isWhitespace(): Boolean {
    return this != null && isWhitespace()
}
