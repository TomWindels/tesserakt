package dev.tesserakt.rdf.types.impl

internal fun <T> emptyIterator(): MutableIterator<T> {
    return EmptyIterator
}

private val EmptyIterator = object: MutableIterator<Nothing> {

    override fun remove() {
        throw NoSuchElementException()
    }

    override fun hasNext(): Boolean {
        return false
    }

    override fun next(): Nothing {
        throw NoSuchElementException()
    }

}
