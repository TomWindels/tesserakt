package dev.tesserakt.rdf.serialization.common

import java.io.File
import java.io.InputStream
import java.nio.charset.Charset

private val UTF8 = Charset.forName("UTF-8")

/**
 * Deserializes the [file]. See [Serializer.DeserializationProcess] for more information.
 */
fun Serializer.deserialize(file: File, encoding: Charset = UTF8) = deserialize(FileDataSource(file, encoding))

/**
 * Deserializes the [file]. See [Serializer.DeserializationProcess] for more information.
 */
fun Serializer.deserialize(file: File, encoding: String) = deserialize(FileDataSource(file, Charset.forName(encoding)))

/**
 * Deserializes the [stream]. See [Serializer.DeserializationProcess] for more information.
 */
fun Serializer.deserialize(stream: InputStream, encoding: Charset = UTF8) = deserialize(StreamDataSource(stream, encoding))

/**
 * Deserializes the [stream]. See [Serializer.DeserializationProcess] for more information.
 */
fun Serializer.deserialize(stream: InputStream, encoding: String) = deserialize(StreamDataSource(stream, Charset.forName(encoding)))
