
import dev.tesserakt.util.printerrln
import sparql.tests.*
import sparql.types.QueryExecutionTest
import sparql.types.test

suspend fun run(args: Array<String>) {
    when (args.size) {
        0 -> {
            println("Running built-in tests")
            val one = compareIncrementalChainSelectOutput(seed = 1)
                .test(QueryExecutionTest::toOutputComparisonTest)
                .run()
            val two = compareIncrementalStarSelectOutput(seed = 1)
                .test(QueryExecutionTest::toOutputComparisonTest)
                .run()
            val three = builtinTests()
                .test(QueryExecutionTest::toOutputComparisonTest)
                .run()
            val four = builtinTests()
                .test(QueryExecutionTest::toIncrementalUpdateTest)
                .run()
            val five = builtinTests()
                .test(QueryExecutionTest::toRandomUpdateTest)
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
                .test(QueryExecutionTest::toOutputComparisonTest)
                .run()
                .report()
        }
        else -> {
            printerrln("Invalid arguments provided. Please provide a dataset filepath & query filepath.")
        }
    }
}
