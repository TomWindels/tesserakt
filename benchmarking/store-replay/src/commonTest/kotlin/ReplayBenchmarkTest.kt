
import dev.tesserakt.rdf.dsl.buildStore
import dev.tesserakt.rdf.ontology.RDF
import dev.tesserakt.rdf.ontology.XSD
import dev.tesserakt.rdf.serialization.common.Prefixes
import dev.tesserakt.rdf.trig.serialization.TriGSerializer
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.Quad.Companion.asNamedTerm
import dev.tesserakt.rdf.types.Store
import dev.tesserakt.sparql.benchmark.replay.RBO
import dev.tesserakt.sparql.benchmark.replay.ReplayBenchmark
import dev.tesserakt.sparql.benchmark.replay.SnapshotStore
import dev.tesserakt.stream.ldes.ontology.DC
import dev.tesserakt.stream.ldes.ontology.LDES
import dev.tesserakt.stream.ldes.ontology.TREE
import dev.tesserakt.util.toTruncatedString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class ReplayBenchmarkTest {

    @Test
    fun evaluation() {
        val benchmark = buildBenchmark()
        println(TriGSerializer.serialize(
            store = benchmark.toStore(),
            prefixes = Prefixes(XSD, TREE, LDES, DC, RDF, RBO)
        ))
        var i = 0
        benchmark.eval { current: Store, diff: SnapshotStore.Diff ->
            println(current)
            println(diff)
            ++i
        }
        assertEquals(3, i)
    }

    @Test
    fun representation() {
        val reference = buildBenchmark()
        val store = reference.toStore()
        val rebuilt = ReplayBenchmark.from(store).single()
        // evaluating the outer one; iterating like this on purpose to validate multiple evals as well
        var i = 0
        reference.eval outer@ { current1, diff1 ->
            var j = -1
            rebuilt.eval inner@ { current2, diff2 ->
                ++j
                if (j < i || j > i) {
                    return@inner
                }
                assertStoreContentEqual(current1, current2)
                assertEquals(diff1, diff2)
            }
            ++i
        }
        assertEquals(3, i)
    }

    private fun buildBenchmark(): ReplayBenchmark {
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

        return ReplayBenchmark(identifier = "benchmark".asNamedTerm(), snapshotStore, listOf("SELECT * WHERE { ?s ?p ?o }"))
    }

    private fun assertStoreContentEqual(expected: Set<Quad>, actual: Set<Quad>) {
        val missing = expected - actual
        val superfluous = actual - expected
        if (missing.isNotEmpty() || superfluous.isNotEmpty()) {
            fail("Store content mismatch!\nMissing quads: ${missing.toTruncatedString(200)}\nUnexpected quads: ${superfluous.toTruncatedString(200)}")
        }
    }

}
