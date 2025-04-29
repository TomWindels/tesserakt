
import dev.tesserakt.rdf.dsl.RDF_DSL
import dev.tesserakt.rdf.dsl.buildStore
import dev.tesserakt.rdf.dsl.extractPrefixes
import dev.tesserakt.rdf.ontology.RDF
import dev.tesserakt.rdf.ontology.XSD
import dev.tesserakt.rdf.serialization.common.Prefixes
import dev.tesserakt.rdf.trig.serialization.TriGSerializer
import dev.tesserakt.rdf.trig.serialization.prefixes
import dev.tesserakt.rdf.trig.serialization.prettyFormatting
import dev.tesserakt.rdf.trig.serialization.trig
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.Quad.Companion.asLiteralTerm
import dev.tesserakt.rdf.types.Quad.Companion.asNamedTerm
import dev.tesserakt.rdf.types.Store
import dev.tesserakt.stream.ldes.StreamTransform
import dev.tesserakt.stream.ldes.VersionedLinkedDataEventStream
import dev.tesserakt.stream.ldes.ontology.DC
import dev.tesserakt.stream.ldes.ontology.LDES
import dev.tesserakt.stream.ldes.ontology.TREE
import dev.tesserakt.util.toTruncatedString
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertFails
import kotlin.test.fail
import kotlin.time.Duration.Companion.seconds

class VersionedLDESTest {

    @Test
    fun basicVersionedLDES() {
        val ldes = VersionedLinkedDataEventStream.initialise(
            identifier = "myLDES".asNamedTerm(),
            timestampPath = DC.modified,
            versionOfPath = DC.isVersionOf,
            transform = StreamTransform.GraphBased
        )
        println(TriGSerializer.serialize(ldes))
    }

    @Test
    fun invalidLDES() {
        assertFails {
            VersionedLinkedDataEventStream(
                identifier = "myLDES".asNamedTerm(),
                transform = StreamTransform.GraphBased,
                store = Store()
            )
        }
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
            baseVersion = "s1".asNamedTerm(),
            timestamp = (Clock.System.now() - 30.seconds).asLiteral(),
            data = buildStore(block = one)
        )
        ldes.add(
            baseVersion = "s2".asNamedTerm(),
            timestamp = (Clock.System.now() - 20.seconds).asLiteral(),
            data = buildStore(block = two)
        )
        ldes.add(
            baseVersion = "s2".asNamedTerm(),
            timestamp = (Clock.System.now() - 10.seconds).asLiteral(),
            data = buildStore(block = two2)
        )
        val serializer = trig {
            prettyFormatting {
                prefixes {
                    putAll(one.extractPrefixes())
                    putAll(two.extractPrefixes())
                    putAll(two2.extractPrefixes())
                    putAll(Prefixes(DC, TREE, LDES, RDF, XSD))
                }
            }
        }
        println(serializer.serialize(data = ldes))
    }

    @Test
    fun consumeLDES() {
        val ldes = VersionedLinkedDataEventStream.initialise(
            identifier = "myLDES".asNamedTerm(),
            timestampPath = DC.modified,
            versionOfPath = DC.isVersionOf,
            transform = StreamTransform.GraphBased
        )
        val now = Clock.System.now()
        val data = buildStore { "s1".asNamedTerm() has RDF.type being "Test".asNamedTerm() }
        ldes.add(
            baseVersion = "s1".asNamedTerm(),
            timestamp = (now - 10.seconds).asLiteral(),
            data = data
        )
        assertStoreContentEqual(emptySet(), ldes.read((now - 20.seconds).asLiteral()))
        assertStoreContentEqual(data, ldes.read((now - 5.seconds).asLiteral()))
    }

    @Test
    fun consumeVersionedLDES() {
        val ldes = VersionedLinkedDataEventStream.initialise(
            identifier = "myLDES".asNamedTerm(),
            timestampPath = DC.modified,
            versionOfPath = DC.isVersionOf,
            transform = StreamTransform.GraphBased
        )
        val now = Clock.System.now()

        val pre_t1 = (now - 11.seconds).asLiteral()
        val t1 = (now - 10.seconds).asLiteral()
        val pre_t2 = (now - 9.seconds).asLiteral()
        val t2 = (now - 8.seconds).asLiteral()
        val pre_t3 = (now - 7.seconds).asLiteral()
        val t3 = (now - 6.seconds).asLiteral()
        val pre_t4 = (now - 5.seconds).asLiteral()

        val data1 = buildStore { "s1".asNamedTerm() has RDF.type being "Test".asNamedTerm() }
        val data1v2 = buildStore { "s1".asNamedTerm() has RDF.type being "Test2".asNamedTerm() }
        val data2 = buildStore { "s2".asNamedTerm() has RDF.type being "Test".asNamedTerm() }

        ldes.add(
            baseVersion = "s1".asNamedTerm(),
            timestamp = t1,
            data = data1
        )
        ldes.add(
            baseVersion = "s2".asNamedTerm(),
            timestamp = t2,
            data = data2
        )
        ldes.add(
            baseVersion = "s1".asNamedTerm(),
            timestamp = t3,
            data = data1v2
        )
        val serializer = trig {
            prettyFormatting {
                prefixes {
                    putAll(Prefixes(DC, TREE, LDES, RDF, XSD))
                }
            }
        }
        println(serializer.serialize(data = ldes))
        assertStoreContentEqual(emptySet(), ldes.read(pre_t1))
        assertStoreContentEqual(data1, ldes.read(pre_t2))
        assertStoreContentEqual(data1 + data2, ldes.read(pre_t3))
        assertStoreContentEqual(data1v2 + data2, ldes.read(pre_t4))
    }

    private fun assertStoreContentEqual(expected: Set<Quad>, actual: Set<Quad>) {
        val missing = expected - actual
        val superfluous = actual - expected
        if (missing.isNotEmpty() || superfluous.isNotEmpty()) {
            fail("Store content mismatch!\nMissing quads: ${missing.toTruncatedString(200)}\nUnexpected quads: ${superfluous.toTruncatedString(200)}")
        }
    }

}

private fun Instant.asLiteral() = Quad.Literal(value = toString(), type = XSD.date)
