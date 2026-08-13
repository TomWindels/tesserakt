
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.factory.indexedStoreOf
import kotlin.test.*

class IndexedStoreTest {

    @Test
    fun empty() {
        val store = indexedStoreOf()
        assertTrue(!store.iter(null, null, null, null).hasNext())
        assertTrue(!store.iter(Quad.NamedTerm("subject"), Quad.NamedTerm("predicate"), Quad.NamedTerm("object"), Quad.NamedTerm("object")).hasNext())
    }

    @Test
    fun single() {
        val s = Quad.NamedTerm("subject")
        val p = Quad.NamedTerm("predicate")
        val o = Quad.NamedTerm("object")
        val g = Quad.NamedTerm("graph")

        fun validateLookup(iter: Iterator<Quad>) {
            assertTrue(iter.hasNext())
            assertEquals(Quad(s, p, o, g), iter.next())
            assertTrue(!iter.hasNext())
            assertFails { iter.next() }
        }

        fun validateEmptyLookup(iter: Iterator<Quad>) {
            assertTrue(!iter.hasNext())
            assertFails { iter.next() }
        }

        val store = indexedStoreOf(
            Quad(s, p, o, g)
        )
        validateLookup(store.iter(null, null, null, null))
        validateLookup(store.iter(s, null, null, null))
        validateLookup(store.iter(null, p, null, null))
        validateLookup(store.iter(null, null, o, null))
        validateLookup(store.iter(null, null, null, g))
        validateLookup(store.iter(s, p, null, null))
        validateLookup(store.iter(null, p, o, null))
        validateLookup(store.iter(null, null, o, g))
        validateLookup(store.iter(null, p, o, g))
        validateLookup(store.iter(s, null, o, g))
        validateLookup(store.iter(s, p, null, g))
        validateLookup(store.iter(s, p, o, null))
        validateLookup(store.iter(s, p, o, g))

        validateEmptyLookup(store.iter(s, s, o, g))
        validateEmptyLookup(store.iter(s, p, p, g))
        validateEmptyLookup(store.iter(s, p, p, o))
        validateEmptyLookup(store.iter(p, s, o, o))
    }

    @Test
    fun multiple() {
        val s = Quad.NamedTerm("subject")
        val p = Quad.NamedTerm("predicate")
        val o1 = Quad.NamedTerm("object1")
        val o2 = Quad.NamedTerm("object2")
        val o3 = Quad.NamedTerm("object3")
        val g = Quad.NamedTerm("graph")

        fun validateLookup(iter: Iterator<Quad>, items: Set<Quad>) {
            val remaining = items.toMutableSet()
            repeat(items.size) {
                assertTrue(iter.hasNext())
                val next = iter.next()
                assertContains(remaining, next)
                remaining.remove(next)
            }
            assertTrue(!iter.hasNext())
            assertFails { iter.next() }
        }

        fun validateEmptyLookup(iter: Iterator<Quad>) {
            validateLookup(iter, emptySet())
        }

        val store = indexedStoreOf(
            Quad(s, p, o1, g), Quad(s, p, o2, g), Quad(s, p, o3, g),
        )
        validateLookup(store.iter(null, null, null, null), setOf(
            Quad(s, p, o1, g), Quad(s, p, o2, g), Quad(s, p, o3, g),
        ))
        validateLookup(store.iter(s, null, null, null), setOf(
            Quad(s, p, o1, g), Quad(s, p, o2, g), Quad(s, p, o3, g),
        ))
        validateLookup(store.iter(null, p, null, null), setOf(
            Quad(s, p, o1, g), Quad(s, p, o2, g), Quad(s, p, o3, g),
        ))
        validateLookup(store.iter(null, null, o1, null), setOf(
            Quad(s, p, o1, g)
        ))
        validateLookup(store.iter(null, null, null, g), setOf(
            Quad(s, p, o1, g), Quad(s, p, o2, g), Quad(s, p, o3, g),
        ))
        validateLookup(store.iter(s, p, null, null), setOf(
            Quad(s, p, o1, g), Quad(s, p, o2, g), Quad(s, p, o3, g),
        ))
        validateLookup(store.iter(null, p, o1, null), setOf(
            Quad(s, p, o1, g),
        ))
        validateLookup(store.iter(null, null, o1, g), setOf(
            Quad(s, p, o1, g),
        ))
        validateLookup(store.iter(null, p, o1, g), setOf(
            Quad(s, p, o1, g),
        ))
        validateLookup(store.iter(s, null, o1, g), setOf(
            Quad(s, p, o1, g),
        ))
        validateLookup(store.iter(s, p, null, g), setOf(
            Quad(s, p, o1, g), Quad(s, p, o2, g), Quad(s, p, o3, g),
        ))
        validateLookup(store.iter(s, p, o1, null), setOf(
            Quad(s, p, o1, g),
        ))
        validateLookup(store.iter(s, p, o1, g), setOf(
            Quad(s, p, o1, g),
        ))

        validateEmptyLookup(store.iter(s, s, o1, g))
        validateEmptyLookup(store.iter(s, p, p, g))
        validateEmptyLookup(store.iter(s, p, p, o1))
        validateEmptyLookup(store.iter(p, s, o1, o2))
    }

}
