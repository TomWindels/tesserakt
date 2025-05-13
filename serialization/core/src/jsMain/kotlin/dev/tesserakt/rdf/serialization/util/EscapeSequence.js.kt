package dev.tesserakt.rdf.serialization.util

private val strFromCodePoint = js("String.fromCodePoint")

internal actual fun decode(codepoint: Int): String {
    return strFromCodePoint(codepoint) as String
}
