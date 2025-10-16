package dev.tesserakt.sparql.types

import kotlin.jvm.JvmInline

@JvmInline
value class Ordering(
    val elements: List<Element>
) : QueryAtom {

    data class Element(
        val binding: Binding,
        val mode: Mode,
    ) {
        enum class Mode {
            Ascending,
            Descending,
        }
    }

    init {
        require(elements.isNotEmpty()) { "Invalid structure! Expected at least one binding to use for ordering!" }
    }

}
