package dev.tesserakt.benchmarking

import java.io.File

actual fun currentEpochMs(): Long {
    return System.currentTimeMillis()
}

actual fun String.readFile(): String {
    return File(this).readText()
}

actual fun String.isFile(): Boolean {
    return File(this).isFile
}

actual fun String.isFolder(): Boolean {
    return File(this).isDirectory
}

/**
 * Similar to [isFolder], but also tries to make the folder if necessary
 */
actual fun String.tryMakeFolder(): Boolean {
    val file = File(this)
    file.mkdirs()
    return file.isDirectory
}

actual fun String.listFiles(): List<String> {
    return File(this).listFiles()!!.mapNotNull { if (it.isFile) it.path else null }
}
