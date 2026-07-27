package dev.tesserakt.sparql.runtime.evaluation.context

import dev.tesserakt.rdf.types.*
import dev.tesserakt.sparql.runtime.evaluation.BindingIdentifier
import dev.tesserakt.sparql.runtime.evaluation.TermIdentifier
import dev.tesserakt.sparql.runtime.evaluation.mapping.BitsetMapping
import dev.tesserakt.sparql.types.QueryStructure

class StoreBackedQueryContext(
    ast: QueryStructure,
    /**
     * The backing storage structure, which also owns context used by the data source, used to map between term IDs and
     *  term values
     */
    private val source: Store,
) : QueryContext {

    private val bindings = BindingsContext(ast)

    private val context: EncodingContext
        get() = source.context

    override fun iter(
        s: EncodedQuadElement,
        p: EncodedQuadElement,
        o: EncodedQuadElement,
        g: EncodedQuadElement
    ): Iterator<EncodedQuad> {
        return source.encodedIter(s, p, o, g)
    }

    override fun newAnonymousBinding(): Int {
        return bindings.newAnonymousBinding()
    }

    override fun resolveBinding(value: String): Int {
        return bindings.encode(value)
    }

    override fun resolveBinding(id: Int): String {
        return bindings.decode(id)
    }

    override fun resolveTerm(value: Quad.Element): Int {
        // NOTE: if we support the creation of new quad elements within the query body (i.e. BIND expr) we should
        //  expose the creation of such terms through a new dedicated method, which starts counting from Int.MAX_VALUE
        //  downwards (or negative integers, starting from -1?)
        //  then, when resolving, we can use bound checks to make sure we use the appropriate
        //  context (store sourced term vs query generated term)
        return context.encode(value)
            ?: throw NoSuchElementException("Failed to encode term `$value`")
    }

    override fun resolveTerm(id: Int): Quad.Element {
        // see note above
        return context.decode(id)
            ?: throw NoSuchElementException("Failed to decode term with ID $id")
    }

    override fun mappingFromValues(terms: Iterable<Pair<String, Quad.Element>>): BitsetMapping {
        return BitsetMapping(this, terms)
    }

    override fun mappingFromIdentifiers(terms: Iterable<Pair<BindingIdentifier, TermIdentifier>>): BitsetMapping {
        return BitsetMapping(terms)
    }

    override fun emptyMapping(): BitsetMapping {
        return BitsetMapping.EMPTY
    }

}
