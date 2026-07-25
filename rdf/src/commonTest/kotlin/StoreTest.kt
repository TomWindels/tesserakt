
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.Store
import dev.tesserakt.rdf.types.factory.IndexedStore
import dev.tesserakt.rdf.types.factory.MutableStore
import dev.tesserakt.rdf.types.factory.ObservableStore
import dev.tesserakt.rdf.types.factory.Store
import kotlin.random.Random
import kotlin.test.*

class StoreTest {

    fun generateData(seed: Int = 0): Set<Quad> {
        val random = Random(seed)
        return buildSet {
            val uris = buildList {
                val domains = listOf(
                    "example.org",
                    "one.example",
                    "www.w3.org",
                    "xmlns.com",
                    "www.perceive.net",
                )
                repeat(1000) {
                    add(Quad.NamedTerm("http://${domains.random(random)}/generated/$it"))
                }
            }
            val blanks = buildList {
                repeat(100) {
                    add(Quad.BlankTerm(id = it))
                }
            }
            val literals = buildList {
                repeat(100) { number ->
                    add(Quad.Literal(number))
                    add(Quad.Literal(random.nextInt()))
                    add(Quad.Literal(random.nextFloat()))
                    add(Quad.Literal(random.nextDouble()))
                }
            }
            repeat(500) {
                add(
                    Quad(
                        s = uris.random(random),
                        p = uris.random(random),
                        o = uris.random(random),
                        g = uris.random(random),
                    )
                )
                add(
                    Quad(
                        s = blanks.random(random),
                        p = uris.random(random),
                        o = blanks.random(random),
                        g = blanks.random(random),
                    )
                )
                add(
                    Quad(
                        s = blanks.random(random),
                        p = uris.random(random),
                        o = blanks.random(random),
                        g = uris.random(random),
                    )
                )
                add(
                    Quad(
                        s = blanks.random(random),
                        p = uris.random(random),
                        o = uris.random(random),
                        g = uris.random(random),
                    )
                )
                add(
                    Quad(
                        s = blanks.random(random),
                        p = uris.random(random),
                        o = blanks.random(random),
                        g = uris.random(random),
                    )
                )
                add(
                    Quad(
                        s = blanks.random(random),
                        p = uris.random(random),
                        o = literals.random(random),
                        g = uris.random(random),
                    )
                )
            }
        }
    }

    @Test
    fun comparison() {
        val data = generateData()
        assertTrue { data.isNotEmpty() }
        val storeA = Store(data)
        assertTrue { storeA.isNotEmpty() }
        val storeB = MutableStore(data)
        assertTrue { storeB.isNotEmpty() }

        assertEquals(data.size, storeA.size)
        assertEquals(data.size, storeB.size)
        assertEquals(storeA.size, storeB.size)
        assertEquals(storeA.context.size, storeB.context.size)

        // reflectivity
        assertEquals(storeA, storeA)
        assertEquals(storeB, storeB)
        // value match
        assertEquals(storeA, storeB)
        // content match with input
        // we put the stores at the 'actual' position as `Store == Set` is supported, but `Set == Store` is
        //  unfortunately not supported
        assertEquals(data, storeA)
        assertEquals(data, storeB)

        assertTrue { storeB.remove(data.first()) }
        // store B is now a subset of store A
        assertNotEquals(storeA, storeB)
        assertTrue { storeA.containsAll(storeB) }
        assertFalse { storeB.containsAll(storeA) }
    }

    @Test
    fun comparisonIndexed() {
        val data = generateData()
        assertTrue { data.isNotEmpty() }
        val storeA = IndexedStore(data)
        assertTrue { storeA.isNotEmpty() }
        val storeB = MutableStore(data)
        assertTrue { storeB.isNotEmpty() }

        assertEquals(data.size, storeA.size)
        assertEquals(data.size, storeB.size)
        assertEquals(storeA.size, storeB.size)
        assertEquals(storeA.context.size, storeB.context.size)

        // reflectivity
        assertEquals(storeA, storeA)
        assertEquals(storeB, storeB)
        // value match
        assertEquals<Store>(storeA, storeB)
        // content match with input
        // we put the stores at the 'actual' position as `Store == Set` is supported, but `Set == Store` is
        //  unfortunately not supported
        assertEquals(data, storeA)
        assertEquals(data, storeB)

        assertTrue { storeB.remove(data.first()) }
        // store B is now a subset of store A
        assertNotEquals<Store>(storeA, storeB)
        assertTrue { storeA.containsAll(storeB) }
        assertFalse { storeB.containsAll(storeA) }
    }

    @Test
    fun comparisonObservable() {
        val data = generateData()
        assertTrue { data.isNotEmpty() }
        val storeA = Store(data)
        assertTrue { storeA.isNotEmpty() }
        val storeB = ObservableStore(data)
        assertTrue { storeB.isNotEmpty() }

        assertEquals(data.size, storeA.size)
        assertEquals(data.size, storeB.size)
        assertEquals(storeA.size, storeB.size)
        assertEquals(storeA.context.size, storeB.context.size)

        // reflectivity
        assertEquals(storeA, storeA)
        assertEquals(storeB, storeB)
        // value match
        assertEquals(storeA, storeB)
        // content match with input
        // we put the stores at the 'actual' position as `Store == Set` is supported, but `Set == Store` is
        //  unfortunately not supported
        assertEquals(data, storeA)
        assertEquals(data, storeB)

        assertTrue { storeB.remove(data.first()) }
        // store B is now a subset of store A
        assertNotEquals(storeA, storeB)
        assertTrue { storeA.containsAll(storeB) }
        assertFalse { storeB.containsAll(storeA) }
    }

}
