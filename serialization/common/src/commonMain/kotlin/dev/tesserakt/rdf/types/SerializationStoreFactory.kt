package dev.tesserakt.rdf.types

import dev.tesserakt.rdf.serialization.DelicateSerializationApi
import dev.tesserakt.rdf.serialization.common.*

/**
 * A [Store] factory method, creating a new instance with contents obtained from processing the [source] using the
 *  provided [format]
 */
fun Store(source: DataSource, format: Format<*>): Store {
    return serializer(format).deserialize(source).toStore()
}

/**
 * A [Store] factory method, creating a new instance with contents obtained from processing the [source] using the
 *  provided [format]
 */
suspend fun Store(source: SuspendingDataSource, format: Format<*>): Store {
    return serializer(format).deserialize(source).toStore()
}

/**
 * A [Store] factory method, creating a new instance with contents obtained from processing the [text] representation
 *  using the provided [format]. Discouraged as the entire store content has to be in memory.
 */
@DelicateSerializationApi
fun Store(text: String, format: Format<*>): Store {
    return serializer(format).deserialize(text).toStore()
}
