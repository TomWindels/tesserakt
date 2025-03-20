package dev.tesserakt.benchmarking

import java.io.File

actual fun currentEpochMs(): Long {
    return System.currentTimeMillis()
}

actual fun String.isFolder(): Boolean {
    return File(this).isDirectory
}

actual fun String.listFiles(): List<String> {
    return File(this).listFiles()!!.mapNotNull { if (it.isFile) it.path else null }
}
