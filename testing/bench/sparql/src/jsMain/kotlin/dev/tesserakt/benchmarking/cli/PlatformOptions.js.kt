package dev.tesserakt.benchmarking.cli

import com.github.ajalt.clikt.parameters.groups.OptionGroup

actual class PlatformOptions actual constructor() : OptionGroup(
    name = "JS-specific configuration",
    help = "Various properties that are specific to the JS version of the benchmarking tool"
) {

    actual fun apply() {
        // nothing to do
    }

}
