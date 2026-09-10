package dev.tesserakt.rdf.serialization.common

import dev.tesserakt.rdf.serialization.InternalSerializationApi

/**
 * Exception thrown when an error occurs when deserializing a [dev.tesserakt.rdf.types.Quad]
 *  through [Serializer.DeserializationProcess]'s [Serializer.DeserializationProcess.next] / [Serializer.DeserializationProcess.hasNext].
 */
class DeserializationException
@InternalSerializationApi
constructor(message: String, cause: Throwable): RuntimeException(message, cause)
