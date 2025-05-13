package dev.tesserakt.rdf.turtle.serialization

import dev.tesserakt.rdf.ontology.RDF
import dev.tesserakt.rdf.ontology.XSD
import dev.tesserakt.rdf.types.Quad

// TODO: blank node syntax `[]` support
// TODO: improved exception handling

internal class Deserializer(source: Iterator<TurtleToken>) : Iterator<Quad> {

    private inner class BlankNodeProcessor : Iterator<Quad> {

        val name = Quad.BlankTerm(id = unnamedBlankNodeCount++)
        private var p: Quad.NamedTerm? = null
        private var child: BlankNodeProcessor? = null
        private var next: Quad? = null

        init {
            check(source.peek() == TurtleToken.Structural.BlankStart) {
                "Expected ${TurtleToken.Structural.BlankStart}, got ${source.peek()}"
            }
            source.consume()
        }

        override fun hasNext(): Boolean {
            if (next != null) {
                return true
            }
            next = prepareNext()
            return next != null
        }

        override fun next(): Quad {
            val next = next ?: prepareNext()
            this.next = null
            return next ?: throw NoSuchElementException()
        }

        private fun prepareNext(): Quad? {
            while (true) {
                val token = source.peek()
                val child = this.child
                when {
                    child != null -> {
                        if (child.hasNext()) {
                            return child.next()
                        }
                        // the inner blank node has finished, setting it to `null` and emitting its name as the next quad
                        check(source.peek() == TurtleToken.Structural.BlankEnd)
                        source.consume()
                        val o = child.name
                        this.child = null
                        val result = Quad(s = name, p = p!!, o = o)
                        if (source.peek() == TurtleToken.Structural.ObjectTermination) {
                            source.consume()
                        } else if (source.peek() == TurtleToken.Structural.PredicateTermination) {
                            p = null
                            source.consume()
                        }
                        return result
                    }

                    token == TurtleToken.EOF -> unexpectedToken(TurtleToken.EOF)

                    token == TurtleToken.Structural.BlankEnd -> {
                        return null
                    }

                    p == null -> {
                        p = resolve(token.into()).into()
                        source.consume()
                    }

                    token == TurtleToken.Structural.BlankStart -> {
                        this.child = BlankNodeProcessor()
                    }

                    token is TurtleToken.TermToken -> {
                        val o = resolve(token)
                        val result = Quad(s = name, p = p!!, o = o)
                        // consuming token `o`
                        source.consume()
                        when (source.peek()) {
                            TurtleToken.Structural.BlankEnd -> {
                                // not consuming this token, next iteration will yield null
                            }

                            TurtleToken.Structural.ObjectTermination -> {
                                source.consume()
                            }

                            TurtleToken.Structural.PredicateTermination -> {
                                source.consume()
                                p = null
                            }

                            else -> unexpectedToken(source.peek())
                        }
                        return result
                    }

                    else -> unexpectedToken(token)
                }
            }
        }

    }

    /* state/input logic */

    private val source = TokenBuffer(source)

    private val prefixes = mutableMapOf<String /* prefix */, String /* uri */>()
    private val namedBlankNodes = mutableMapOf<String /* serialized label */, Quad.BlankTerm>()
    private var unnamedBlankNodeCount = 0
    private var base = ""
    private var child: BlankNodeProcessor? = null
    private var s: Quad.Term? = null
    private var p: Quad.NamedTerm? = null

