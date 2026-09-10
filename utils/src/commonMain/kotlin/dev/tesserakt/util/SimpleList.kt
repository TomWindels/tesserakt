package dev.tesserakt.util

import kotlin.jvm.JvmInline

/**
 * A simple list-like data structure, which only supports a small subset of typical list operations, making the internal
 *  implementation potentially simpler
 */
interface SimpleList<T> {

    val size: Int

    /**
     * Inserts the given [element] to the back of this structure, returning its index.
     */
    fun add(element: T): Int

    /**
     * Retrieves the element that was inserted at the given index.
     */
    operator fun get(index: Int): T

    fun clear()

}

@JvmInline
value class SimpleArrayList<T>(val inner: ArrayList<T>): SimpleList<T> {

    override val size: Int
        get() = inner.size

    override fun add(element: T): Int {
        val i = inner.size
        inner.add(element)
        return i
    }

    override operator fun get(index: Int): T {
        return inner[index]
    }

    override fun clear() {
        inner.clear()
    }

}

fun <T> SimpleList(): SimpleList<T> = SimpleArrayList(inner = ArrayList())

fun <T> SimpleList(initialCapacity: Int): SimpleList<T> = SimpleArrayList(inner = ArrayList(initialCapacity))
