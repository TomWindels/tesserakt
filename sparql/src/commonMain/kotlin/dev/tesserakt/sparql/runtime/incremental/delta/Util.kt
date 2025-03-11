package dev.tesserakt.sparql.runtime.incremental.delta

import dev.tesserakt.sparql.runtime.core.Mapping
import dev.tesserakt.sparql.runtime.incremental.stream.Stream
import dev.tesserakt.sparql.runtime.incremental.stream.mapped
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


internal inline fun MappingDelta.mapToStream(transform: (Mapping) -> Stream<Mapping>) = when (this) {
    is MappingAddition -> transform(value).mapped { MappingAddition(it, origin = origin) }
    is MappingDeletion -> transform(value).mapped { MappingDeletion(it, origin = origin) }
}
