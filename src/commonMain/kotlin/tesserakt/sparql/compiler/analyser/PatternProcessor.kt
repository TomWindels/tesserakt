package tesserakt.sparql.compiler.analyser

import tesserakt.rdf.ontology.RDF
import tesserakt.rdf.types.Triple
import tesserakt.rdf.types.Triple.Companion.asLiteral
import tesserakt.sparql.compiler.types.PatternAST
import tesserakt.sparql.compiler.types.PatternsAST
import tesserakt.sparql.compiler.types.Token

class PatternProcessor: Analyser<PatternsAST>() {

    private lateinit var subject: PatternAST.Subject
    private lateinit var predicate: PatternAST.Predicate

    private val result = mutableListOf<PatternAST>()

    override fun _process(): PatternsAST {
        result.clear()
        processStartingFromPatternSubject()
        return PatternsAST(result)
    }

    private fun processStartingFromPatternSubject() {
        // either has a term as token, or we have to bail, consuming the token if possible
        subject = token.asPatternElement() ?: return
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
        result.add(PatternAST(subject, predicate, o))
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

    private fun processPatternObject(): PatternAST.Object {
        return when (token) {
            Token.Symbol.BlankStart ->
                processBlankObject()
            else ->
                token.asPatternElement()?.also { consume() }
                    ?: expectedPatternElementOrBindingOrToken(Token.Symbol.BlankStart)
        }
    }


    private fun processBlankObject(): PatternAST.BlankObject {
        // consuming the `[`
        consume()
        if (token == Token.Symbol.BlankEnd) {
            consume()
            return PatternAST.BlankObject(emptyList())
        }
        // looping through the inputs until all have been processed
        val statements = mutableListOf<PatternAST.BlankObject.BlankPattern>()
        // consuming until matching `]` has been reached
        var p: PatternAST.Predicate? = use(PatternPredicateProcessor()) ?: bail("Unexpected token $token")
        var o = processPatternObject()
        while (true) {
            statements.add(PatternAST.BlankObject.BlankPattern(p = p!!, o = o))
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
        return PatternAST.BlankObject(statements)
    }

    /* helper extensions */

    private fun Token.asPatternElement(): PatternAST.Element? = when (this) {
        is Token.Binding -> PatternAST.Binding(this)
        is Token.Term -> PatternAST.Exact(Triple.NamedTerm(value = value))
        is Token.PrefixedTerm -> PatternAST.Exact(Triple.NamedTerm(value = resolve()))
        is Token.StringLiteral -> PatternAST.Exact(value.asLiteral())
        is Token.NumericLiteral -> PatternAST.Exact(value.asLiteral())
        Token.Keyword.RdfTypePredicate -> PatternAST.Exact(RDF.type)
        else -> null
    }

    private fun Token.PrefixedTerm.resolve(): String {
        val uri = prefixes[namespace] ?: bail("Unknown prefix: `$namespace`")
        return uri + value
    }

}
