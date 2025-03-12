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
    override fun toString() = "[+] $value"
}

data class MappingAddition(
    override val value: Mapping,
    override val origin: DataDelta?
): AdditionDelta,
    MappingDelta {
    override fun toString() = if (origin != null) "[+] $value ($origin)" else "[+] $value"
}

@JvmInline
value class DataDeletion(override val value: Quad): DeletionDelta,
    DataDelta {
    override fun toString() = "[-] $value"
}

data class MappingDeletion(
    override val value: Mapping,
    override val origin: DataDelta?
): DeletionDelta,
    MappingDelta {
    override fun toString() = if (origin != null) "[-] $value ($origin)" else "[-] $value"
}
