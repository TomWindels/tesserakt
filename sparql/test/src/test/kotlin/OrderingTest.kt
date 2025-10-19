
import dev.tesserakt.rdf.ontology.XSD
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.Quad.Companion.asLiteralTerm
import dev.tesserakt.sparql.runtime.query.select.OrderComparator
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

class OrderingTest {

    @Test
    fun termTypeMismatch() {
        val terms = listOf(
            Quad.BlankTerm(0),
            Quad.BlankTerm(1),
            Quad.NamedTerm("http://script.example/Latin"),
            Quad.NamedTerm("http://script.example/Кириллица"),
            Quad.NamedTerm("http://script.example/漢字"),
            Quad.Literal("http://script.example/Latin", XSD.string)
        )
        fun check(list: List<Quad.Element>) {
            val iter = list.iterator()
            // the blank nodes should be the first two elements
            assertIs<Quad.BlankTerm>(iter.next())
            assertIs<Quad.BlankTerm>(iter.next())
            // the other items have to match with the [terms] list above in order 1:1
            val ref = terms.iterator().also { it.next(); it.next() }
            while (ref.hasNext()) {
                assertEquals(ref.next(), iter.next())
            }
        }
        // sanity check
        check(terms)
        // randomising the list of reference terms, and feeding it to the comparator we're testing
        val random = Random(0)
        repeat(100) {
            val reordered = terms.shuffled(random).sortedWith(OrderComparator)
            check(reordered)
        }
    }

    @Test
    fun numericalComparison() {
        val terms = listOf(
            1.asLiteralTerm(),
            1.2f.asLiteralTerm(),
            2L.asLiteralTerm(),
            5e10.asLiteralTerm(),
        )
        // randomising the list of reference terms, and feeding it to the comparator we're testing
        val random = Random(0)
        repeat(100) {
            val reordered = terms.shuffled(random).sortedWith(OrderComparator)
            assertContentEquals(terms, reordered)
        }
    }

    @Test
    fun datetimeComparison() {
        val terms = listOf(
            Quad.Literal("1970-01-01T00:00:00", XSD.dateTime),
            Quad.Literal("1970-01-01T01:00:00", XSD.dateTime),
            Quad.Literal("2000-01-01T01:00:00", XSD.dateTime),
        )
        // randomising the list of reference terms, and feeding it to the comparator we're testing
        val random = Random(0)
        repeat(100) {
            val reordered = terms.shuffled(random).sortedWith(OrderComparator)
            assertContentEquals(terms, reordered)
        }
    }

}
