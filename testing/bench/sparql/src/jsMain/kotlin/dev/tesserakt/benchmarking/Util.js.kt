package dev.tesserakt.benchmarking

import kotlin.js.Date

private val fs = js("require('fs')")

actual fun currentEpochMs(): Long {
    return Date.now().toLong()
}

actual fun String.readFile(): String {
    // Calling the readFileSync() method
    // to read 'input.txt' file
    val opts: dynamic = Any()
    opts.encoding = "utf8"
    opts.flag = "r"
    // const data = fs.readFileSync('./input.txt', { encoding: 'utf8', flag: 'r' });
    return fs.readFileSync(this, opts)
}

actual fun String.isFile(): Boolean {
    return fs.existsSync(this) as Boolean && fs.statSync(this).isFile() as Boolean
}

actual fun String.isFolder(): Boolean {
    return fs.existsSync(this) as Boolean && fs.statSync(this).isDirectory() as Boolean
}

/**
 * Similar to [isFolder], but also tries to make the folder if necessary
 */
actual fun String.tryMakeFolder(): Boolean {
    val option: dynamic = Any()
    option.recursive = true
    fs.mkdirSync(this, option)
    return isFolder()
}

actual fun String.listFiles(): List<String> {
    val contents = fs.readdirSync(this).unsafeCast<Array<String>>().toList()
    return contents.mapNotNull { filename ->
        val filepath = "$this/$filename"
        val stat = fs.statSync(filepath)
        filepath.takeIf { !(stat.isDirectory() as Boolean) }
    }
}
