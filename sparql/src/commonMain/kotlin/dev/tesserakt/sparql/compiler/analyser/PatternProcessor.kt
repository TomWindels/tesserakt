package dev.tesserakt.sparql.compiler.analyser

import dev.tesserakt.rdf.ontology.RDF
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.Quad.Companion.asLiteralTerm
import dev.tesserakt.sparql.compiler.lexer.Token
import dev.tesserakt.sparql.types.runtime.element.Pattern
import dev.tesserakt.sparql.types.runtime.element.Patterns
import dev.tesserakt.sparql.types.runtime.query.createAnonymousBinding

class PatternProcessor: Analyser<Patterns>() {

    private lateinit var subject: Pattern.Subject
    private lateinit var predicate: Pattern.Predicate

    private val result = mutableListOf<Pattern>()

    override fun _process(): Patterns {
        result.clear()
        processStartingFromPatternSubject()
        return Patterns(result)
    }

    private fun processStartingFromPatternSubject() {
        // either has a term as token, or we have to bail, consuming the token if possible
        subject = (token.asPatternElement() ?: return)
            .let { it as? Pattern.Subject ?: bail("$it is not a valid triple pattern subject!") }
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
        result.add(Pattern(subject, predicate, o))
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

    private fun processPatternObject(): Pattern.Object {
        return when (token) {
            Token.Symbol.BlankStart -> {
                val subj = createAnonymousBinding()
                val properties = processBlankObject()
                properties.forEach { result.add(Pattern(subj, it.first, it.second)) }
                subj
            }
            else ->
                token.asPatternElement()
                    .let { element -> element as? Pattern.Object }
                    ?.also { consume() }
                    ?: expectedPatternElementOrBindingOrToken(Token.Symbol.BlankStart)
        }
    }

    private fun processBlankObject(): List<Pair<Pattern.Predicate, Pattern.Object>> {
        // consuming the `[`
        consume()
        if (token == Token.Symbol.BlankEnd) {
            consume()
            return emptyList()
        }
        // looping through the inputs until all have been processed
        val statements = mutableListOf<Pair<Pattern.Predicate, Pattern.Object>>()
        // consuming until matching `]` has been reached
        var p: Pattern.Predicate? = use(PatternPredicateProcessor()) ?: bail("Unexpected token $token")
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

    private fun Token.asPatternElement(): Pattern.Element? = when (this) {
        is Token.Binding -> Pattern.NamedBinding(this.name)
        is Token.Term -> Pattern.Exact(Quad.NamedTerm(value = value))
        is Token.PrefixedTerm -> Pattern.Exact(Quad.NamedTerm(value = resolve()))
        is Token.StringLiteral -> Pattern.Exact(value.asLiteralTerm())
        is Token.NumericLiteral -> Pattern.Exact(value.asLiteralTerm())
        Token.Keyword.RdfTypePredicate -> Pattern.Exact(RDF.type)
        else -> null
    }

    private fun Token.PrefixedTerm.resolve(): String {
        val uri = prefixes[namespace] ?: bail("Unknown prefix: `$namespace`")
        return uri + value
    }

}
