package dev.tesserakt.benchmarking

expect fun currentEpochMs(): Long

expect fun String.isFolder(): Boolean

expect fun String.listFiles(): List<String>
