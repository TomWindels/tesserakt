package dev.tesserakt.sparql.runtime.evaluation

import dev.tesserakt.rdf.types.Quad
import kotlin.jvm.JvmInline

sealed interface Delta

sealed interface AdditionDelta: Delta

sealed interface DeletionDelta: Delta

sealed interface DataDelta: Delta {
    val value: Quad
}

sealed interface MappingDelta: Delta {
    val value: Mapping
    val origin: DataDelta?
}

@JvmInline
value class DataAddition(override val value: Quad): AdditionDelta,
    DataDelta {
    override fun toString() = "Adding of quad $value"
}

data class MappingAddition(
    override val value: Mapping,
    override val origin: DataDelta?
): AdditionDelta,
    MappingDelta {
    override fun toString() = if (origin != null) "Additional mapping $value, caused by $origin" else "Additional mapping $value"
}

@JvmInline
value class DataDeletion(override val value: Quad): DeletionDelta,
    DataDelta {
    override fun toString() = "Removal of quad $value"
}

data class MappingDeletion(
    override val value: Mapping,
    override val origin: DataDelta?
): DeletionDelta,
    MappingDelta {
    override fun toString() = if (origin != null) "Removal of mapping $value, caused by $origin" else "Removal of mapping $value"
}
