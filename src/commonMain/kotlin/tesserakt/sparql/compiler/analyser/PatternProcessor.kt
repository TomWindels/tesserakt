package tesserakt.sparql.compiler.analyser

import tesserakt.rdf.ontology.RDF
import tesserakt.rdf.types.Triple
import tesserakt.sparql.compiler.types.Pattern
import tesserakt.sparql.compiler.types.Patterns
import tesserakt.sparql.compiler.types.Token

class PatternProcessor: Analyser<Patterns>() {

    private lateinit var subject: Pattern.Subject
    private lateinit var predicate: Pattern.Predicate

    private val result = mutableListOf<Pattern>()

    override fun _process(): Patterns {
        result.clear()
        processStartingFromPatternSubject()
        return result
    }

    private fun processStartingFromPatternSubject() {
        // either has a term as token, or we have to bail
        if (token is Token.Syntax) {
            return
        }
        // consuming the token as well
        subject = token.asPatternElement()
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
            Token.Syntax.Comma -> {
                consume()
                processStartingFromPatternObject()
            }
            Token.Syntax.SemiColon -> {
                consume()
                tryProcessStartingFromPatternPredicate()
            }
            Token.Syntax.Period -> {
                consume()
                processStartingFromPatternSubject()
            }
            !is Token.Syntax -> bail("Invalid pattern structure")
            else -> {
                // actual end reached probably, e.g. FILTER/BIND/... expression, UNION, ..., so returning
                return
            }
        }
    }

    private fun processPatternObject(): Pattern.Object {
        return when (token) {
            Token.Syntax.BlankStart ->
                processBlankObject()
            is Token.Term, is Token.Binding, is Token.StringLiteral, is Token.NumericLiteral ->
                token.asPatternElement().also { consume() }
            else -> expectedPatternElementOrBindingOrToken(Token.Syntax.BlankStart)
        }
    }


    private fun processBlankObject(): Pattern.BlankObject {
        // consuming the `[`
        consume()
        if (token == Token.Syntax.BlankEnd) {
            consume()
            return Pattern.BlankObject(emptyList())
        }
        // looping through the inputs until all have been processed
        val statements = mutableListOf<Pattern.BlankObject.BlankPattern>()
        // consuming until matching `]` has been reached
        var p: Pattern.Predicate? = use(PatternPredicateProcessor()) ?: bail("Unexpected token $token")
        var o = processPatternObject()
        while (true) {
            statements.add(Pattern.BlankObject.BlankPattern(p = p!!, o = o))
            when (token) {
                Token.Syntax.SemiColon -> {
                    consume()
                    // both have to be re-read, or the content has finished
                    p = use(PatternPredicateProcessor())
                    if (p == null) {
                        expectToken(Token.Syntax.BlankEnd)
                        // removing the `]`
                        consume()
                        break
                    }
                    o = processPatternObject()
                }
                Token.Syntax.Comma -> {
                    consume()
                    // only the object changes
                    o = processPatternObject()
                }
                // end reached
                Token.Syntax.BlankEnd -> {
                    // removing the `]`
                    consume()
                    break
                }
                else -> expectedToken(Token.Syntax.SemiColon, Token.Syntax.Period, Token.Syntax.BlankEnd)
            }
        }
        return Pattern.BlankObject(statements)
    }

    /* helper extensions */

    private fun Token.asPatternElement(): Pattern.Element = when (this) {
        is Token.Binding -> Pattern.Binding(this)
        is Token.Term -> asExactPatternElement()
        Token.Syntax.RdfTypePredicate -> Pattern.Exact(RDF.type)
        else -> expectedPatternElementOrBindingOrToken(Token.Syntax.RdfTypePredicate)
    }

    private fun Token.Term.asExactPatternElement() =
        Pattern.Exact(
            if (value.contains(':')) {
                val prefix = value.substringBefore(':')
                val uri = prefixes[prefix] ?: bail("Unknown prefix: `$prefix`")
                val name = uri + value.substringAfter(':')
                Triple.NamedTerm(value = name)
            } else {
                // removing the `<`, `>`
                Triple.NamedTerm(value = value.substring(1, value.length - 1))
            }
        )

}
