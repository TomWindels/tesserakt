package dev.tesserakt.sparql.runtime.incremental.delta

import dev.tesserakt.sparql.runtime.core.Mapping
import dev.tesserakt.util.compatibleWith


internal operator fun MappingDelta.plus(other: MappingDelta): MappingDelta? {
    return when {
        this is MappingAddition &&
        other is MappingAddition &&
        value.compatibleWith(other.value) ->
            MappingAddition(value = value + other.value, origin = origin)

        this is MappingDeletion &&
        other is MappingDeletion &&
        value.compatibleWith(other.value) ->
            MappingDeletion(value = value + other.value, origin = origin)

        else -> null
    }
}

internal inline fun MappingDelta.map(transform: (Mapping) -> Mapping) = when (this) {
    is MappingAddition -> MappingAddition(transform(value), origin = origin)
    is MappingDeletion -> MappingDeletion(transform(value), origin = origin)
}


internal inline fun MappingDelta.transform(transform: (Mapping) -> Iterable<Mapping>) = when (this) {
    is MappingAddition -> transform(value).map { MappingAddition(it, origin = origin) }
    is MappingDeletion -> transform(value).map { MappingDeletion(it, origin = origin) }
}
