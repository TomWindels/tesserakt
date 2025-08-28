package dev.tesserakt.benchmarking.cli

import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import dev.tesserakt.benchmarking.GcContext
import dev.tesserakt.benchmarking.RunContext

actual class PlatformOptions actual constructor() : OptionGroup(
    name = "JVM-specific configuration",
    help = "Various properties that are specific to the JVM version of the benchmarking tool"
) {

    val forceGc by option().flag(default = false).help("Request the garbage collector to execute after every query execution")

    val enableMemoryProfiling by option().flag(default = false).help("Enable memory profiling, writing additional output. Not useful when dealing with endpoints")

    actual fun apply() {
        if (forceGc) {
            RunContext.CURRENT = GcContext
        }
        RunContext.memoryProfiling = enableMemoryProfiling
    }

}
