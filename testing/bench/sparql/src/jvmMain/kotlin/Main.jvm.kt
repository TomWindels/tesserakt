
import com.github.ajalt.clikt.command.main
import dev.tesserakt.benchmarking.BenchmarkingCli

suspend fun main(args: Array<String>) =
    BenchmarkingCli().main(args)
