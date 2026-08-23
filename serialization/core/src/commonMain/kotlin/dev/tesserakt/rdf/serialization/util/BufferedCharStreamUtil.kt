package dev.tesserakt.rdf.serialization.util

import dev.tesserakt.rdf.serialization.InternalSerializationApi
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

@InternalSerializationApi
@OptIn(ExperimentalContracts::class)
inline fun BufferedCharStream.expect(condition: Boolean, message: () -> String) {
    contract {
        returns() implies condition
    }
    if (!condition) {
        val msg = message().ifBlank { "No further information available." }
        bail(msg)
    }
}

@OptIn(InternalSerializationApi::class)
inline fun BufferedCharStream.expect(char: Char, offset: Int = 0) {
    val c = peek(offset)
    expect(c == char) { "`$char` expected, got ${if (c != null) "`$c`" else "<EOF>"}" }
}

@InternalSerializationApi
inline fun BufferedCharStream.bail(message: String, range: IntRange): Nothing {
    throw IllegalStateException("${message}\nError occurred here\n${report(start = range.first, end = range.last)}")
}

@InternalSerializationApi
inline fun BufferedCharStream.bail(message: String): Nothing {
    throw IllegalStateException("${message}\nError occurred here\n${report()}")
}
