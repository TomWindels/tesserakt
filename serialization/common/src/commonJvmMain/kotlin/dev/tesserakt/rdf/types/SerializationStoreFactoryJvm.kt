package dev.tesserakt.rdf.types

import dev.tesserakt.concurrent.globalTaskRunner
import dev.tesserakt.rdf.serialization.common.Format
import dev.tesserakt.rdf.serialization.common.deserialize
import dev.tesserakt.rdf.serialization.common.serializer
import java.io.File

/**
 * A [Store] factory method, creating a new instance with contents obtained from processing the [source] using the
 *  provided [format]
 */
fun Store(source: File, format: Format<*>): Store {
    val proc = serializer(format).deserialize(source)
    // if the file length is big enough, we attempt to multi-thread the process if configured
    // putting the threshold at 100 MB
    val fileSize = source.length()
    val capacityHint = (fileSize / 1000L).toInt()
    val enableMultithreading = fileSize > 100_000_000L
    val iter = if (enableMultithreading) {
        globalTaskRunner.buffered(proc)
    } else {
        proc
    }
    // making sure we close the iterator on our way out
    return iter.use {
        it.toStore(capacityHint = capacityHint)
    }
}
