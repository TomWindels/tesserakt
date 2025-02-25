package dev.tesserakt.sparql.runtime.incremental.iterable


object EmptyIterator: Iterator<Nothing> {

    override fun hasNext(): Boolean {
        return false
    }

    override fun next(): Nothing {
        throw NoSuchElementException()
    }

}

inline fun <T> emptyIterator(): Iterator<T> = EmptyIterator
