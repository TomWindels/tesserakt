package dev.tesserakt.rdf.serialization.util

internal actual fun StringBuilder.appendCodePoint(codepoint: Int) {
    append(codepointToString(codepoint))
}
