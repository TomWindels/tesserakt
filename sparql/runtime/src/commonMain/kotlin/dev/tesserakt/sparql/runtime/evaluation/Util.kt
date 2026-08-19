package dev.tesserakt.sparql.runtime.evaluation

import dev.tesserakt.sparql.runtime.evaluation.mapping.Mapping
import dev.tesserakt.sparql.runtime.stream.Stream
import dev.tesserakt.sparql.runtime.stream.mapped


operator fun MappingDelta.plus(other: MappingDelta): MappingDelta? {
    return when (this) {
        is MappingAddition if other is MappingAddition -> value.join(other.value)?.let { MappingAddition(it) }
        is MappingDeletion if other is MappingDeletion ->
            value.join(other.value)?.let {
                MappingDeletion(
                    value = it,
                )
            }

        else -> null
    }
}

inline fun MappingDelta.map(transform: (Mapping) -> Mapping) = when (this) {
    is MappingAddition -> MappingAddition(
        transform(value),
    )
    is MappingDeletion -> MappingDeletion(
        transform(value),
    )
}


inline fun MappingDelta.mapToStream(transform: (Mapping) -> Stream<Mapping>) = when (this) {
    is MappingAddition -> transform(value).mapped {
        MappingAddition(
            it,
        )
    }
    is MappingDeletion -> transform(value).mapped {
        MappingDeletion(
            it,
        )
    }
}
