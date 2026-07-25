package dev.tesserakt.rdf.serialization.util

internal actual fun decode(codepoint: Int): String {
    return String(intArrayOf(codepoint), 0, 1)
}
