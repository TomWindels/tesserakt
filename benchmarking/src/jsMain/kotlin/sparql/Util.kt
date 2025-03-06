package sparql

import comunica.comunicaSelectQuery
import dev.tesserakt.interop.rdfjs.n3.N3Store
import dev.tesserakt.interop.rdfjs.toN3Store
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.runtime.common.types.Bindings
import dev.tesserakt.util.node.readFileSync
import kotlin.time.measureTime


actual class ExternalQueryExecution actual constructor(
    private val query: String,
    data: Collection<Quad>
) {

    private val store: N3Store
    private val duration = measureTime {
        store = data.toN3Store()
    }

    actual suspend fun execute(): List<Bindings> {
        return store.comunicaSelectQuery(query)
    }

    actual fun report(): String {
        return " * Comunica query execution\n\tPreparation took $duration"
    }

}

actual fun readFile(filepath: String): Result<String> = runCatching {
    // Calling the readFileSync() method
    // to read 'input.txt' file
    val opts: dynamic = Any()
    opts.encoding = "utf8"
    opts.flag = "r"
    // const data = fs.readFileSync('./input.txt', { encoding: 'utf8', flag: 'r' });
    readFileSync(filepath, opts)
}
