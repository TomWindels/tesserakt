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
    val code = peek(offset)
    if (code == -1) {
        bail("Expected `$char`, got <EOF>")
    }
    val actual = Char(code)
    expect(Char(code) == char) { "`$char` expected, got `$actual`" }
}

@OptIn(InternalSerializationApi::class)
inline fun BufferedCharStream.nextIs(char: Char, offset: Int = 0): Boolean {
    val code = peek(offset)
    if (code == -1) {
        return false
    }
    val actual = Char(code)
    return char == actual
}

@OptIn(InternalSerializationApi::class)
inline fun BufferedCharStream.nextIsEofOr(predicate: (Char) -> Boolean): Boolean {
    val code = peek()
    if (code == -1) {
        return true
    }
    val actual = Char(code)
    return predicate(actual)
}

@OptIn(InternalSerializationApi::class)
inline fun BufferedCharStream.peekOrBail(): Char {
    val code = peek()
    if (code == -1) {
        bail("Unexpected EOF reached!")
    }
    return Char(code)
}

@InternalSerializationApi
inline fun BufferedCharStream.bail(message: String, range: IntRange): Nothing {
    throw IllegalStateException("${message}\nError occurred here\n${report(start = range.first, end = range.last)}")
}

@InternalSerializationApi
inline fun BufferedCharStream.bail(message: String): Nothing {
    throw IllegalStateException("${message}\nError occurred here\n${report()}")
}
