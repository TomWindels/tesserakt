package dev.tesserakt.rdf.serialization

import dev.tesserakt.rdf.serialization.common.DataSource
import dev.tesserakt.rdf.serialization.common.Serializer
import dev.tesserakt.rdf.serialization.util.BufferedString
import dev.tesserakt.rdf.types.Quad

object NTriples: Serializer() {

    @OptIn(InternalSerializationApi::class)
    override fun deserialize(input: DataSource): Iterator<Quad> {
        return Deserializer(BufferedString(input.open()))
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

    private fun Quad.Term.encoded(): String {
        return when (this) {
            is Quad.BlankTerm -> "_:b$id"
            is Quad.Literal -> toString()
            is Quad.NamedTerm -> "<$value>"
        }
    }

}
