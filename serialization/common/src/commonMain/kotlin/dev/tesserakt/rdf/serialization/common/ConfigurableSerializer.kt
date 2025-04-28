package dev.tesserakt.rdf.serialization.common

import dev.tesserakt.rdf.types.Quad

abstract class ConfigurableSerializer<C>: Serializer() {

    abstract val DEFAULT: C

    /**
     * Basic form of customisable serialization. The [data] is being iterated over once: the order of elements is not
     *  altered, and will be reflected in the output.
     */
    abstract fun serialize(data: Iterator<Quad>, config: C): Iterator<String>

    /**
     * Customisable serializer. May change the order of the individual elements found in [data] in the final output to
     *  reduce the resulting serialised size.
     */
    open fun serialize(data: Collection<Quad>, config: C): Iterator<String> {
        return serialize(data = data.iterator(), config = config)
    }

}
