package dev.tesserakt.sparql.runtime.evaluation

import dev.tesserakt.rdf.types.EncodedQuad
import dev.tesserakt.sparql.runtime.evaluation.mapping.Mapping
import kotlin.jvm.JvmInline

sealed interface Delta

sealed interface AdditionDelta: Delta

sealed interface DeletionDelta: Delta

sealed interface DataDelta: Delta {
    val value: EncodedQuad
}

sealed interface MappingDelta: Delta {
    val value: Mapping
}

@JvmInline
value class DataAddition(override val value: EncodedQuad): AdditionDelta, DataDelta {
    override fun toString() = "[+] $value"
}

@JvmInline
value class MappingAddition(
    override val value: Mapping,
): AdditionDelta, MappingDelta {
    override fun toString() = "[+] $value"
}

@JvmInline
value class DataDeletion(override val value: EncodedQuad): DeletionDelta, DataDelta {
    override fun toString() = "[-] $value"
}

@JvmInline
value class MappingDeletion(
    override val value: Mapping,
): DeletionDelta, MappingDelta {
    override fun toString() = "[-] $value"
}
