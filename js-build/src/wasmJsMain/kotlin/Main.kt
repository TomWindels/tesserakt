
import dev.tesserakt.interop.rdfjs.n3.N3Quad
import dev.tesserakt.interop.rdfjs.n3.N3Store
import dev.tesserakt.interop.rdfjs.toN3Triple
import dev.tesserakt.interop.rdfjs.toStore
import dev.tesserakt.rdf.types.MutableStore
import dev.tesserakt.sparql.Compiler.Default.asSPARQLSelectQuery
import dev.tesserakt.sparql.benchmark.replay.ReplayBenchmark
import dev.tesserakt.sparql.benchmark.replay.SnapshotStore
import dev.tesserakt.sparql.runtime.common.types.Bindings
import dev.tesserakt.sparql.runtime.incremental.evaluation.query

@OptIn(ExperimentalJsExport::class)
@JsExport
fun query(query: JsString, store: JsAny): JsReference<List<Bindings>> {
    /* a simple source file to ensure the build is not skipped */
    val query = query.toString().asSPARQLSelectQuery()
    val n3 = store.unsafeCast<N3Store>()
    val data = n3.toStore()
    val bindings = data.query(query)
    return bindings.toJsReference()
}

@OptIn(ExperimentalJsExport::class)
@JsExport
fun test(data: JsReference<List<Bindings>>) {
    val bindings = data.get()
    bindings.forEach {
        println(it)
    }
}

internal fun startTime() {
    js("{ console.time('tesserakt-sparql') }")
}

internal fun endTime() {
    js("{ console.timeEnd('tesserakt-sparql') }")
}

@OptIn(ExperimentalJsExport::class)
@JsExport
fun runBenchmark(store: JsAny) {
    val replay = ReplayBenchmark.from((store.unsafeCast<N3Store>()).toStore()).single()
    val query = replay.queries.first().asSPARQLSelectQuery()
    repeat(10) {
        println("Run ${it + 1} / 10")
        val store = MutableStore()
        val eval = store.query(query)
        replay.eval { current, diff ->
            startTime()
            diff.insertions.forEach { store.add(it) }
            diff.deletions.forEach { store.remove(it) }
            endTime()
            println("Received ${eval.results.size} binding(s)!")
        }
    }
}

@OptIn(ExperimentalJsExport::class)
@JsExport
fun getBenchmarkQuery(store: JsAny): JsString {
    val replay = ReplayBenchmark.from(store.unsafeCast<N3Store>().toStore()).single()
    return replay.queries.first().toJsString()
}

@OptIn(ExperimentalJsExport::class)
@JsExport
fun getBenchmarks(store: JsAny): JsArray<JsReference<SnapshotStore.Diff>> {
    val snapshots = SnapshotStore(store.unsafeCast<N3Store>().toStore())
    return snapshots.diffs.map { it.toJsReference() }.toJsArray()
}

@OptIn(ExperimentalJsExport::class)
@JsExport
fun extractInsertions(diff: JsReference<SnapshotStore.Diff>): JsArray<N3Quad> {
    return diff.get().insertions.map { it.toN3Triple() }.toJsArray()
}

@OptIn(ExperimentalJsExport::class)
@JsExport
fun extractDeletions(diff: JsReference<SnapshotStore.Diff>): JsArray<N3Quad> {
    return diff.get().deletions.map { it.toN3Triple() }.toJsArray()
}
