import dev.tesserakt.concurrent.ConcurrencyMode
import dev.tesserakt.concurrent.MultiThreaded
import dev.tesserakt.concurrent.SingleThreaded
import dev.tesserakt.concurrent.set
import dev.tesserakt.rdf.types.MutableStore
import dev.tesserakt.rdf.types.ObservableStore
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.impl.MutableStoreImpl
import dev.tesserakt.rdf.types.transaction.ConcurrentObservableStoreTransactionImpl
import dev.tesserakt.rdf.types.transaction.ConcurrentStoreTransactionImpl
import dev.tesserakt.rdf.types.transaction.SimpleStoreTransactionImpl
import dev.tesserakt.rdf.types.transaction.transaction
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class TransactionTest {

    private val data = run {
        val rng = Random(1)
        fun nextTerm(): Quad.NamedTerm {
            val length = rng.nextInt(12, 24)
            val chars = CharArray(length) {
                val code = rng.nextInt(24) + 'a'.code
                Char(code)
            }
            val uri = String(chars)
            return Quad.NamedTerm(uri)
        }
        buildSet {
            repeat(750_000) {
                add(Quad(nextTerm(), nextTerm(), nextTerm()))
            }
        }.also { println("Generated ${it.size} triples!") }
    }

    @Test
    fun simple() {
        ConcurrencyMode.set(SingleThreaded)
        val store = MutableStore()
        store.transaction {
            assert(this is SimpleStoreTransactionImpl)
            addAll(data)
        }
        assert(data.size == store.size)
//        assert(store.all { it in data })
//        assert(data.all { it in store })
    }

    @Test
    fun simpleObservable() {
        ConcurrencyMode.set(SingleThreaded)
        val store = ObservableStore()
        val listener = object: ObservableStore.Listener {
            val received = mutableSetOf<Quad>()
            override fun onQuadAdded(quad: Quad) {
                received.add(quad)
            }
        }
        store.addListener(listener)
        store.transaction {
            assert(this is SimpleStoreTransactionImpl)
            addAll(data)
        }
//        assert(store.all { it in data })
//        assert(data.all { it in store })
//        assert(listener.received.all { it in data })
        assert(data.size == store.size)
        assertEquals(data.size, listener.received.size)
        assertEquals(store.size, listener.received.size)
    }

    @Test
    fun concurrent() {
        ConcurrencyMode.set(MultiThreaded)
        val store = MutableStoreImpl.withConcurrencySupport()
        store.transaction {
            assert(this is ConcurrentStoreTransactionImpl)
            addAll(data)
        }
        assert(data.size == store.size)
//        assert(store.all { it in data })
//        assert(data.all { it in store })
    }

    @Test
    fun concurrentObservable() {
        ConcurrencyMode.set(MultiThreaded)
        val store = ObservableStore()
        val listener = object: ObservableStore.Listener {
            val received = mutableSetOf<Quad>()
            override fun onQuadAdded(quad: Quad) {
                received.add(quad)
            }
        }
        store.addListener(listener)
        store.transaction {
            assert(this is ConcurrentObservableStoreTransactionImpl)
            addAll(data)
        }
//        assert(store.all { it in data })
//        assert(data.all { it in store })
//        assert(listener.received.all { it in data })
        assert(data.size == store.size)
        assertEquals(data.size, listener.received.size)
        assertEquals(store.size, listener.received.size)
    }

}
