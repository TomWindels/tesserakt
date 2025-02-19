package dev.tesserakt.rdf.trig.serialization

import dev.tesserakt.rdf.ontology.RDF
import dev.tesserakt.rdf.types.Quad

// TODO: blank node syntax `[]` support
// TODO: improved exception handling
// TODO: exception on incomplete graph block

internal class Deserializer(private val source: Iterator<TriGToken>) : Iterator<Quad> {

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
    private var g: Quad.Graph = Quad.DefaultGraph
    private var inGraphBlock = false

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

                inGraphBlock && token == TriGToken.Structural.GraphStatementEnd -> {
                    inGraphBlock = false
                    g = Quad.DefaultGraph
                    token = nextOrNull()
                }

                position == Position.Subject -> {
                    check(token is TriGToken.TermToken) {
                        "$token is not a valid subject / graph term"
                    }
                    val resolved = resolve(token)
                    if (inGraphBlock) {
                        // we're already in a graph block, the only valid next term is also a term token, which we won't
                        //  explicitly test for now
                        s = resolved
                        position = Position.Predicate
                        token = nextOrBail()
                    } else {
                        // it's possible that the next token will now result in a `{`, meaning we're dealing with
                        //  a graph term
                        when (val next = nextOrBail()) {
                            TriGToken.Structural.GraphStatementStart -> {
                                g = resolved as? Quad.Graph
                                    ?: throw IllegalStateException("$resolved is not a valid graph term!")
                                position = Position.Subject
                                inGraphBlock = true
                            }

                            is TriGToken.TermToken -> {
                                s = resolved
                                val predicate = resolve(next)
                                p = predicate as? Quad.NamedTerm
                                    ?: throw IllegalStateException("$predicate is not a valid predicate term!")
                                position = Position.Object
                            }

                            else -> unexpectedToken(token)
                        }
                        token = nextOrBail()
                    }
                }

                position == Position.Predicate && token is TriGToken.TermToken -> {
                    val predicate = resolve(token)
                    p = predicate as? Quad.NamedTerm
                        ?: throw IllegalStateException("$predicate is not a valid predicate term!")
                    position = Position.Object
                    token = nextOrBail()
                }

                position == Position.Predicate && token == TriGToken.Structural.TypePredicate -> {
                    p = RDF.type
                    position = Position.Object
                    token = nextOrBail()
                }

                position == Position.Predicate -> {
                    throw IllegalStateException("Invalid predicate token: $token")
                }

                position == Position.Object -> {
                    // FIXME: blank objects
                    check(token is TriGToken.TermToken)
                    o = resolve(token)
                    var next: TriGToken? = nextOrBail()
                    // it's possible for the next token to be a graph term, in which case we'll have to update that as well
                    val result = if (!inGraphBlock && next is TriGToken.TermToken) {
                        val graph = resolve(next)
                        next = nextOrNull()
                        Quad(
                            s = s!!,
                            p = p!!,
                            o = o!!,
                            g = graph as? Quad.Graph ?: throw IllegalStateException("$graph is not a valid graph term!")
                        )
                    } else {
                        Quad(
                            s = s!!,
                            p = p!!,
                            o = o!!,
                            g = g
                        )
                    }
                    // depending on what this next token is, we either adjust the position and yield, update the graph
                    //  value and yield
                    while (true) {
                        when (next) {
                            null -> {
                                break
                            }

                            TriGToken.Structural.StatementTermination -> {
                                position = Position.Subject
                                break
                            }

                            TriGToken.Structural.PredicateTermination -> {
                                position = Position.Predicate
                                break
                            }

                            TriGToken.Structural.ObjectTermination -> {
                                position = Position.Object
                                break
                            }

                            TriGToken.Structural.GraphStatementEnd -> {
                                check(inGraphBlock)
                                inGraphBlock = false
                                g = Quad.DefaultGraph
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
    private fun consumeUntilInsideStatement(): TriGToken? {
        if (!source.hasNext()) {
            return null
        }
        if (inGraphBlock || position != Position.Subject) {
            // we are inside a statement or graph block, meaning we shouldn't encounter any prefixes now and can
            //  bail early
            return nextOrBail()
        }
        var token: TriGToken
        do {
            token = source.next()
            when (token) {
                TriGToken.Structural.PrefixAnnotationA,
                TriGToken.Structural.PrefixAnnotationB -> {
                    processPrefix()
                }

                is TriGToken.TermToken -> {
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
        check(prefix is TriGToken.PrefixedTerm && prefix.value.isEmpty())
        check(prefix.prefix !in prefixes) {
            "The prefix ${prefix.prefix} is already registered as ${prefixes[prefix.prefix]}"
        }
        val uri = nextOrBail()
        check(uri is TriGToken.Term)
        val termination = nextOrBail()
        check(termination == TriGToken.Structural.StatementTermination)
        prefixes[prefix.prefix] = uri.value
    }

    private fun resolve(term: TriGToken.TermToken): Quad.Term {
        return when (term) {
            is TriGToken.LiteralTerm -> {
                val type = resolve(term.type) as? Quad.NamedTerm
                    ?: throw IllegalStateException("Invalid literal type `${term.type}` in token $term")
                Quad.Literal(value = term.value, type = type)
            }

            is TriGToken.PrefixedTerm -> {
                if (term.prefix == "_") {
                    blanks.getOrPut(term.value) { Quad.BlankTerm(id = blanks.size) }
                } else {
                    val uri = prefixes[term.prefix]
                        ?: throw IllegalStateException("Unknown prefix `${term.prefix}` in token $term")
                    Quad.NamedTerm(value = "$uri${term.value}")
                }
            }

            is TriGToken.RelativeTerm -> Quad.NamedTerm(value = "$base${term.value}")
            is TriGToken.Term -> Quad.NamedTerm(value = term.value)
        }
    }

    private fun nextOrNull(): TriGToken? {
        if (!source.hasNext()) {
            return null
        }
        return source.next()
    }

    private fun nextOrBail(): TriGToken {
        if (!source.hasNext()) {
            throw NoSuchElementException("Reached end of token stream unexpectedly!")
        }
        return source.next()
    }

    private fun unexpectedToken(token: TriGToken?): Nothing {
        if (token == null) {
            throw IllegalStateException("Unexpected end of input")
        }
        throw IllegalStateException("Unexpected token $token")
    }

}
