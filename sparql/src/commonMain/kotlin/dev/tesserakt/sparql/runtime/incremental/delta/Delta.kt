package dev.tesserakt.sparql.runtime.incremental.delta

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.runtime.core.Mapping
import kotlin.jvm.JvmInline

sealed interface Delta

sealed interface AdditionDelta: Delta

sealed interface DeletionDelta: Delta

sealed interface DataDelta: Delta {
    val value: Quad
}

internal sealed interface MappingDelta: Delta {
    val value: Mapping
}

@JvmInline
value class DataAddition(override val value: Quad): AdditionDelta, DataDelta {
    override fun toString() = "Adding of quad $value"
}

@JvmInline
internal value class MappingAddition(override val value: Mapping): AdditionDelta, MappingDelta {
    override fun toString() = "Additional mapping $value"
}

@JvmInline
value class DataDeletion(override val value: Quad): DeletionDelta, DataDelta {
    override fun toString() = "Removal of quad $value"
}

@JvmInline
internal value class MappingDeletion(override val value: Mapping): DeletionDelta, MappingDelta {
    override fun toString() = "Removal of mapping $value"
}
