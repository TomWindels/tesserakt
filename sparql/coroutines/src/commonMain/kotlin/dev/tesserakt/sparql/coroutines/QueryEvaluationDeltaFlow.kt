package dev.tesserakt.sparql.coroutines

import dev.tesserakt.rdf.types.ConcurrentMutableStore
import dev.tesserakt.rdf.types.MutableStore
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.Bindings
import dev.tesserakt.sparql.Query
import dev.tesserakt.sparql.runtime.createState
import dev.tesserakt.sparql.runtime.evaluation.DataAddition
import dev.tesserakt.sparql.runtime.evaluation.DataDeletion
import dev.tesserakt.sparql.runtime.query.QueryState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlin.jvm.JvmName

@JvmName("queryQuadFlow")
fun Flow<Quad>.query(query: Query<Bindings>): Flow<Delta<Bindings>> {
    @Suppress("UNCHECKED_CAST")
    val state = query.ast.createState() as QueryState<Bindings, *>
    val processor = state.Processor()
    return transform { quad ->
        processor.process(DataAddition(quad)).forEach { delta ->
            emit(Delta(delta))
        }
    }.onStart {
        processor.state().forEach { initial ->
            emit(Delta.Addition(initial))
        }
    }
}

@JvmName("queryDeltaQuadFlow")
fun Flow<Delta<Quad>>.query(query: Query<Bindings>): Flow<Delta<Bindings>> {
    @Suppress("UNCHECKED_CAST")
    val state = query.ast.createState() as QueryState<Bindings, *>
    val processor = state.Processor()
    return transform { delta ->
        processor.process(DataDelta(delta)).forEach { resultChange ->
            emit(Delta(resultChange))
        }
    }.onStart {
        processor.state().forEach { initial ->
            emit(Delta.Addition(initial))
        }
    }
}

@DelicateStoreApi
fun ConcurrentMutableStore.observeAsFlow(capacity: Int = Channel.UNLIMITED): Flow<Delta<Quad>> {
    // we have to emit all items we already have directly into this channel first, so concurrent modifications
    //  whilst suspended are not possible (we're never suspending)
    val channel = Channel<Delta<Quad>>(capacity.coerceAtLeast(size))
    forEach {
        // these should all succeed
        channel.trySend(Delta.Addition(it))
    }
    val listener = object : MutableStore.Listener {
        override fun onQuadAdded(quad: Quad) {
            check(channel.trySend(Delta.Addition(quad)).isSuccess) { "Buffer is overloaded (too much backpressure)!" }
        }

        override fun onQuadRemoved(quad: Quad) {
            check(channel.trySend(Delta.Deletion(quad)).isSuccess) { "Buffer is overloaded (too much backpressure)!" }
        }
    }
    addListener(listener)
    return channel
        .receiveAsFlow()
        .onCompletion { removeListener(listener) }
}

fun ConcurrentMutableStore.queryAsFlow(query: Query<Bindings>): Flow<Delta<Bindings>> {
    @Suppress("UNCHECKED_CAST")
    val state = query.ast.createState() as QueryState<Bindings, *>
    val processor = state.Processor()
    return callbackFlow {
        // creating the listener object, but not attaching it just yet
        // ensuring the listener's callbacks are executed in separate jobs, so [this] MutableStore is unaffected
        //  by downstream consumers
        val listener = object: MutableStore.Listener {
            override fun onQuadAdded(quad: Quad) {
                processor.process(DataAddition(quad)).forEach {
                    trySend(Delta(it))
                }
            }

            override fun onQuadRemoved(quad: Quad) {
                processor.process(DataDeletion(quad)).forEach {
                    trySend(Delta(it))
                }
            }
        }
        // now listening to changes made to the store itself
        this@queryAsFlow.addListener(listener)
        // and removing the listener again when we're no longer required
        awaitClose {
            this@queryAsFlow.removeListener(listener)
        }
    }.onStart {
        // propagating initial state downstream first
        processor.state().forEach { initial ->
            emit(Delta.Addition(initial))
        }
        // FIXME!!!!!!!!!!!
        //  due to `emit` halting execution, the listener might be registered too late! add test for this first!
        // updating the state with the existing data next
        val pending = mutableListOf<Delta<Bindings>>()
        this@queryAsFlow.forEach { existing ->
            processor.process(DataAddition(existing)).forEach {
                pending.add(Delta(it))
            }
        }
        pending.forEach { emit(it) }
    }
}
