package dev.tesserakt.benchmarking

import java.io.BufferedReader
import java.io.InputStreamReader


object CommandExecutor {

    fun run(command: String): String {
        val rt = Runtime.getRuntime()
        val commands = command.split(Regex("\\s+")).toTypedArray()
        val proc = rt.exec(commands)
        val stdInput = BufferedReader(InputStreamReader(proc.inputStream))
        val stdError = BufferedReader(InputStreamReader(proc.errorStream))
        val err = buildString {
            // Read any errors from the attempted command
            stdError.lines().forEach {
                appendLine(it)
            }
        }
        if (err.isNotBlank()) {
            throw IllegalStateException("Command contained errors:\n$err")
        }
        // Read the output from the command
        return buildString {
            stdInput.forEachLine {
                appendLine(it)
            }
        }.trim()
    }

}
