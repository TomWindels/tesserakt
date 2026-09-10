package dev.tesserakt.rdf.types.transaction

import dev.tesserakt.rdf.types.MutableStore

actual fun MutableStore.newTransaction(): StoreTransaction {
    return SimpleStoreTransactionImpl(this)
}
