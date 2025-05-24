package dev.tesserakt.sparql.runtime.evaluation

import dev.tesserakt.sparql.runtime.evaluation.mapping.Mapping
import dev.tesserakt.sparql.runtime.stream.Stream
import dev.tesserakt.sparql.runtime.stream.mapped


operator fun MappingDelta.plus(other: MappingDelta): MappingDelta? {
    return when {
        this is MappingAddition &&
        other is MappingAddition -> value.join(other.value)?.let { MappingAddition(it, origin) }

        this is MappingDeletion &&
        other is MappingDeletion ->
            value.join(other.value)?.let {
                MappingDeletion(
                    value = it,
                    origin = origin
                )
            }

        else -> null
    }
}

inline fun MappingDelta.map(transform: (Mapping) -> Mapping) = when (this) {
    is MappingAddition -> MappingAddition(
        transform(value),
        origin = origin
    )
    is MappingDeletion -> MappingDeletion(
        transform(value),
        origin = origin
    )
}


inline fun MappingDelta.mapToStream(transform: (Mapping) -> Stream<Mapping>) = when (this) {
    is MappingAddition -> transform(value).mapped {
        MappingAddition(
            it,
            origin = origin
        )
    }
    is MappingDeletion -> transform(value).mapped {
        MappingDeletion(
            it,
            origin = origin
        )
    }
}
