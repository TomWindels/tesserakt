package dev.tesserakt.util

/**
 * A string pool responsible for storing strings in a compact manner. It returns handles for processed values that
 *  can be used to retrieve the original value using its internal structure. Handles do not leak the entire collection.
 *
 * NOTE: the optimisation is achieved by deduplicating common string start sequences. Strings that do not start with the
 *  same character sequence are therefore not optimised. As a result, it is discouraged to use this for use cases
 *  where string values sharing a character sequence are rare.
 */
interface CommonPrefixStringPool {

    /**
     * Creates a handle for the given [value]. The returned [Handle] can be used to retrieve the original string value.
     * If this [value] has already been added before, no internal changes are made.
     */
    fun createHandle(value: String): Handle

    interface Handle {
        /**
         * Retrieves the original [String] value that was inserted into the associated [CommonPrefixStringPool].
         * Accessing the value after the value or handle have been [remove]d results in a [NoSuchElementException]
         *  if no other instances of this value remain.
         */
        fun retrieve(): String
    }

}

fun CommonPrefixStringPool(): CommonPrefixStringPool = CommonPrefixStringPoolImpl()
