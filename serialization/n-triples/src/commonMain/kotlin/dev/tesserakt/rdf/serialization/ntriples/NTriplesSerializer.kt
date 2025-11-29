package dev.tesserakt.rdf.serialization.ntriples

import dev.tesserakt.rdf.serialization.InternalSerializationApi
import dev.tesserakt.rdf.serialization.common.Serializer
import dev.tesserakt.rdf.serialization.core.DataStream
import dev.tesserakt.rdf.serialization.util.BufferedString
import dev.tesserakt.rdf.types.Quad

internal object NTriplesSerializer: Serializer() {

    @OptIn(InternalSerializationApi::class)
    override fun deserialize(input: DataStream): Iterator<Quad> {
        return Deserializer(BufferedString(input))
    }

    override fun serialize(data: Iterator<Quad>): Iterator<String> = iterator {
        data.forEach { quad ->
            yield(quad.s.encoded())
            yield(" ")
            yield(quad.p.encoded())
            yield(" ")
            yield(quad.o.encoded())
            yield(" .\n")
        }
    }

    private fun Quad.Element.encoded(): String {
        return when (this) {
            is Quad.BlankTerm -> "_:b$id"
            is Quad.Literal -> toString()
            is Quad.LangString -> toString()
            is Quad.NamedTerm -> "<$value>"
            Quad.DefaultGraph -> throw IllegalStateException("Graph terms (including default graph) are not encoded in the N-Triples format!")
        }
    }

}
