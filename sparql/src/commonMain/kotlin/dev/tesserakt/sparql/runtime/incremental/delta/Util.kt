package dev.tesserakt.sparql.runtime.incremental.delta

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.runtime.core.Mapping
import dev.tesserakt.util.compatibleWith

internal inline fun addition(quad: Quad) = Delta.DataAddition(quad)

internal inline fun addition(mapping: Mapping) = Delta.BindingsAddition(mapping)

operator fun Delta.Bindings.plus(other: Delta.Bindings): Delta.Bindings? {
    return when {
        this is Delta.BindingsAddition &&
        other is Delta.BindingsAddition &&
        value.compatibleWith(other.value) ->
            Delta.BindingsAddition(value = value + other.value)

        else -> null
    }
}

inline fun Delta.Bindings.map(transform: (Mapping) -> Mapping) = when (this) {
    is Delta.BindingsAddition -> Delta.BindingsAddition(transform(value))
}


inline fun Delta.Bindings.transform(transform: (Mapping) -> Collection<Mapping>) = when (this) {
    is Delta.BindingsAddition -> transform(value).map { Delta.BindingsAddition(it) }
}
