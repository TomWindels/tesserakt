package dev.tesserakt.stream.ldes

import dev.tesserakt.rdf.types.Quad

class InvalidStreamFormatException internal constructor(
    identifier: Quad.NamedTerm,
    message: String
): RuntimeException("Stream $identifier is not correctly formatted! $message")
