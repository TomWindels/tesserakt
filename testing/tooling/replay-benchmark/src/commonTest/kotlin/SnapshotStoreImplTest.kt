
import dev.tesserakt.rdf.dsl.buildStore
import dev.tesserakt.rdf.ontology.RDF
import dev.tesserakt.rdf.ontology.XSD
import dev.tesserakt.rdf.serialization.common.serializer
import dev.tesserakt.rdf.serialization.trig.TriG
import dev.tesserakt.rdf.serialization.trig.usePrettyFormatting
import dev.tesserakt.rdf.serialization.trig.withPrefixes
import dev.tesserakt.rdf.types.IndexedStore
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.Quad.NamedTerm
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
            NamedTerm("s1") a NamedTerm("Test")
        }
        val second = buildStore {
            NamedTerm("s1") a NamedTerm("Test")
            NamedTerm("s2") a NamedTerm("Test")
        }
        val third = buildStore {
            NamedTerm("s2") a NamedTerm("Test")
        }

        val snapshotStore = SnapshotStore
            .Builder(start = IndexedStore(first))
            .addSnapshot(second)
            .addSnapshot(third)
            .build(NamedTerm("snapshotStore"))

        val serializer = serializer(TriG) {
            usePrettyFormatting {
                withPrefixes(XSD, TREE, LDES, DC, RDF)
            }
        }
        println(serializer.serialize(snapshotStore.toStore()).let { buildString { while (it.hasNext()) append(it.next()) }})

        val diffs = snapshotStore.diffs.iterator()

        assertDiffContentEqual(
            expectedInsertions = setOf(Quad(NamedTerm("s1"), RDF.type, NamedTerm("Test"))),
            expectedDeletions = emptySet(),
            actual = diffs.next(),
        )
        assertDiffContentEqual(
            expectedInsertions = setOf(Quad(NamedTerm("s2"), RDF.type, NamedTerm("Test"))),
            expectedDeletions = emptySet(),
            actual = diffs.next(),
        )
        assertDiffContentEqual(
            expectedInsertions = emptySet(),
            expectedDeletions = setOf(Quad(NamedTerm("s1"), RDF.type, NamedTerm("Test"))),
            actual = diffs.next(),
        )

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

    private fun assertDiffContentEqual(expectedInsertions: Set<Quad>, expectedDeletions: Set<Quad>, actual: SnapshotStore.Diff) {
        val missing1 = expectedInsertions - actual.insertions
        val missing2 = expectedDeletions - actual.deletions
        val superfluous1 = actual.insertions - expectedInsertions
        val superfluous2 = actual.deletions - expectedDeletions
        if (missing1.isNotEmpty() || missing2.isNotEmpty() || superfluous1.isNotEmpty() || superfluous2.isNotEmpty()) {
            fail("Store content mismatch!\nMissing insertion quads: ${missing1.toTruncatedString(200)}\nMissing deletion quads: ${missing2.toTruncatedString(200)}\nUnexpected insertion quads: ${superfluous1.toTruncatedString(200)}\nUnexpected deletion quads: ${superfluous2.toTruncatedString(200)}")
        }
    }

}
