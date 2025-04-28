import dev.tesserakt.rdf.dsl.buildStore
import dev.tesserakt.rdf.ontology.RDF
import dev.tesserakt.rdf.ontology.XSD
import dev.tesserakt.rdf.serialization.common.serialize
import dev.tesserakt.rdf.trig.serialization.TriGSerializer
import dev.tesserakt.rdf.trig.serialization.prefixes
import dev.tesserakt.rdf.trig.serialization.prettyFormatting
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.Quad.Companion.asNamedTerm
import dev.tesserakt.rdf.types.SnapshotStore
import dev.tesserakt.stream.ldes.ontology.DC
import dev.tesserakt.stream.ldes.ontology.LDES
import dev.tesserakt.stream.ldes.ontology.TREE
import dev.tesserakt.util.toTruncatedString
import kotlin.test.Test
import kotlin.test.assertFails
import kotlin.test.fail

class SnapshotStoreTest {

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

        println(TriGSerializer.serialize(
            data = snapshotStore.toStore(),
            config = {
                prettyFormatting {
                    prefixes(XSD, TREE, LDES, DC, RDF)
                }
            },
        ))

        val snapshots = snapshotStore.snapshots.iterator()

        assertStoreContentEqual(first, snapshots.next())
        assertStoreContentEqual(second, snapshots.next())
        assertStoreContentEqual(third, snapshots.next())
        assertFails { snapshots.next() }

        snapshotStore.diffs.forEachIndexed { i, diff ->
            println("Delta ${i + 1}\n$diff")
        }
    }

    private fun assertStoreContentEqual(expected: Set<Quad>, actual: Set<Quad>) {
        val missing = expected - actual
        val superfluous = actual - expected
        if (missing.isNotEmpty() || superfluous.isNotEmpty()) {
            fail("Store content mismatch!\nMissing quads: ${missing.toTruncatedString(200)}\nUnexpected quads: ${superfluous.toTruncatedString(200)}")
        }
    }

}
