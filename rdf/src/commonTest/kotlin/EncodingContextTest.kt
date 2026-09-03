
import dev.tesserakt.rdf.types.EncodingContext
import dev.tesserakt.rdf.types.MutableEncodingContext
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.impl.ImmutableEncodingContextImpl
import dev.tesserakt.rdf.types.impl.TieredEncodingContextImpl
import kotlin.test.*

class EncodingContextTest {

    private val terms = buildList {
        add(Quad.DefaultGraph)
        repeat(100) {
            add(Quad.NamedTerm("http://example.org/term$it"))
            add(Quad.NamedTerm("http://example.org/term_$it"))
            add(Quad.NamedTerm("https://example.org/term_$it"))
            add(Quad.NamedTerm("https://www.example.org/term$it"))
            add(Quad.Literal(it))
            add(Quad.Literal(it.toDouble()))
            add(Quad.Literal("Number = $it", language = "en"))
            add(Quad.Literal("Number = $it"))
            add(Quad.BlankTerm(it))
        }
    }

    @Test
    fun testRegular() {
        val ctx = ImmutableEncodingContextImpl(terms)
        validate(ctx)
    }

    @Test
    fun testMutable() {
        val ctx = MutableEncodingContext()
        validate(ctx)
    }

    @Test
    fun testTiered() {
        val ctx = TieredEncodingContextImpl()
        validate(ctx)
    }

    private fun validate(context: EncodingContext) {
        // we know that all of our terms are unique, so we want to ensure that all decoded representations are unique
        //  too
        val total = mutableSetOf<Int>()
        terms.forEach { term ->
            val encoded = try {
                 context.encode(term)
            } catch (t: Throwable) {
                fail("Failed to encode $term", t)
            }
            assertNotNull(encoded, "The encoding of $term ended up being `null`!")

            assertFalse { encoded in total }
            total.add(encoded)

            val decoded = try {
                context.decode(encoded)
            } catch (t: Throwable) {
                fail("Failed to decode $encoded (expected $term)", t)
            }
            assertEquals(term, decoded)
        }
        assertEquals(terms.size, total.size)
    }

}
