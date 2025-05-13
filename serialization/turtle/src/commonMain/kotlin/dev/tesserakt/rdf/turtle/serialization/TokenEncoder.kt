package dev.tesserakt.rdf.turtle.serialization

import dev.tesserakt.rdf.ontology.RDF
import dev.tesserakt.rdf.types.Quad

/**
 * Token encoder, converting an iterator of [Quad]s into an iterator of [TurtleToken]s that can be converted into a
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
) : Iterator<TurtleToken> {

    constructor(collection: Collection<Quad>) : this(collection.orderedIterator())

    companion object {

        operator fun invoke(source: Iterable<Quad>) = when (source) {
            is Collection<Quad> -> TokenEncoder(source)
            else -> TokenEncoder(source.iterator())
        }

    }

    // last emitted variables, used to track what sequence should be sent
    // set back to null if it's guaranteed that it has to be resent (i.e. during graph block change)
    private var s: Quad.Term? = null
    private var p: Quad.NamedTerm? = null
    private var o: Quad.Term? = null

    private var current: Quad? = if (source.hasNext()) source.next() else null

    private var nextToken: TurtleToken? = null

    override fun hasNext(): Boolean {
        if (nextToken != null) {
            return true
        }
        incrementToken()
        return nextToken != null
    }

    override fun next(): TurtleToken {
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
            current == null && o != null -> {
                s = null
                p = null
                o = null
                nextToken = TurtleToken.Structural.StatementTermination
            }

            current == null -> {
                nextToken = null
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
                    upcoming == null && nextToken != TurtleToken.Structural.StatementTermination -> {
                        //  we also have to reset our state
                        s = null
                        p = null
                        o = null
                        nextToken = TurtleToken.Structural.StatementTermination
                    }

                    upcoming == null -> {
                        nextToken = null
                    }

                    upcoming.s != s -> {
                        // resetting state
                        s = null
                        p = null
                        o = null
                        nextToken = TurtleToken.Structural.StatementTermination
                    }

                    upcoming.p != p -> {
                        // resetting state
                        p = null
                        o = null
                        nextToken = TurtleToken.Structural.PredicateTermination
                    }

                    upcoming.o != o -> {
                        // resetting state
                        o = null
                        nextToken = TurtleToken.Structural.ObjectTermination
                    }
                }
            }
        }
    }

    private fun consumeQuad() {
        current = if (source.hasNext()) source.next() else null
    }

    private fun Quad.Graph.toGraphToken(): TurtleToken = when (this) {
        is Quad.BlankTerm -> toToken()
        is Quad.NamedTerm -> toToken()
        Quad.DefaultGraph -> throw IllegalArgumentException("Default graphs are not explicitly encoded using this encoder!")
    }

    private fun Quad.Term.toToken(): TurtleToken = when (this) {
        is Quad.BlankTerm ->
            TurtleToken.PrefixedTerm(prefix = "_", value = "b$id")

        is Quad.Literal ->
            TurtleToken.LiteralTerm(value = value, type = type.toToken() as TurtleToken.NonLiteralTerm)

        RDF.type -> TurtleToken.Keyword.TypePredicate

        is Quad.NamedTerm ->
            TurtleToken.Term(value = value)
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
