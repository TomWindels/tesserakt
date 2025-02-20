package dev.tesserakt.rdf.serialization.common

import dev.tesserakt.rdf.serialization.util.BufferedString
import dev.tesserakt.rdf.serialization.util.openAsBufferedReader


fun Path.open(bufferSize: Int = 4000): Result<BufferedString> =
    path.openAsBufferedReader().map { reader -> BufferedString(reader = reader, bufferSize = bufferSize) }
