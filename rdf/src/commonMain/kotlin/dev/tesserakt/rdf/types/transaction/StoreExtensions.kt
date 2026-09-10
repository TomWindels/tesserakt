package dev.tesserakt.rdf.types.transaction

import dev.tesserakt.rdf.types.MutableStore

/**
 * A convenient way of running a [StoreTransaction].
 *
 * NOTE:
 *  This method already calls [StoreTransaction.commit]! Doing so inside the [block] is therefore an error!
 *
 * NOTE:
 *  The [StoreTransaction] instance is no longer valid after the [block] finished. Maintaining a reference to it is
 *  therefore an error!
 */
inline fun MutableStore.transaction(block: StoreTransaction.() -> Unit) {
    val transaction = newTransaction()
    try {
        block(transaction)
    } finally {
        // making sure we clean up after ourselves
        transaction.commit()
    }
}

/**
 * Creates a [StoreTransaction] that can be used to alter the store contents.
 *
 * IMPORTANT: [StoreTransaction.commit] has to be called when the transaction is no longer required ()
 */
expect fun MutableStore.newTransaction(): StoreTransaction
