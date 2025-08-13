package dev.tesserakt.benchmarking.cli

import com.github.ajalt.clikt.parameters.groups.OptionGroup

expect class PlatformOptions(): OptionGroup {

    fun apply()

}
