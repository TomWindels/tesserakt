package dev.tesserakt.sparql.runtime.incremental.delta

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.runtime.core.Mapping
import kotlin.jvm.JvmInline

sealed interface Delta {

    sealed interface Addition: Delta

    sealed interface Deletion: Delta

    sealed interface Data: Delta {
        val value: Quad
    }

    sealed interface Bindings: Delta {
        val value: Mapping
    }

    @JvmInline
    value class DataAddition(override val value: Quad): Addition, Data {
        override fun toString() = "Adding of quad $value"
    }

    @JvmInline
    value class BindingsAddition(override val value: Mapping): Addition, Bindings {
        override fun toString() = "Additional mapping $value"
    }

    @JvmInline
    value class DataDeletion(override val value: Quad): Deletion, Data {
        override fun toString() = "Removal of quad $value"
    }

    @JvmInline
    value class BindingsDeletion(override val value: Mapping): Deletion, Bindings {
        override fun toString() = "Removal of mapping $value"
    }

}
