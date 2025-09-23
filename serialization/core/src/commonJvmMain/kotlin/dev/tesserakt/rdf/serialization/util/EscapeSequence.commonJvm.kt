package dev.tesserakt.rdf.serialization.util

internal actual fun decode(codepoint: Int): String {
    return java.lang.String(intArrayOf(codepoint), 0, 1) as String
}
