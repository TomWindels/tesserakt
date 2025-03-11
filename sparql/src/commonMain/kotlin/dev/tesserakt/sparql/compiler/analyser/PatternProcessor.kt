package dev.tesserakt.sparql.compiler.analyser

import dev.tesserakt.rdf.ontology.RDF
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.Quad.Companion.asLiteralTerm
import dev.tesserakt.sparql.compiler.lexer.Token
import dev.tesserakt.sparql.ast.TriplePatternSet
import dev.tesserakt.sparql.ast.TriplePattern
import dev.tesserakt.sparql.runtime.query.createAnonymousBinding

class PatternProcessor: Analyser<TriplePatternSet>() {

    private lateinit var subject: TriplePattern.Subject
    private lateinit var predicate: TriplePattern.Predicate

    private val result = mutableListOf<TriplePattern>()

    override fun _process(): TriplePatternSet {
        result.clear()
        processStartingFromPatternSubject()
        return TriplePatternSet(result)
    }

    private fun processStartingFromPatternSubject() {
        // either has a term as token, or we have to bail, consuming the token if possible
        subject = (token.asPatternElement() ?: return)
            .let { it as? TriplePattern.Subject ?: bail("$it is not a valid triple pattern subject!") }
        // consuming the next token and going to the predicate section no matter what
        consume()
        processStartingFromPatternPredicate()
    }

    private fun processStartingFromPatternPredicate() {
        predicate = use(PatternPredicateProcessor()) ?: bail("Unexpected end of pattern statement")
        processStartingFromPatternObject()
    }

    /**
     * Same version as `processStartingFromPatternPredicate`, but stops execution if no predicate is found instead of
     *  bailing
     */
    private fun tryProcessStartingFromPatternPredicate() {
        predicate = use(PatternPredicateProcessor()) ?: return
        processStartingFromPatternObject()
    }

    private tailrec fun processStartingFromPatternObject() {
        val o = processPatternObject()
        result.add(TriplePattern(subject, predicate, o))
        when (token) {
            Token.Symbol.Comma -> {
                consume()
                processStartingFromPatternObject()
            }
            Token.Symbol.SemiColon -> {
                consume()
                tryProcessStartingFromPatternPredicate()
            }
            Token.Symbol.Period -> {
                consume()
                processStartingFromPatternSubject()
            }
            is Token.Term,
            is Token.PrefixedTerm,
            is Token.NumericLiteral,
            is Token.StringLiteral,
            is Token.Binding -> bail("Invalid pattern structure")
            else -> {
                // actual end reached probably, e.g. FILTER/BIND/... expression, UNION, ..., so returning
                return
            }
        }
    }

    private fun processPatternObject(): TriplePattern.Object {
        return when (token) {
            Token.Symbol.BlankStart -> {
                val subj = createAnonymousBinding()
                val properties = processBlankObject()
                properties.forEach { result.add(TriplePattern(subj, it.first, it.second)) }
                subj
            }
            else ->
                token.asPatternElement()
                    .let { element -> element as? TriplePattern.Object }
                    ?.also { consume() }
                    ?: expectedPatternElementOrBindingOrToken(Token.Symbol.BlankStart)
        }
    }

    private fun processBlankObject(): List<Pair<TriplePattern.Predicate, TriplePattern.Object>> {
        // consuming the `[`
        consume()
        if (token == Token.Symbol.BlankEnd) {
            consume()
            return emptyList()
        }
        // looping through the inputs until all have been processed
        val statements = mutableListOf<Pair<TriplePattern.Predicate, TriplePattern.Object>>()
        // consuming until matching `]` has been reached
        var p: TriplePattern.Predicate? = use(PatternPredicateProcessor()) ?: bail("Unexpected token $token")
        var o = processPatternObject()
        while (true) {
            statements.add(p!! to o)
            when (token) {
                Token.Symbol.SemiColon -> {
                    consume()
                    // both have to be re-read, or the content has finished
                    p = use(PatternPredicateProcessor())
                    if (p == null) {
                        expectToken(Token.Symbol.BlankEnd)
                        // removing the `]`
                        consume()
                        break
                    }
                    o = processPatternObject()
                }
                Token.Symbol.Comma -> {
                    consume()
                    // only the object changes
                    o = processPatternObject()
                }
                // end reached
                Token.Symbol.BlankEnd -> {
                    // removing the `]`
                    consume()
                    break
                }
                else -> expectedToken(Token.Symbol.SemiColon, Token.Symbol.Period, Token.Symbol.BlankEnd)
            }
        }
        return statements
    }

    /* helper extensions */

    private fun Token.asPatternElement(): TriplePattern.Element? = when (this) {
        is Token.Binding -> TriplePattern.NamedBinding(this.name)
        is Token.Term -> TriplePattern.Exact(Quad.NamedTerm(value = value))
        is Token.PrefixedTerm -> TriplePattern.Exact(Quad.NamedTerm(value = resolve()))
        is Token.StringLiteral -> TriplePattern.Exact(value.asLiteralTerm())
        is Token.NumericLiteral -> TriplePattern.Exact(value.asLiteralTerm())
        Token.Keyword.RdfTypePredicate -> TriplePattern.Exact(RDF.type)
        else -> null
    }

    private fun Token.PrefixedTerm.resolve(): String {
        val uri = prefixes[namespace] ?: bail("Unknown prefix: `$namespace`")
        return uri + value
    }

}
