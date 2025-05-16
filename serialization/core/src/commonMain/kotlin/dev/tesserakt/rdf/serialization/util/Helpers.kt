package dev.tesserakt.rdf.serialization.util

inline fun Char.isHexDecimal(): Boolean {
    return isDigit() || lowercaseChar() in "abcdef"
}

inline fun Char?.isHexDecimal(): Boolean {
    return this != null && isHexDecimal()
}
