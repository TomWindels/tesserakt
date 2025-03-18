package dev.tesserakt.sparql.runtime.query

import dev.tesserakt.sparql.runtime.evaluation.MappingDelta
import dev.tesserakt.sparql.runtime.stream.Stream

interface StatelessFilter {

    /**
     * Filters the [input] stream
     */
    fun filter(input: Stream<MappingDelta>): Stream<MappingDelta>

}
