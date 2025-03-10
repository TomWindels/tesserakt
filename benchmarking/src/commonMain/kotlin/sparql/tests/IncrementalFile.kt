package sparql.tests

import dev.tesserakt.rdf.serialization.Turtle.parseTurtleString
import dev.tesserakt.util.printerrln
import sparql.readFile
import sparql.types.tests

fun compareIncrementalBasicGraphPatternOutput(datasetFilepath: String, queryFilepath: String) = tests {
    val store = readFile(datasetFilepath).getOrElse {
        printerrln("Failed to read triples at `$datasetFilepath`! Caught ${it::class.simpleName}")
        it.printStackTrace()
        return@tests
    }.parseTurtleString()
    val querySource = readFile(queryFilepath).getOrElse {
        printerrln("Failed to read query at `$queryFilepath`! Caught ${it::class.simpleName}")
        it.printStackTrace()
        return@tests
    }
    // the various queries in the file are expected to be separated using an additional newline, so creating tests for
    //  every query formatted that way
    val queries = querySource.split("\n\n").filter { it.isNotBlank() }
    println("Found ${queries.size} queries that will be used on a dataset with ${store.size} triples!")
    queries.forEach { query ->
        using(store) test query
    }
}
