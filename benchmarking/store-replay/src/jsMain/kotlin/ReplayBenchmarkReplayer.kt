
import dev.tesserakt.rdf.serialization.common.Path
import dev.tesserakt.rdf.trig.serialization.TriGSerializer
import dev.tesserakt.rdf.types.consume
import dev.tesserakt.sparql.benchmark.replay.ReplayBenchmark
import dev.tesserakt.sparql.benchmark.replay.SnapshotStore

@OptIn(ExperimentalJsExport::class)
@JsExport
class ReplayBenchmarkReplayer private constructor(private val benchmark: ReplayBenchmark) {

    @JsName("Diff")
    class DiffJs internal constructor(diff: SnapshotStore.Diff) {

        @OptIn(ExperimentalJsCollectionsApi::class)
        val insertions = diff
            .insertions
            .mapTo(mutableSetOf()) { it.toJsQuad() }
                .asJsSetView()

        @OptIn(ExperimentalJsCollectionsApi::class)
        val deletions = diff
            .deletions
            .mapTo(mutableSetOf()) { it.toJsQuad() }
            .asJsSetView()

    }

    val queries: Array<String> = benchmark.queries.toTypedArray()

    fun forEachSnapshot(callback: (MutableStoreJs, DiffJs) -> Unit) {
        benchmark.eval { store, diff ->
            callback(store.toJsMutableStore(), DiffJs(diff))
        }
    }

    companion object {

        @OptIn(ExperimentalJsStatic::class)
        @JsStatic
        fun fromFile(filepath: String): ReplayBenchmarkReplayer {
            return ReplayBenchmarkReplayer(
                benchmark = ReplayBenchmark
                    .from(TriGSerializer.deserialize(Path(filepath)).consume())
                    .single()
            )
        }

    }

}
