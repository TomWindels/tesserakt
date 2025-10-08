package dev.tesserakt.benchmarking

import java.io.File

fun currentEpochMs(): Long {
    return System.currentTimeMillis()
}

fun String.readFile(): String {
    return File(this).readText()
}

fun String.isFile(): Boolean {
    return File(this).isFile
}

fun String.isFolder(): Boolean {
    return File(this).isDirectory
}

/**
 * Similar to [isFolder], but also tries to make the folder if necessary
 */
fun String.tryMakeFolder(): Boolean {
    val file = File(this)
    file.mkdirs()
    return file.isDirectory
}

fun String.listFiles(): List<String> {
    return File(this).listFiles()!!.mapNotNull { if (it.isFile) it.path else null }
}

fun String.basename(): String = when {
    isEmpty() -> ""
    last() != '/' -> substringAfterLast('/')
    else -> {
        val loc = lastIndexOf('/', length - 2)
        substring(loc + 1, length - 1)
    }
}
