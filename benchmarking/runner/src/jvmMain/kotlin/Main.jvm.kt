import dev.tesserakt.benchmarking.RunnerConfig

fun main(args: Array<String>) {
    val runtime = Runtime.getRuntime()
    val configs = RunnerConfig.fromCommandLine(args)
    configs.forEachIndexed { i, config ->
        println("Evaluating benchmark ${i + 1}/${configs.size}")
        println(config)
        config.createRunner().run()
        // GC'ing in-between iterations, giving a fresh start for the warm up
        runtime.gc()
    }
}
