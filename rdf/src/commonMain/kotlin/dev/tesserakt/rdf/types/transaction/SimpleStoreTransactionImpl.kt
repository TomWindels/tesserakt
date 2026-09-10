package dev.tesserakt.rdf.types.transaction

import dev.tesserakt.rdf.types.MutableStore
import dev.tesserakt.rdf.types.Quad

internal class SimpleStoreTransactionImpl(
    private val parent: MutableStore,
): StoreTransaction() {

    override fun add(quad: Quad) {
        // no rollback support
        parent.add(quad)
    }

    override fun remove(quad: Quad) {
        // no rollback support
        parent.remove(quad)
    }

    override fun commit() {
        // no-op
    }

}
