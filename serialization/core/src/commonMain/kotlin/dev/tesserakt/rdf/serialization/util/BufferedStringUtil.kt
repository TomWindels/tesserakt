package dev.tesserakt.rdf.serialization.util

import dev.tesserakt.rdf.serialization.InternalSerializationApi
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract


@InternalSerializationApi
inline fun BufferedString.consumeWhile(invalid: (Char) -> Boolean, predicate: (Char) -> Boolean): String {
    val result = StringBuilder()
    var c = peek() ?: throw NoSuchElementException("Unexpected EOF reached! Last received data: `$result`")
    while (true) {
        if (invalid(c)) {
            // we haven't consumed the bad character yet, but we also have to highlight it, so range ends at `1`
            bail("Invalid character encountered: `$c`", -result.length .. 1)
        }
        if (!predicate(c)) {
            return result.toString()
        }
        result.append(c)
        consume()
        c = peek()
            ?: throw NoSuchElementException("Unexpected EOF reached! Last received data: `$result`")
    }
}

@InternalSerializationApi
inline fun BufferedString.consumeWhile(predicate: (Char) -> Boolean): String {
    val result = StringBuilder()
    var c = peek() ?: throw NoSuchElementException("Unexpected EOF reached! Last received data: `$result`")
    while (predicate(c)) {
        result.append(peek())
        consume()
        c = peek() ?: throw NoSuchElementException("Unexpected EOF reached! Last received data: `$result`")
    }
    return result.toString()
}

@InternalSerializationApi
@OptIn(ExperimentalContracts::class)
inline fun BufferedString.expect(condition: Boolean, message: () -> String) {
    contract {
        returns() implies condition
    }
    if (!condition) {
        val msg = message().ifBlank { "No further information available." }
        bail(msg)
    }
}

@OptIn(InternalSerializationApi::class)
inline fun BufferedString.expect(char: Char, offset: Int = 0) {
    val c = peek(offset)
    expect(c == char) { "`$char` expected, got ${if (c != null) "`$c`" else "<EOF>"}" }
}

@InternalSerializationApi
inline fun BufferedString.bail(message: String, range: IntRange): Nothing {
    throw IllegalStateException("${message}\nError occurred here\n${report(start = range.first, end = range.last)}")
}

@InternalSerializationApi
inline fun BufferedString.bail(message: String): Nothing {
    throw IllegalStateException("${message}\nError occurred here\n${report()}")
}
