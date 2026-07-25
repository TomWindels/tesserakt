package dev.tesserakt.rdf.serialization.common

import java.io.File
import java.io.InputStream

/**
 * Deserializes the [file]. See [Serializer.DeserializationProcess] for more information.
 */
fun Serializer.deserialize(file: File) = deserialize(FileDataSource(file))

/**
 * Deserializes the [stream]. See [Serializer.DeserializationProcess] for more information.
 */
fun Serializer.deserialize(stream: InputStream) = deserialize(StreamDataSource(stream))
