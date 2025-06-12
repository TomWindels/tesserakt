package dev.tesserakt.benchmarking

expect fun currentEpochMs(): Long

expect fun String.isFolder(): Boolean

/**
 * Similar to [isFolder], but also tries to make the folder if necessary
 */
expect fun String.tryMakeFolder(): Boolean

expect fun String.listFiles(): List<String>
