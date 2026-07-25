package dev.tesserakt.rdf.serialization.common

import dev.tesserakt.rdf.serialization.InternalSerializationApi
import dev.tesserakt.rdf.serialization.core.SuspendingDataStream

/**
 * A special variant of [FileDataSource], reading the file using [`fs.createReadStream`](https://nodejs.org/api/fs.html#fscreatereadstreampath-options)
 */
class FileStreamDataSource(private val filepath: String): SuspendingDataSource {

    @OptIn(InternalSerializationApi::class)
    override suspend fun open(): SuspendingDataStream {
        val opts: dynamic = Any()
        opts.encoding = "utf8"
        val stream = fs.createReadStream(filepath, opts)
        return NodeStreamDataSource(stream).open()
    }

}
