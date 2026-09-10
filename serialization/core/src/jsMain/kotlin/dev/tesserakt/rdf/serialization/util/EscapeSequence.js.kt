package dev.tesserakt.rdf.serialization.util

private val strFromCodePoint = js("String.fromCodePoint")

internal actual fun StringBuilder.appendCodePoint(codepoint: Int) {
    append(strFromCodePoint(codepoint) as String)
}
