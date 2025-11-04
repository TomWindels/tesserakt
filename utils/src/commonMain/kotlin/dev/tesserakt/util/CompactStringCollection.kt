package dev.tesserakt.util

/**
 * A collection responsible for storing strings in a compact manner. It returns a handle for every value inserted that
 *  can be used to retrieve the value using its internal structure, and ensures that handles do not leak the entire
 *  collection.
 */
interface CompactStringCollection {

    /**
     * Adds the given [value] to this collection, returning a [Handle] that can be used to retrieve the string value.
     * If this [value] has already been added before, nothing changes.
     */
    fun add(value: String): Handle

    interface Handle {
        /**
         * Retrieves the original [String] value that was inserted into the associated [CompactStringCollection].
         * Accessing the value after the value or handle have been [remove]d results in a [NoSuchElementException]
         *  if no other instances of this value remain.
         */
        fun retrieve(): String
    }

}

fun CompactStringCollection(): CompactStringCollection = CompactStringCollectionImpl()
