package dev.tesserakt.sparql.runtime.incremental.delta

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.runtime.core.Mapping
import dev.tesserakt.util.compatibleWith

internal inline fun addition(quad: Quad) = DataAddition(quad)

internal inline fun addition(mapping: Mapping) = MappingAddition(mapping)

internal operator fun MappingDelta.plus(other: MappingDelta): MappingDelta? {
    return when {
        this is MappingAddition &&
        other is MappingAddition &&
        value.compatibleWith(other.value) ->
            MappingAddition(value = value + other.value)

        else -> null
    }
}

internal inline fun MappingDelta.map(transform: (Mapping) -> Mapping) = when (this) {
    is MappingAddition -> MappingAddition(transform(value))
    is MappingDeletion -> MappingDeletion(transform(value))
}


internal inline fun MappingDelta.transform(transform: (Mapping) -> Collection<Mapping>) = when (this) {
    is MappingAddition -> transform(value).map { MappingAddition(it) }
    is MappingDeletion -> transform(value).map { MappingDeletion(it) }
}
