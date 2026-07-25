package dev.tesserakt.rdf.serialization.ntriples

import dev.tesserakt.rdf.serialization.common.Format
import dev.tesserakt.rdf.serialization.common.Serializer

object NTriples: Format<Nothing>() {
    override fun default(): Serializer {
        return NTriplesSerializer
    }

    override fun build(config: Nothing.() -> Unit): Serializer {
        // the config goes ignored
        return NTriplesSerializer
    }
}
