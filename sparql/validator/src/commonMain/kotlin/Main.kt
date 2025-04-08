
import dev.tesserakt.util.printerrln
import sparql.tests.*
import sparql.types.QueryExecutionTestValues

suspend fun run(args: Array<String>) {
    when (args.size) {
        0 -> {
            println("Running built-in tests")
            val one = compareIncrementalChainSelectOutput(seed = 1)
                .test(QueryExecutionTestValues::toOutputComparisonTest)
                .run()
            val two = compareIncrementalStarSelectOutput(seed = 1)
                .test(QueryExecutionTestValues::toOutputComparisonTest)
                .run()
            val three = builtinTests()
                .test(QueryExecutionTestValues::toOutputComparisonTest)
                .run()
            val four = builtinTests()
                .test(QueryExecutionTestValues::toIncrementalUpdateTest)
                .run()
            val five = builtinTests()
                .test(QueryExecutionTestValues::toRandomUpdateTest)
                .run()
            one.report()
            two.report()
            three.report()
            four.report()
            five.report()
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
