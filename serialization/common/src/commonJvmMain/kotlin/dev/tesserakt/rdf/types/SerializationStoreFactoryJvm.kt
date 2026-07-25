package dev.tesserakt.rdf.types

import dev.tesserakt.rdf.serialization.common.Format
import dev.tesserakt.rdf.serialization.common.deserialize
import dev.tesserakt.rdf.serialization.common.serializer
import java.io.File

/**
 * A [Store] factory method, creating a new instance with contents obtained from processing the [source] using the
 *  provided [format]
 */
fun Store(source: File, format: Format<*>): Store {
    return serializer(format).deserialize(source).toStore()
}
