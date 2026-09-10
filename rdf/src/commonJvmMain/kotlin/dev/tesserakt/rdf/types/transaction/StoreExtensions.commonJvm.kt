package dev.tesserakt.rdf.types.transaction

import dev.tesserakt.concurrent.ThreadedTaskRunner
import dev.tesserakt.concurrent.globalTaskRunner
import dev.tesserakt.rdf.types.MutableStore
import dev.tesserakt.rdf.types.impl.MutableStoreImpl
import dev.tesserakt.rdf.types.impl.ObservableStoreImpl
import java.util.concurrent.ConcurrentHashMap

actual fun MutableStore.newTransaction(): StoreTransaction {
    val runner = globalTaskRunner
    if (runner !is ThreadedTaskRunner || runner.threadCount <= 1) {
        return SimpleStoreTransactionImpl(this)
    }
    if (this is MutableStoreImpl && this.isConcurrent()) {
        return ConcurrentStoreTransactionImpl(parent = this, runner = runner)
    }
    if (this is ObservableStoreImpl) {
        // the store is already created with concurrency support, so no additional checks required
        return ConcurrentObservableStoreTransactionImpl(parent = this, runner = runner)
    }
    return SimpleStoreTransactionImpl(this)
}

private fun MutableStoreImpl.isConcurrent(): Boolean {
    // we assume that a concurrent set of quads is only constructed if the associated context is also made with
    //  concurrency support, as there's otherwise no point
    return quads is ConcurrentHashMap.KeySetView<*, *> // return type of `ConcurrentSet()` on the JVM
}
