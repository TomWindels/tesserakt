
import dev.tesserakt.util.printerrln
import sparql.tests.compareIncrementalBasicGraphPatternOutput
import sparql.tests.compareIncrementalChainSelectOutput
import sparql.tests.compareIncrementalStarSelectOutput

suspend fun run(args: Array<String>) {
    when (args.size) {
        0 -> {
            println("Running built-in tests")
            val one = compareIncrementalChainSelectOutput(seed = 1).run()
            val two = compareIncrementalStarSelectOutput(seed = 1).run()
            val three = compareIncrementalBasicGraphPatternOutput().run()
            one.report()
            two.report()
            three.report()
        }
        2 -> {
            val (dataset, querypath) = args
            compareIncrementalBasicGraphPatternOutput(datasetFilepath = dataset, queryFilepath = querypath)
                .run()
                .report()
        }
        else -> {
            printerrln("Invalid arguments provided. Please provide a dataset filepath & query filepath.")
        }
    }
}
