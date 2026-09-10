package dev.tesserakt.rdf.types.transaction

import dev.tesserakt.rdf.types.Quad

abstract class StoreTransaction {

    /**
     * Adds the [quad] to the associated [dev.tesserakt.rdf.types.MutableStore] instance.
     */
    abstract fun add(quad: Quad)

    /**
     * [add]s all [quads] from the associated [dev.tesserakt.rdf.types.MutableStore] instance.
     */
    open fun addAll(quads: Iterable<Quad>) {
        quads.forEach { add(it) }
    }

    /**
     * Removes the [quad] from the associated [dev.tesserakt.rdf.types.MutableStore] instance.
     */
    abstract fun remove(quad: Quad)

    /**
     * [remove]s all [quads] from the associated [dev.tesserakt.rdf.types.MutableStore] instance.
     */
    open fun removeAll(quads: Iterable<Quad>) {
        quads.forEach { remove(it) }
    }

    /**
     * Finishes the transaction. Note that by default this is largely a no-op, merely cleaning up any resources that
     *  may have been used to facilitate the transaction process, semantically similar to [AutoCloseable.close].
     *
     * If changes are expected to be infrequent or slow, it is recommended to start many small transactions instead of
     *  maintaining a single long-lasting one.
     *
     * IMPORTANT: this has to be called exactly once!
     */
    abstract fun commit()

}
