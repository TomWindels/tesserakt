package dev.tesserakt.rdf.serialization.common

import dev.tesserakt.rdf.serialization.InternalSerializationApi
import dev.tesserakt.rdf.serialization.core.DataStream
import dev.tesserakt.rdf.serialization.core.TextDataStream

actual class FileDataSource actual constructor(private val filepath: String) : DataSource {
    @OptIn(InternalSerializationApi::class)
    actual override fun open(): DataStream {
        // Calling the readFileSync() method
        // to read 'input.txt' file
        val opts: dynamic = Any()
        opts.encoding = "utf8"
        opts.flag = "r"
        // const data = fs.readFileSync('./input.txt', { encoding: 'utf8', flag: 'r' });
        val content = fs.readFileSync(filepath, opts)
        return TextDataStream(content = content as String)
    }
}
