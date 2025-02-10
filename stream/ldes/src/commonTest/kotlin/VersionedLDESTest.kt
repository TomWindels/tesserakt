
import dev.tesserakt.rdf.dsl.RDF_DSL
import dev.tesserakt.rdf.dsl.buildStore
import dev.tesserakt.rdf.dsl.extractPrefixes
import dev.tesserakt.rdf.ontology.RDF
import dev.tesserakt.rdf.ontology.XSD
import dev.tesserakt.rdf.trig.serialization.TriGSerializer
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.Quad.Companion.asLiteralTerm
import dev.tesserakt.rdf.types.Quad.Companion.asNamedTerm
import dev.tesserakt.stream.ldes.StreamTransform
import dev.tesserakt.stream.ldes.VersionedLinkedDataEventStream
import dev.tesserakt.stream.ldes.ontology.LDES
import dev.tesserakt.stream.ldes.ontology.TREE
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

class VersionedLDESTest {

    @Test
    fun basicVersionedLDES() {
        val ldes = VersionedLinkedDataEventStream.initialise(
            identifier = "myLDES".asNamedTerm(),
            timestampPath = "http://purl.org/dc/elements/1.1/modified".asNamedTerm(), // dc:modified
            versionOfPath = "http://purl.org/dc/elements/1.1/isVersionOf".asNamedTerm(), // dc:isVersionOf
            transform = StreamTransform.GraphBased
        )
        println(TriGSerializer.serialize(ldes.store))
    }

    @Test
    fun mutatedVersionedLDES() {
        val ldes = VersionedLinkedDataEventStream.initialise(
            identifier = "myLDES".asNamedTerm(),
            timestampPath = DC.modified,
            versionOfPath = DC.isVersionOf,
            transform = StreamTransform.GraphBased
        )
        val one: RDF_DSL = {
            val example = prefix("ex", "http://example.org/")
            val document = prefix("", "http://example-document.org/")

            document("s1") has example("name") being document("Test")
            document("s1") has example("property") being "Value".asLiteralTerm()

            document("Test") has example("value") being "abc".asLiteralTerm()
        }
        val two: RDF_DSL = {
            val example = prefix("ex", "http://example.org/")
            val document = prefix("", "http://example-document.org/")

            document("s2") has example("name") being document("Test2")
            document("s2") has example("property") being "Value".asLiteralTerm()
            document("s2") has example("property") being "Additional".asLiteralTerm()

            document("Test2") has example("value") being "def".asLiteralTerm()
        }
        val two2: RDF_DSL = {
            val example = prefix("ex", "http://example.org/")
            val document = prefix("", "http://example-document.org/")

            document("s2") has example("name") being "Test2".asLiteralTerm()
            document("s2") has example("property") being "Additional".asLiteralTerm()

            document("Test2") has example("value") being "def".asLiteralTerm()
        }
        ldes.add(
            base = "s1".asNamedTerm(),
            version = (Clock.System.now() - 30.seconds).asLiteral(),
            data = buildStore(block = one).toList()
        )
        ldes.add(
            base = "s2".asNamedTerm(),
            version = (Clock.System.now() - 20.seconds).asLiteral(),
            data = buildStore(block = two).toList()
        )
        ldes.add(
            base = "s2".asNamedTerm(),
            version = (Clock.System.now() - 10.seconds).asLiteral(),
            data = buildStore(block = two2).toList()
        )
        println(TriGSerializer.serialize(
            store = ldes.store,
            prefixes =
                TriGSerializer.Prefixes(one.extractPrefixes()) +
                TriGSerializer.Prefixes(two.extractPrefixes()) +
                TriGSerializer.Prefixes(two2.extractPrefixes()) +
                TriGSerializer.Prefixes.createFor(DC, TREE, LDES, RDF)
        ))
    }

}

private fun Instant.asLiteral() = Quad.Literal(value = toString(), type = XSD.date)
