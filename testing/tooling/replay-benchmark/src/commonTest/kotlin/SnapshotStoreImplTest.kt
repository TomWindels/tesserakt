
import dev.tesserakt.rdf.dsl.buildStore
import dev.tesserakt.rdf.ontology.RDF
import dev.tesserakt.rdf.ontology.XSD
import dev.tesserakt.rdf.serialization.common.serializer
import dev.tesserakt.rdf.trig.serialization.TriG
import dev.tesserakt.rdf.trig.serialization.usePrettyFormatting
import dev.tesserakt.rdf.trig.serialization.withPrefixes
import dev.tesserakt.rdf.types.Quad.Companion.asNamedTerm
import dev.tesserakt.rdf.types.SnapshotStore
import dev.tesserakt.rdf.types.Store
import dev.tesserakt.stream.ldes.ontology.DC
import dev.tesserakt.stream.ldes.ontology.LDES
import dev.tesserakt.stream.ldes.ontology.TREE
import dev.tesserakt.util.toTruncatedString
import kotlin.test.Test
import kotlin.test.assertFails
import kotlin.test.fail

class SnapshotStoreImplTest {

    @Test
    fun insertion() {
        val first = buildStore {
            "s1".asNamedTerm() has RDF.type being "Test".asNamedTerm()
        }
        val second = buildStore {
            "s1".asNamedTerm() has RDF.type being "Test".asNamedTerm()
            "s2".asNamedTerm() has RDF.type being "Test".asNamedTerm()
        }
        val third = buildStore {
            "s2".asNamedTerm() has RDF.type being "Test".asNamedTerm()
        }

        val snapshotStore = SnapshotStore
            .Builder(start = first)
            .addSnapshot(second)
            .addSnapshot(third)
            .build("snapshotStore".asNamedTerm())

        val serializer = serializer(TriG) {
            usePrettyFormatting {
                withPrefixes(XSD, TREE, LDES, DC, RDF)
            }
        }
        println(serializer.serialize(snapshotStore.toStore()))

        val snapshots = snapshotStore.snapshots.iterator()

        assertStoreContentEqual(first, snapshots.next())
        assertStoreContentEqual(second, snapshots.next())
        assertStoreContentEqual(third, snapshots.next())
        assertFails { snapshots.next() }

        snapshotStore.diffs.forEachIndexed { i, diff ->
            println("Delta ${i + 1}\n$diff")
        }
    }

    private fun assertStoreContentEqual(expected: Store, actual: Store) {
        val missing = expected - actual
        val superfluous = actual - expected
        if (missing.isNotEmpty() || superfluous.isNotEmpty()) {
            fail("Store content mismatch!\nMissing quads: ${missing.toTruncatedString(200)}\nUnexpected quads: ${superfluous.toTruncatedString(200)}")
        }
    }

}