    init {
        parsePrefixes()
    }

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
        while (true) {
            val child = child
            val token = source.peek()
            when {
                child != null -> {
                    if (child.hasNext()) {
                        return child.next()
                    }
                    // clearing out the child as it's finished
                    this.child = null
                    check(source.peek() == TurtleToken.Structural.BlankEnd)
                    source.consume()
                    // using the child from this iteration one last time - to reference its name in the appropriate
                    //  quad
                    when {
                        s == null -> {
                            val next = source.peek()
                            if (next == TurtleToken.Structural.StatementTermination || next == TurtleToken.EOF) {
                                // state was already cleared
                                source.consume()
                            } else {
                                // subsequent terms use this blank node as a subject
                                s = child.name
                            }
                        }

                        else -> {
                            return onObjectElement(o = child.name)
                        }
                    }
                }

                token == TurtleToken.EOF -> return null

                token == TurtleToken.Structural.BlankStart -> {
                    this.child = BlankNodeProcessor()
                }

                s == null -> {
                    s = resolve(source.peek().into())
                    source.consume()
                }

                p == null -> {
                    p = if (source.peek() == TurtleToken.Keyword.TypePredicate) {
                        RDF.type
                    } else {
                        resolve(source.peek().into()).into()
                    }
                    source.consume()
                }

                else -> {
                    val o = when (token) {
                        is TurtleToken.TermToken -> {
                            resolve(token)
                        }

                        TurtleToken.Keyword.TrueLiteral -> {
                            Quad.Literal(value = "true", type = XSD.boolean)
                        }

                        TurtleToken.Keyword.FalseLiteral -> {
                            Quad.Literal(value = "false", type = XSD.boolean)
                        }

                        else -> unexpectedToken(token)
                    }
                    return onObjectElement(o)
                }
            }
        }
    }

    private fun onObjectElement(o: Quad.Term): Quad {
        val result = Quad(s = s!!, p = p!!, o = o)
        // the object itself
        source.consume()
        when (val terminator = source.consume()) {
            TurtleToken.Structural.StatementTermination -> {
                s = null
                p = null
            }

            TurtleToken.Structural.PredicateTermination -> {
                p = null
            }

            TurtleToken.Structural.ObjectTermination, TurtleToken.EOF -> {
                /* nothing to do */
            }

            else -> unexpectedToken(terminator)
        }
        return result
    }

    /**
     * Consumes tokens, processing prefixes in the process, until reaching the start of the next statement, returning
     *  said start, or `null` if no statement follows. If already inside a statement according to the [position] state,
     *  this method will immediately return.
     */
    private fun parsePrefixes() {
        var token = source.peekOrNull()
        do {
            when (token) {
                null -> return

                TurtleToken.Keyword.BaseAnnotationA,
                TurtleToken.Keyword.BaseAnnotationB -> {
                    val uri = nextOrBail()
                    check(uri is TurtleToken.Term) { "Invalid base value `${uri}`" }
                    base = uri.value
                    token = nextOrNull()
                    if (token == TurtleToken.Structural.StatementTermination) {
                        token = nextOrNull()
                    }
                }

                TurtleToken.Keyword.PrefixAnnotationA,
                TurtleToken.Keyword.PrefixAnnotationB -> {
                    processPrefix()
                    token = nextOrNull()
                    if (token == TurtleToken.Structural.StatementTermination) {
                        token = nextOrNull()
                    }
                }

                else -> {
                    // not our concern, exiting
                    break
                }
            }
        } while (source.hasNext())
    }

    private fun processPrefix() {
        val prefix = nextOrBail()
        check(prefix is TurtleToken.PrefixedTerm && prefix.value.isEmpty())
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
                    namedBlankNodes.getOrPut(term.value) { Quad.BlankTerm(id = namedBlankNodes.size) }
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
        source.consume()
        return source.peekOrNull()
    }

    private fun nextOrBail(): TurtleToken {
        source.consume()
        return source.peek()
            .also { if (it == TurtleToken.EOF) throw NoSuchElementException("Reached end of token stream unexpectedly!") }
    }

    private fun unexpectedToken(token: TurtleToken?): Nothing {
        if (token == null) {
            throw IllegalStateException("Unexpected end of input")
        }
        throw IllegalStateException("Unexpected token $token\nState: s=${s}, p=${p}")
    }

    private inline fun <reified T> Any.into(): T {
        if (this !is T) {
            throw IllegalStateException("Unexpected token $this, expected ${T::class.simpleName}\nState: s=${s}, p=${p}")
        }
        return this
    }

}
