package dev.tesserakt.rdf.turtle.serialization

import dev.tesserakt.rdf.ontology.RDF
import dev.tesserakt.rdf.ontology.XSD
import dev.tesserakt.rdf.types.Quad

// TODO: blank node syntax `[]` support
// TODO: improved exception handling

internal class Deserializer(private val source: Iterator<TurtleToken>) : Iterator<Quad> {

    enum class Position {
        Subject,
        Predicate,
        Object,
    }

    /* state/input logic */

    private var position = Position.Subject
    private val prefixes = mutableMapOf<String /* prefix */, String /* uri */>()
    private val blanks = mutableMapOf<String /* serialized label */, Quad.BlankTerm>()
    private var base = ""
    private var s: Quad.Term? = null
    private var p: Quad.NamedTerm? = null
    private var o: Quad.Term? = null

    /* iterator/output logic */

    private var next: Quad? = prepareNext()

    override fun hasNext(): Boolean {
        if (next != null) {
            return true
        }
        next = prepareNext()
        return next != null
    }

    override fun next(): Quad {
        val result = next ?: prepareNext() ?: throw NoSuchElementException("No quads available!")
        next = prepareNext()
        return result
    }

    private fun prepareNext(): Quad? {
        if (!source.hasNext()) {
            return null
        }
        // consuming tokens until we reach something we can work with
        // from hereon out, we can assure it's a valid position-specific token
        var token = consumeUntilInsideStatement()
        while (true) {
            when {
                token == null -> return null

                position == Position.Subject -> {
                    check(token is TurtleToken.TermToken) {
                        "$token is not a valid subject / graph term"
                    }
                    val resolved = resolve(token)
                    s = resolved
                    position = Position.Predicate
                    token = nextOrBail()
                }

                position == Position.Predicate && token is TurtleToken.TermToken -> {
                    val predicate = resolve(token)
                    p = predicate as? Quad.NamedTerm
                        ?: throw IllegalStateException("$predicate is not a valid predicate term!")
                    position = Position.Object
                    token = nextOrBail()
                }

                position == Position.Predicate && token == TurtleToken.Structural.TypePredicate -> {
                    p = RDF.type
                    position = Position.Object
                    token = nextOrBail()
                }

                position == Position.Predicate -> {
                    throw IllegalStateException("Invalid predicate token: $token")
                }

                position == Position.Object -> {
                    // FIXME: blank objects
                    o = when (token) {
                        is TurtleToken.TermToken -> {
                            resolve(token)
                        }

                        TurtleToken.Structural.TrueLiteral -> {
                            Quad.Literal(value = "true", type = XSD.boolean)
                        }

                        TurtleToken.Structural.FalseLiteral -> {
                            Quad.Literal(value = "false", type = XSD.boolean)
                        }

                        else -> unexpectedToken(token)
                    }
                    val result = Quad(
                        s = s!!,
                        p = p!!,
                        o = o!!,
                    )
                    // depending on what this next token is, we either adjust the position and yield, update the graph
                    //  value and yield
                    while (true) {
                        when (val next: TurtleToken? = nextOrNull()) {
                            null -> {
                                break
                            }

                            TurtleToken.Structural.StatementTermination -> {
                                position = Position.Subject
                                break
                            }

                            TurtleToken.Structural.PredicateTermination -> {
                                position = Position.Predicate
                                break
                            }

                            TurtleToken.Structural.ObjectTermination -> {
                                position = Position.Object
                                break
                            }

                            else -> unexpectedToken(next)
                        }
                    }
                    return result
                }
            }
        }
    }

    /**
     * Consumes tokens, processing prefixes in the process, until reaching the start of the next statement, returning
     *  said start, or `null` if no statement follows. If already inside a statement according to the [position] state,
     *  this method will immediately return.
     */
    private fun consumeUntilInsideStatement(): TurtleToken? {
        if (!source.hasNext()) {
            return null
        }
        if (position != Position.Subject) {
            // we are inside a statement, meaning we shouldn't encounter any prefixes now and can
            //  bail early
            return nextOrBail()
        }
        var token = nextOrNull()
        do {
            when (token) {
                null -> return null

                TurtleToken.Structural.BaseAnnotationA,
                TurtleToken.Structural.BaseAnnotationB -> {
                    val uri = nextOrBail()
                    check(uri is TurtleToken.Term) { "Invalid base value `${uri}`" }
                    base = uri.value
                    token = nextOrNull()
                    if (token == TurtleToken.Structural.StatementTermination) {
                        token = nextOrNull()
                    }
                }

                TurtleToken.Structural.PrefixAnnotationA,
                TurtleToken.Structural.PrefixAnnotationB -> {
                    processPrefix()
                    token = nextOrNull()
                    if (token == TurtleToken.Structural.StatementTermination) {
                        token = nextOrNull()
                    }
                }

                is TurtleToken.TermToken -> {
                    // found ourselves a statement, we can continue
                    break
                }

                else -> unexpectedToken(token)
            }
        } while (source.hasNext())
        // if we bailed because we reached the end, we should return null
        if (!source.hasNext()) {
            return null
        }
        return token
    }

    private fun processPrefix() {
        val prefix = nextOrBail()
        check(prefix is TurtleToken.PrefixedTerm && prefix.value.isEmpty())
        check(prefix.prefix !in prefixes) {
            "The prefix ${prefix.prefix} is already registered as ${prefixes[prefix.prefix]}"
        }
        val uri = nextOrBail()
        check(uri is TurtleToken.Term)
        prefixes[prefix.prefix] = uri.value
    }

    private fun resolve(term: TurtleToken.TermToken): Quad.Term {
        return when (term) {
            is TurtleToken.LiteralTerm -> {
                val type = resolve(term.type) as? Quad.NamedTerm
                    ?: throw IllegalStateException("Invalid literal type `${term.type}` in token $term")
                Quad.Literal(value = term.value, type = type)
            }

            is TurtleToken.PrefixedTerm -> {
                if (term.prefix == "_") {
                    blanks.getOrPut(term.value) { Quad.BlankTerm(id = blanks.size) }
                } else {
                    val uri = prefixes[term.prefix]
                        ?: throw IllegalStateException("Unknown prefix `${term.prefix}` in token $term")
                    Quad.NamedTerm(value = "$uri${term.value}")
                }
            }

            is TurtleToken.RelativeTerm -> Quad.NamedTerm(value = "$base${term.value}")
            is TurtleToken.Term -> Quad.NamedTerm(value = term.value)
        }
    }

    private fun nextOrNull(): TurtleToken? {
        if (!source.hasNext()) {
            return null
        }
        return source.next()
    }

    private fun nextOrBail(): TurtleToken {
        if (!source.hasNext()) {
            throw NoSuchElementException("Reached end of token stream unexpectedly!")
        }
        return source.next()
    }

    private fun unexpectedToken(token: TurtleToken?): Nothing {
        if (token == null) {
            throw IllegalStateException("Unexpected end of input")
        }
        throw IllegalStateException("Unexpected token $token\nState: pos=${position}, s=${s}, p=${p}, o=${o}")
    }

}
