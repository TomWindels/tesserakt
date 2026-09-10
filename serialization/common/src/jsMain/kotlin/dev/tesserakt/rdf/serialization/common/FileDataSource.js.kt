package dev.tesserakt.rdf.serialization.common

import dev.tesserakt.rdf.serialization.InternalSerializationApi
import dev.tesserakt.rdf.serialization.core.DataStream
import dev.tesserakt.rdf.serialization.core.TextDataStream

actual class FileDataSource actual constructor(
    private val filepath: String,
    private val encoding: String,
) : DataSource {

    companion object {
        private val encodingLut = mapOf(
            "UTF-8" to "utf8",
        )
    }

    @OptIn(InternalSerializationApi::class)
    actual override fun open(): DataStream {
        val opts: dynamic = Any()
        // if we have an alternative name that the platform expects, we map it here;
        //  otherwise, we trust what the user requested is aware of what the JS runtime expects
        opts.encoding = encodingLut[encoding] ?: encoding
        opts.flag = "r"
        // const data = fs.readFileSync('path', { encoding: 'utf8', flag: 'r' });
        val content = FileSystem.readFileSync(filepath, opts)
        return TextDataStream(content = content)
    }
}
