
import dev.tesserakt.rdf.dsl.RDF_DSL
import dev.tesserakt.rdf.dsl.buildStore
import dev.tesserakt.rdf.dsl.extractPrefixes
import dev.tesserakt.rdf.ontology.RDF
import dev.tesserakt.rdf.ontology.XSD
import dev.tesserakt.rdf.serialization.common.Prefixes
import dev.tesserakt.rdf.serialization.common.serializer
import dev.tesserakt.rdf.serialization.trig.TriG
import dev.tesserakt.rdf.serialization.trig.usePrettyFormatting
import dev.tesserakt.rdf.serialization.trig.withPrefixes
import dev.tesserakt.rdf.types.*
import dev.tesserakt.rdf.types.Quad.NamedTerm
import dev.tesserakt.stream.ldes.IndexedVersionedLinkedDataEventStream
import dev.tesserakt.stream.ldes.MutableVersionedLinkedDataEventStream
import dev.tesserakt.stream.ldes.StreamTransform
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
        val ldes = IndexedVersionedLinkedDataEventStream.initialise(
            identifier = NamedTerm("myLDES"),
            timestampPath = DC.modified,
            versionOfPath = DC.isVersionOf,
            transform = StreamTransform.GraphBased
        )
        println(serializer(TriG).serialize(ldes))
    }

    @Test
    fun invalidLDES() {
        assertFails {
            IndexedVersionedLinkedDataEventStream(
                identifier = NamedTerm("myLDES"),
                transform = StreamTransform.GraphBased,
                store = indexedStoreOf()
            )
        }
    }

    @Test
    fun mutatedVersionedLDES() {
        val ldes = MutableVersionedLinkedDataEventStream.initialise(
            identifier = NamedTerm("myLDES"),
            timestampPath = DC.modified,
            versionOfPath = DC.isVersionOf,
            transform = StreamTransform.GraphBased
        )
        val one: RDF_DSL = {
            val example = prefix("ex", "http://example.org/")
            val document = prefix("", "http://example-document.org/")

            (document / "s1") (example / "name") (document / "Test")
            (document / "s1") (example / "property") (Quad.Literal("Value"))

            (document / "Test") (example / "value") (Quad.Literal("abc"))
        }
        val two: RDF_DSL = {
            val example = prefix("ex", "http://example.org/")
            val document = prefix("", "http://example-document.org/")

            (document / "s2") (example / "name") (document / "Test2")
            (document / "s2") (example / "property") (Quad.Literal("Value"))
            (document / "s2") (example / "property") (Quad.Literal("Additional"))

            (document / "Test2") (example / "value") (Quad.Literal("def"))
        }
        val two2: RDF_DSL = {
            val example = prefix("ex", "http://example.org/")
            val document = prefix("", "http://example-document.org/")

            (document / "s2") (example / "name") (Quad.Literal("Test2"))
            (document / "s2") (example / "property") (Quad.Literal("Additional"))

            (document / "Test2") (example / "value") (Quad.Literal("def"))
        }
        ldes.add(
            baseVersion = NamedTerm("s1"),
            timestamp = (Clock.System.now() - 30.seconds).asLiteral(),
            data = buildStore(block = one)
        )
        ldes.add(
            baseVersion = NamedTerm("s2"),
            timestamp = (Clock.System.now() - 20.seconds).asLiteral(),
            data = buildStore(block = two)
        )
        ldes.add(
            baseVersion = NamedTerm("s2"),
            timestamp = (Clock.System.now() - 10.seconds).asLiteral(),
            data = buildStore(block = two2)
        )
        val serializer = serializer(TriG) {
            usePrettyFormatting {
                withPrefixes {
                    putAll(one.extractPrefixes())
                    putAll(two.extractPrefixes())
                    putAll(two2.extractPrefixes())
                    putAll(Prefixes(DC, TREE, LDES, RDF, XSD))
                }
            }
        }
        println(serializer.serialize(ldes))
    }

    @Test
    fun consumeLDES() {
        val ldes = MutableVersionedLinkedDataEventStream.initialise(
            identifier = NamedTerm("myLDES"),
            timestampPath = DC.modified,
            versionOfPath = DC.isVersionOf,
            transform = StreamTransform.GraphBased
        )
        val now = Clock.System.now()
        val data = buildStore { NamedTerm("s1") a NamedTerm("Test") }
        ldes.add(
            baseVersion = NamedTerm("s1"),
            timestamp = (now - 10.seconds).asLiteral(),
            data = data
        )
        assertStoreContentEqual(emptyStore(), ldes.read((now - 20.seconds).asLiteral()))
        assertStoreContentEqual(data, ldes.read((now - 5.seconds).asLiteral()))
    }

    @Test
    fun consumeVersionedLDES() {
        val ldes = MutableVersionedLinkedDataEventStream.initialise(
            identifier = NamedTerm("myLDES"),
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

        val data1 = buildStore { NamedTerm("s1") a NamedTerm("Test") }
        val data1v2 = buildStore { NamedTerm("s1") a NamedTerm("Test2") }
        val data2 = buildStore { NamedTerm("s2") a NamedTerm("Test") }

        ldes.add(
            baseVersion = NamedTerm("s1"),
            timestamp = t1,
            data = data1
        )
        ldes.add(
            baseVersion = NamedTerm("s2"),
            timestamp = t2,
            data = data2
        )
        ldes.add(
            baseVersion = NamedTerm("s1"),
            timestamp = t3,
            data = data1v2
        )
        val serializer = serializer(TriG) {
            usePrettyFormatting {
                withPrefixes {
                    putAll(Prefixes(DC, TREE, LDES, RDF, XSD))
                }
            }
        }
        println(serializer.serialize(ldes))
        assertStoreContentEqual(emptyStore(), ldes.read(pre_t1))
        assertStoreContentEqual(data1, ldes.read(pre_t2))
        assertStoreContentEqual(Store(data1 + data2), ldes.read(pre_t3))
        assertStoreContentEqual(Store(data1v2 + data2), ldes.read(pre_t4))

        // doing the same tests, but indexed
        val indexed = IndexedVersionedLinkedDataEventStream(
            identifier = NamedTerm("myLDES"),
            store = IndexedStore(ldes),
            transform = StreamTransform.GraphBased
        )

        assertStoreContentEqual(emptyStore(), indexed.read(pre_t1))
        assertStoreContentEqual(data1, indexed.read(pre_t2))
        assertStoreContentEqual(Store(data1 + data2), indexed.read(pre_t3))
        assertStoreContentEqual(Store(data1v2 + data2), indexed.read(pre_t4))
    }

    private fun assertStoreContentEqual(expected: Store, actual: Store) {
        val missing = expected - actual
        val superfluous = actual - expected
        if (missing.isNotEmpty() || superfluous.isNotEmpty()) {
            fail("Store content mismatch!\nMissing quads: ${missing.toTruncatedString(200)}\nUnexpected quads: ${superfluous.toTruncatedString(200)}")
        }
    }

}

private fun Instant.asLiteral() = Quad.Literal(value = toString(), type = XSD.date) as Quad.TypedLiteral
