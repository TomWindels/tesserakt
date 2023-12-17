package tesserakt.sparql.compiler.analyser

import tesserakt.rdf.ontology.RDF
import tesserakt.rdf.types.Triple
import tesserakt.sparql.compiler.types.PatternAST
import tesserakt.sparql.compiler.types.Token

class PatternPredicateProcessor: Analyser<PatternAST.Predicate?>() {

    override fun _process(): PatternAST.Predicate? {
        return processPatternPredicate()
    }

    private fun processPatternPredicate(): PatternAST.Predicate? {
        var predicate = processPatternPredicateNext() ?: return null
        while (true) {
            when (token) {
                Token.Syntax.PredicateOr -> {
                    predicate = processPatternPredicateOr(predicate)
                }
                Token.Syntax.ForwardSlash -> {
                    predicate = processPatternPredicateChain(predicate)
                }
                is Token.Term,
                is Token.Binding,
                is Token.NumericLiteral,
                is Token.StringLiteral,
                Token.Syntax.BlankStart -> {
                    // object, so not setting anything and returning instead
                    return predicate
                }
                else -> expectedPatternElementOrBindingOrToken(
                    Token.Syntax.PredicateOr,
                    Token.Syntax.ForwardSlash,
                    Token.Syntax.BlankStart
                )
            }
        }
    }

    /** Processes [!][(]<predicate>[)][*] **/
    private fun processPatternPredicateNext(): PatternAST.Predicate? {
        return if (token == Token.Syntax.ExclamationMark) {
            consume()
            PatternAST.Not(
                predicate = processPatternPredicateContent() ?: bail("Unexpected end of `!...` statement")
            )
        } else {
            processPatternPredicateContent()
        }
    }

    /** Processes [(]<predicate>[/|<predicate>][)][*|+] **/
    private fun processPatternPredicateContent() = when (token) {
        is Token.Term, is Token.Binding, Token.Syntax.RdfTypePredicate -> token.asPatternElement()
        Token.Syntax.RoundBracketStart -> {
            consume()
            var result = processPatternPredicateNext() ?: bail("Unexpected end of `(...)` statement")
            while (true) {
                result = when (token) {
                    Token.Syntax.RoundBracketEnd -> break
                    Token.Syntax.PredicateOr -> processPatternPredicateOr(result)
                    Token.Syntax.ForwardSlash -> processPatternPredicateChain(result)
                    else -> expectedToken(
                        Token.Syntax.RoundBracketEnd,
                        Token.Syntax.PredicateOr,
                        Token.Syntax.ForwardSlash
                    )
                }
            }
            result
        }
        else -> null
    }?.let { current ->
        // consuming the last token from the currently processed predicate
        consume()
        // consuming the star if possible
        when (token) {
            Token.Syntax.Asterisk -> {
                consume()
                PatternAST.ZeroOrMore(current)
            }
            Token.Syntax.OpPlus -> {
                consume()
                PatternAST.OneOrMore(current)
            }
            else -> {
                current
            }
        }
    }

    private fun processPatternPredicateChain(prior: PatternAST.Predicate): PatternAST.Predicate {
        // should currently be pointing to /, so consuming it
        consume()
        val next = processPatternPredicateNext() ?: bail("Unexpected end of `.../...` statement")
        return PatternAST.Chain(prior, next)
    }

    private fun processPatternPredicateOr(prior: PatternAST.Predicate): PatternAST.Predicate {
        // should currently be pointing to |, so consuming it
        consume()
        val next = processPatternPredicateNext() ?: bail("Unexpected end of `...|...` statement")
        return PatternAST.Alts(prior, next)
    }

    /* helper extensions */

    private fun Token.asPatternElement(): PatternAST.Element = when (this) {
        is Token.Binding -> PatternAST.Binding(this)
        is Token.Term -> asExactPatternElement()
        Token.Syntax.RdfTypePredicate -> PatternAST.Exact(RDF.type)
        else -> expectedPatternElementOrBindingOrToken(Token.Syntax.RdfTypePredicate)
    }

    private fun Token.Term.asExactPatternElement() =
        PatternAST.Exact(
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
