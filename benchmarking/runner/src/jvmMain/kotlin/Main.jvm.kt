
import dev.tesserakt.benchmarking.RunnerConfig
import dev.tesserakt.util.printerrln
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

fun main(args: Array<String>) {
    val runtime = Runtime.getRuntime()
    val configs = RunnerConfig.fromCommandLine(args)
    val executor = Executors.newSingleThreadExecutor()
    configs.forEachIndexed { i, config ->
        println("Evaluating benchmark ${i + 1}/${configs.size}")
        println(config)
        try {
            executor
                .submit { config.createRunner().run() }
                .get(5, TimeUnit.MINUTES)
        } catch (t: TimeoutException) {
            printerrln("Timeout expired!")
        }
        // GC'ing in-between iterations, giving a fresh start for the warm up
        runtime.gc()
    }
    require(executor.shutdownNow().isEmpty()) { "The executing thread did not finish all tasks properly!" }
    println("Execution finished!")
}
