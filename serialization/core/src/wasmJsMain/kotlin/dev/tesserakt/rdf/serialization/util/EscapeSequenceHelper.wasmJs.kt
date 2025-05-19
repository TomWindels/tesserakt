package dev.tesserakt.rdf.serialization.util

internal actual fun decode(codepoint: Int): String {
    return codepointToString(codepoint)
}
