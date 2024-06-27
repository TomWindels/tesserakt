package sparql.tests

import dev.tesserakt.rdf.types.Store
import dev.tesserakt.util.printerrln
import test.suite.TestEnvironment
import test.suite.testEnv

fun compareIncrementalBasicGraphPatternOutput(filepath: String, query: String) = testEnv {
    val store = readFile(filepath) .getOrElse {
        printerrln("Failed to read triples at `$filepath`! Caught ${it::class.simpleName}")
        it.printStackTrace()
        return@testEnv
    }
    using(store) test query
}

internal expect fun readFile(filepath: String): Result<Store>
