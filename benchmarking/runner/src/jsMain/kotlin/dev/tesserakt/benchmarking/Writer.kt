package dev.tesserakt.benchmarking

private val fs = js("require('fs')")
private val opt = Any()
    .apply { asDynamic().flag = "a" }

actual class Writer actual constructor(private val filepath: String) {

    actual fun write(text: String) {
        fs.writeFileSync(filepath, text, opt)
    }

    actual fun close() {
        /* nothing to do, not buffered */
    }

}
