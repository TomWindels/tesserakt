package dev.tesserakt.rdf.serialization.common

import dev.tesserakt.rdf.serialization.core.DataSourceStream

private val fs = js("require('fs')")

actual class FileDataSource actual constructor(private val filepath: String) : DataSource {
    actual override fun open(): DataSourceStream {
        // Calling the readFileSync() method
        // to read 'input.txt' file
        val opts: dynamic = Any()
        opts.encoding = "utf8"
        opts.flag = "r"
        // const data = fs.readFileSync('./input.txt', { encoding: 'utf8', flag: 'r' });
        val content = fs.readFileSync(filepath, opts)
        return DataSourceStream(content = content as String)
    }
}
