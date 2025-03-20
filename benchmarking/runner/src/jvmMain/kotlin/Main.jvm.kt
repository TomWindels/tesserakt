import dev.tesserakt.benchmarking.RunnerConfig

fun main(args: Array<String>) {
    val configs = RunnerConfig.fromCommandLine(args)
    configs.forEach {
        it.createRunner().run()
    }
}
