package dev.tesserakt.benchmarking

import kotlin.js.Date

private val fs = js("require('fs')")

actual fun currentEpochMs(): Long {
    return Date.now().toLong()
}

actual fun String.isFolder(): Boolean {
    return fs.existsSync(this) as Boolean && fs.statSync(this).isDirectory() as Boolean
}

actual fun String.listFiles(): List<String> {
    val contents = fs.readdirSync(this).unsafeCast<Array<String>>().toList()
    return contents.mapNotNull { filename ->
        val filepath = "$this/$filename"
        val stat = fs.statSync(filepath)
        filepath.takeIf { !(stat.isDirectory() as Boolean) }
    }
}
