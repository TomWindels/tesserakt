
import dev.tesserakt.rdf.ontology.RDF
import dev.tesserakt.rdf.ontology.XSD
import dev.tesserakt.rdf.serialization.DelicateSerializationApi
import dev.tesserakt.rdf.serialization.NTriples
import dev.tesserakt.rdf.serialization.common.collect
import dev.tesserakt.rdf.serialization.common.deserialize
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.Store
import dev.tesserakt.rdf.types.consume
import kotlin.test.Test
import kotlin.test.assertTrue

class NTriplesTest {

    @Test
    fun deserialization1() = test("""
        _:subject1 <http://an.example/predicate1> "object1" . # comments here
        # or on a line by themselves
        _:subject2 <http://an.example/predicate2> "object2" .
    """) { store ->
        store.all { quad -> quad.o.let { o -> o is Quad.Literal && o.type == XSD.string } }
    }

    @Test
    fun deserialization2() = test("""
        <http://example.org/show/218> <http://www.w3.org/2000/01/rdf-schema#label> "That Seventies Show"^^<http://www.w3.org/2001/XMLSchema#string> . # literal with XML Schema string datatype
        <http://example.org/show/218> <http://www.w3.org/2000/01/rdf-schema#label> "That Seventies Show" . # same as above
        <http://example.org/show/218> <http://example.org/show/localName> "That Seventies Show"@en . # literal with a language tag
        <http://example.org/show/218> <http://example.org/show/localName> "Cette Série des Années Septante"@fr-be .  # literal outside of ASCII range with a region subtag
        <http://example.org/#spiderman> <http://example.org/text> "This is a multi-line\nliteral with many quotes (\"\"\"\"\")\nand two apostrophes ('')." .
        <http://en.wikipedia.org/wiki/Helium> <http://example.org/elements/atomicNumber> "2"^^<http://www.w3.org/2001/XMLSchema#integer> . # xsd:integer
        <http://en.wikipedia.org/wiki/Helium> <http://example.org/elements/specificGravity> "1.663E-4"^^<http://www.w3.org/2001/XMLSchema#double> .     # xsd:double
    """) { store ->
        // one duplicate entry, so 6 distinct items expected
        store.size == 6 &&
        store.all { it.o is Quad.Literal } &&
        store.count { it.o.let { it is Quad.Literal && it.type == RDF.langString } } == 2
    }

    @Test
    fun deserialization3() = test("""
        _:alice <http://xmlns.com/foaf/0.1/knows> _:bob .
        _:bob <http://xmlns.com/foaf/0.1/knows> _:alice .
    """) { store ->
        store.size == 2 && store.let {
            val first = store.first()
            val second = store.last()
            first.s == second.o && first.o == second.s
        }
    }

    @OptIn(DelicateSerializationApi::class)
    private inline fun test(nt: String, block: (Store) -> Boolean) {
        val deserialized = NTriples.deserialize(nt).consume()
        println(deserialized)
        val serialized = NTriples.serialize(deserialized).collect()
        println(serialized)
        assertTrue(block(deserialized))
    }

}
