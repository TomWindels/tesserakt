import dev.tesserakt.util.printerrln
import sparql.tests.compareIncrementalBasicGraphPatternOutput
import sparql.tests.compareIncrementalSelectOutput

suspend fun run(args: Array<String>) {
    when (args.size) {
        0 -> {
            println("Running built-in tests")
            val one = compareIncrementalSelectOutput().run()
            val two = compareIncrementalBasicGraphPatternOutput().run()
            one.report()
            two.report()
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
