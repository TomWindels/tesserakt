package dev.tesserakt.benchmarking

expect fun currentEpochMs(): Long

expect fun String.readFile(): String

expect fun String.isFile(): Boolean

expect fun String.isFolder(): Boolean

/**
 * Similar to [isFolder], but also tries to make the folder if necessary
 */
expect fun String.tryMakeFolder(): Boolean

expect fun String.listFiles(): List<String>

fun String.basename(): String = when {
    isEmpty() -> ""
    last() != '/' -> substringAfterLast('/')
    else -> {
        val loc = lastIndexOf('/', length - 2)
        substring(loc + 1, length - 1)
    }
}
