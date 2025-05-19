package dev.tesserakt.rdf.serialization.util

import kotlin.text.isDigit as isDigitKt
import kotlin.text.isWhitespace as isWhitespaceKt

inline fun Char.isHexDecimal(): Boolean {
    return isDigit() || lowercaseChar() in "abcdef"
}

inline fun Char?.isHexDecimal(): Boolean {
    return this != null && isHexDecimal()
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
