package dev.tesserakt.rdf.trig.serialization

import dev.tesserakt.rdf.ontology.RDF
import dev.tesserakt.rdf.types.Quad

/**
 * Token encoder, converting an iterator of [Quad]s into an iterator of [TriGToken]s that can be converted into a
 *  serialized representation of the [Quad] iterable. See the constructor overload taking a [Collection] as a parameter
 *  for a more optimised token stream, at the cost of copying and reordering the underlying quad collection.
 *
 * Important: this token encoder does **NOT** convert blank representations into blank statements using `[]` syntax, as
 *  it is never guaranteed for an iterable of [Quad]s to stop its use of a blank node after a given point in time.
 *  Therefore, to support additional uses of already defined blank nodes later in the iterable, the `_:b` representation
 *  is always used.
 */
internal class TokenEncoder(
    private val source: Iterator<Quad>
) : Iterator<TriGToken> {

    constructor(collection: Collection<Quad>) : this(collection.orderedIterator())

    companion object {

        operator fun invoke(source: Iterable<Quad>) = when (source) {
            is Collection<Quad> -> TokenEncoder(source)
            else -> TokenEncoder(source.iterator())
        }

    }

    /* iteration state, tracking the ongoing graph block, if any */
    private var inGraphBlock = false
    private var g: Quad.Graph = Quad.DefaultGraph

    // last emitted variables, used to track what sequence should be sent
    // set back to null if it's guaranteed that it has to be resent (i.e. during graph block change)
    private var s: Quad.Term? = null
    private var p: Quad.NamedTerm? = null
    private var o: Quad.Term? = null

    private var current: Quad? = if (source.hasNext()) source.next() else null

    private var nextToken: TriGToken? = null

    override fun hasNext(): Boolean {
        if (nextToken != null) {
            return true
        }
        incrementToken()
        return nextToken != null
    }

    override fun next(): TriGToken {
        var token = nextToken
        if (token != null) {
            nextToken = null
            return token
        }
        incrementToken()
        token = nextToken
        nextToken = null
        return token ?: throw NoSuchElementException()
    }

    private fun incrementToken() {
        val current = current
        when {
            current == null && inGraphBlock -> {
                s = null
                p = null
                o = null
                inGraphBlock = false
                nextToken = TriGToken.Structural.GraphStatementEnd
            }

            current == null && o != null -> {
                s = null
                p = null
                o = null
                nextToken = TriGToken.Structural.StatementTermination
            }

            current == null -> {
                nextToken = null
            }

            g != current.g && inGraphBlock -> {
                inGraphBlock = false
                nextToken = TriGToken.Structural.GraphStatementEnd
            }

            g != current.g && !inGraphBlock && current.g == Quad.DefaultGraph -> {
                g = current.g
                //  we also have to reset our state
                s = null
                p = null
                o = null
                // but the next token has to be set by the next iteration
                incrementToken()
            }

            g != current.g && !inGraphBlock -> {
                g = current.g
                //  we also have to reset our state
                s = null
                p = null
                o = null
                nextToken = current.g.toGraphToken()
            }

            g == current.g && !inGraphBlock && g != Quad.DefaultGraph -> {
                inGraphBlock = true
                nextToken = TriGToken.Structural.GraphStatementStart
            }

            s != current.s -> {
                s = current.s
                //  we also have to reset our state
                p = null
                o = null
                nextToken = current.s.toToken()
            }

            p != current.p -> {
                p = current.p
                o = null
                nextToken = current.p.toToken()
            }

            o != current.o -> {
                o = current.o
                nextToken = current.o.toToken()
            }

            else -> {
                // this quad has been fully emitted, meaning we need to see what the next one is about to properly
                //  terminate this sequence
                consumeQuad()
                val upcoming = this.current
                when {
                    // first ensuring we're fully terminating the stream if there's no input remaining
                    upcoming == null && nextToken != TriGToken.Structural.StatementTermination -> {
                        //  we also have to reset our state
                        s = null
                        p = null
                        o = null
                        nextToken = TriGToken.Structural.StatementTermination
                    }

                    upcoming == null && inGraphBlock -> {
                        nextToken = TriGToken.Structural.GraphStatementEnd
                        inGraphBlock = false
                    }

                    upcoming == null -> {
                        nextToken = null
                    }

                    upcoming.s != s -> {
                        // resetting state
                        s = null
                        p = null
                        o = null
                        nextToken = TriGToken.Structural.StatementTermination
                    }

                    upcoming.p != p -> {
                        // resetting state
                        p = null
                        o = null
                        nextToken = TriGToken.Structural.PredicateTermination
                    }

                    upcoming.o != o -> {
                        // resetting state
                        o = null
                        nextToken = TriGToken.Structural.ObjectTermination
                    }
                }
            }
        }
    }

    private fun consumeQuad() {
        current = if (source.hasNext()) source.next() else null
    }

    private fun Quad.Graph.toGraphToken(): TriGToken = when (this) {
        is Quad.BlankTerm -> toToken()
        is Quad.NamedTerm -> toToken()
        Quad.DefaultGraph -> throw IllegalArgumentException("Default graphs are not explicitly encoded using this encoder!")
    }

    private fun Quad.Term.toToken(): TriGToken = when (this) {
        is Quad.BlankTerm ->
            TriGToken.PrefixedTerm(prefix = "_", value = "b$id")

        is Quad.Literal ->
            TriGToken.LiteralTerm(value = value, type = type.toToken() as TriGToken.NonLiteralTerm)

        RDF.type -> TriGToken.Keyword.TypePredicate

        is Quad.NamedTerm ->
            TriGToken.Term(value = value)
    }

}

/**
 * Returns a custom iterator that iterates over the collection in an ordered fashion, where all quads are returned
 *  grouped by graph, followed by subject and predicate
 */
private fun Collection<Quad>.orderedIterator() = this
    .groupBy { quad -> quad.g }
    .mapValues { graphs ->
        graphs.value
            .groupBy { quad -> quad.s }
            .mapValues { subjects -> subjects.value.groupBy { quad -> quad.p } }
    }
    .flatMap { graphs -> graphs.value.flatMap { subjects -> subjects.value.flatMap { predicates -> predicates.value } } }
    .iterator()
