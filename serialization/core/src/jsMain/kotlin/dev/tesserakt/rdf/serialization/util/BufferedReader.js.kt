package dev.tesserakt.rdf.serialization.util

private val fs = js("require('fs')")

actual class BufferedReader(private val content: String) : AutoCloseable {

    private var pos = 0

    actual override fun close() {
        // nothing to do
    }

    internal fun read(count: Int): String? {
        if (pos >= content.length) {
            return null
        }
        val result = content.substring(pos, (pos + count).coerceAtMost(content.length))
        pos += count
        return result
    }

}

actual fun BufferedReader.read(count: Int): String? {
    return read(count)
}

actual fun String.openAsBufferedReader(): Result<BufferedReader> = runCatching {
    // Calling the readFileSync() method
    // to read 'input.txt' file
    val opts: dynamic = Any()
    opts.encoding = "utf8"
    opts.flag = "r"
    // const data = fs.readFileSync('./input.txt', { encoding: 'utf8', flag: 'r' });
    val content = fs.readFileSync(this, opts)
    BufferedReader(content = content as String)
}

actual fun String.wrapAsBufferedReader(): BufferedReader {
    return BufferedReader(content = this)
}
