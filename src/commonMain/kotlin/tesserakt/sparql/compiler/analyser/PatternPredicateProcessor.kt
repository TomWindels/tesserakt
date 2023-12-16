package tesserakt.sparql.compiler.analyser

import tesserakt.rdf.ontology.RDF
import tesserakt.rdf.types.Triple
import tesserakt.sparql.compiler.types.Pattern
import tesserakt.sparql.compiler.types.Token

class PatternPredicateProcessor: Analyser<Pattern.Predicate?>() {

    override fun _process(): Pattern.Predicate? {
        return processPatternPredicate()
    }

    private fun processPatternPredicate(): Pattern.Predicate? {
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
    private fun processPatternPredicateNext(): Pattern.Predicate? {
        return if (token == Token.Syntax.ExclamationMark) {
            consume()
            Pattern.Not(
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
                Pattern.ZeroOrMore(current)
            }
            Token.Syntax.OpPlus -> {
                consume()
                Pattern.OneOrMore(current)
            }
            else -> {
                current
            }
        }
    }

    private fun processPatternPredicateChain(prior: Pattern.Predicate): Pattern.Predicate {
        // should currently be pointing to /, so consuming it
        consume()
        val next = processPatternPredicateNext() ?: bail("Unexpected end of `.../...` statement")
        return Pattern.Chain(prior, next)
    }

    private fun processPatternPredicateOr(prior: Pattern.Predicate): Pattern.Predicate {
        // should currently be pointing to |, so consuming it
        consume()
        val next = processPatternPredicateNext() ?: bail("Unexpected end of `...|...` statement")
        return Pattern.Constrained(prior, next)
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
