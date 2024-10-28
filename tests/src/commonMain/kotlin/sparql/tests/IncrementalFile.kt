package sparql.tests

import dev.tesserakt.rdf.serialization.Turtle.parseTurtleString
import dev.tesserakt.util.printerrln
import sparql.readFile
import test.suite.testEnv

fun compareIncrementalBasicGraphPatternOutput(datasetFilepath: String, queryFilepath: String) = testEnv {
    val store = readFile(datasetFilepath).getOrElse {
        printerrln("Failed to read triples at `$datasetFilepath`! Caught ${it::class.simpleName}")
        it.printStackTrace()
        return@testEnv
    }.parseTurtleString()
    val querySource = readFile(queryFilepath).getOrElse {
        printerrln("Failed to read query at `$queryFilepath`! Caught ${it::class.simpleName}")
        it.printStackTrace()
        return@testEnv
    }
    // the various queries in the file are expected to be separated using an additional newline, so creating tests for
    //  every query formatted that way
    val queries = querySource.split("\n\n").filter { it.isNotBlank() }
    println("Found ${queries.size} queries that will be used on a dataset with ${store.size} triples!")
    queries.forEach { query ->
        using(store) test query
    }
}
