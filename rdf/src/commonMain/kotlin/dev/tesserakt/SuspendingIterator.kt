package dev.tesserakt

/**
 * A variant of [Iterator], which can suspend between obtaining new elements.
 */
interface SuspendingIterator<out T> {

    suspend fun hasNext(): Boolean

    suspend fun next(): T

}
