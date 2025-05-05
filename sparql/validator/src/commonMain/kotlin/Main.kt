
import dev.tesserakt.util.printerrln
import sparql.tests.*
import sparql.types.QueryExecutionTestValues

suspend fun run(args: Array<String>) {
    when (args.size) {
        0 -> {
            println("Running built-in tests")
            val results = listOf(
                compareIncrementalChainSelectOutput(seed = 1)
                    .test(QueryExecutionTestValues::toOutputComparisonTest),
                compareIncrementalStarSelectOutput(seed = 1)
                    .test(QueryExecutionTestValues::toOutputComparisonTest),
                builtinTests()
                    .test(QueryExecutionTestValues::toOutputComparisonTest),
                builtinTests()
                    .test(QueryExecutionTestValues::toIncrementalUpdateTest),
                builtinTests()
                    .test(QueryExecutionTestValues::toRandomUpdateTest),
            ).map { it.run() }
            results.forEach { it.report() }
            if (results.any { !it.isSuccess() }) {
                throw IllegalStateException("Not all tests succeeded!")
            }
        }
        1 -> {
            val (replayBenchmarkPath) = args
            compareIncrementalStoreReplay(replayBenchmarkPath)
        }
        2 -> {
            val (dataset, querypath) = args
            compareIncrementalBasicGraphPatternOutput(datasetFilepath = dataset, queryFilepath = querypath)
                .test(QueryExecutionTestValues::toOutputComparisonTest)
                .run()
                .report()
        }
        else -> {
            printerrln("Invalid arguments provided. Please provide a dataset filepath & query filepath.")
        }
    }
}
