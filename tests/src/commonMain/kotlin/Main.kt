import sparql.tests.compareIncrementalBasicGraphPatternOutput


suspend fun main(args: Array<String>) {
    if (args.isNotEmpty()) {
        println(args.joinToString("--"))
    } else {
        println("Running built-in tests")
        compareIncrementalBasicGraphPatternOutput().run().report()
    }
}
