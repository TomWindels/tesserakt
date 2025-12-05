package dev.tesserakt.rdf.serialization.core

import dev.tesserakt.rdf.serialization.InternalSerializationApi

@InternalSerializationApi
interface DataStream: AutoCloseable {

    /**
     * Reads `0..`[count] characters, inserting into the [target] using platform specific methods for every
     *  character read. Returns the character count read, or a negative number of the end has been reached and no
     *  characters have been added.
     */
    fun read(target: CharArray, offset: Int, count: Int): Int

}
