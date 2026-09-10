package dev.tesserakt.sparql.runtime.evaluation.context

import dev.tesserakt.rdf.types.EncodedQuad
import dev.tesserakt.rdf.types.EncodedQuadElement
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.runtime.stream.emptyIterator

interface QueryContext {

    /**
     * If this context is tied to a backing structure, such as a [dev.tesserakt.rdf.types.Store], the initial
     *  data can be retrieved directly through this method.
     */
    fun iter(
        s: EncodedQuadElement = Int.MIN_VALUE,
        p: EncodedQuadElement = Int.MIN_VALUE,
        o: EncodedQuadElement = Int.MIN_VALUE,
        g: EncodedQuadElement = Int.MIN_VALUE,
    ): Iterator<EncodedQuad> {
        // default behaviour has no backing structure, so there's no data to retrieve
        return emptyIterator()
    }

    fun newAnonymousBinding(): Int

    fun resolveBinding(value: String): Int

    fun resolveTerm(value: Quad.Element): Int

    fun resolveBinding(id: Int): String

    fun resolveTerm(id: Int): Quad.Element

}
