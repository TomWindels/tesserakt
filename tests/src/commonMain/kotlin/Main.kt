import dev.tesserakt.util.printerrln
import sparql.tests.compareIncrementalBasicGraphPatternOutput

suspend fun run(args: Array<String>) {
    when (args.size) {
        0 -> {
            println("Running built-in tests")
            compareIncrementalBasicGraphPatternOutput()
                .run()
                .report()
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
