package dev.tesserakt.sparql.runtime.incremental.delta

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.runtime.core.Mapping
import kotlin.jvm.JvmInline

sealed interface Delta {

    sealed interface Addition: Delta

    sealed interface Data: Delta {
        val value: Quad
    }

    sealed interface Bindings: Delta {
        val value: Mapping
    }

    @JvmInline
    value class DataAddition(override val value: Quad): Addition, Data {
        override fun toString() = "Additional quad $value"
    }

    @JvmInline
    value class BindingsAddition(override val value: Mapping): Addition, Bindings {
        override fun toString() = "Additional mapping $value"
    }

}
