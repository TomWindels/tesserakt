package dev.tesserakt.rdf.serialization.common

import dev.tesserakt.rdf.types.Quad

abstract class Serializer {

    /**
     * Standard serializer. May change the order of the individual elements found in [data] in the final output to reduce
     *  the resulting serialised size.
     */
    open fun serialize(data: Collection<Quad>): Iterator<String> {
        return serialize(data = data.iterator())
    }

    /**
     * The most basic form of serialization. The [data] is being iterated over once: the order of elements is not
     *  altered, and will be reflected in the output.
     */
    abstract fun serialize(data: Iterator<Quad>): Iterator<String>

    /**
     * Standard deserialization. The [input] is iterated over once, as long as the returned [Iterator] is being consumed.
     */
    abstract fun deserialize(input: DataSource): Iterator<Quad>

}
