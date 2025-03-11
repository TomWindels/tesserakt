package dev.tesserakt.sparql.compiler.analyser

import dev.tesserakt.rdf.ontology.RDF
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.compiler.lexer.Token
import dev.tesserakt.sparql.types.runtime.element.Pattern

class PatternPredicateProcessor: Analyser<Pattern.Predicate?>() {

    override fun _process(): Pattern.Predicate? {
        return processPatternPredicate()
    }

    private fun processPatternPredicate(): Pattern.Predicate? {
        var predicate = processPatternPredicateNext() ?: return null
        while (true) {
            when (token) {
                Token.Symbol.PredicateOr -> {
                    predicate = processPatternPredicateOr(predicate)
                }
                Token.Symbol.ForwardSlash -> {
                    predicate = processPatternPredicateSequence(predicate)
                }
                is Token.Term,
                is Token.PrefixedTerm,
                is Token.Binding,
                is Token.NumericLiteral,
                is Token.StringLiteral,
                Token.Symbol.BlankStart -> {
                    // object, so not setting anything and returning instead
                    return predicate
                }
                else -> expectedPatternElementOrBindingOrToken(
                    Token.Symbol.PredicateOr,
                    Token.Symbol.ForwardSlash,
                    Token.Symbol.BlankStart
                )
            }
        }
    }

    /** Processes [!][(]<predicate>[)][*] **/
    private fun processPatternPredicateNext(): Pattern.Predicate? {
        return if (token == Token.Symbol.ExclamationMark) {
            consume()
            val inner = processPatternPredicateContent() ?: bail("Unexpected end of `!...` statement")
            when (inner) {
                is Pattern.SimpleAlts -> {
                    Pattern.Negated(
                        terms = inner
                    )
                }
                is Pattern.StatelessPredicate -> {
                    Pattern.Negated(
                        terms = Pattern.SimpleAlts(allowed = listOf(inner))
                    )
                }
                else -> {
                    bail("Invalid negated term!")
                }
            }
        } else {
            when (val processed = processPatternPredicateContent()) {
                null -> {
                    return null
                }
                is Pattern.Predicate -> {
                    processed
                }
            }
        }
    }

    /** Processes [(]<predicate>[/|<predicate>][)][*|+] **/
    private fun processPatternPredicateContent() = when (token) {
        is Token.Term, is Token.PrefixedTerm, is Token.Binding, Token.Keyword.RdfTypePredicate -> token.asPatternElement()
        Token.Symbol.RoundBracketStart -> {
            consume()
            var result = processPatternPredicateNext() ?: bail("Unexpected end of `(...)` statement")
            while (true) {
                result = when (token) {
                    Token.Symbol.RoundBracketEnd -> break
                    Token.Symbol.PredicateOr -> processPatternPredicateOr(result)
                    Token.Symbol.ForwardSlash -> processPatternPredicateSequence(result)
                    else -> expectedToken(
                        Token.Symbol.RoundBracketEnd,
                        Token.Symbol.PredicateOr,
                        Token.Symbol.ForwardSlash
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
            Token.Symbol.Asterisk -> {
                if (current !is Pattern.UnboundPredicate) {
                    bail("$current cannot be part of a repeating pattern!")
                }
                consume()
                Pattern.ZeroOrMore(current)
            }
            Token.Symbol.OpPlus -> {
                if (current !is Pattern.UnboundPredicate) {
                    bail("$current cannot be part of a repeating pattern!")
                }
                consume()
                Pattern.OneOrMore(current)
            }
            else -> {
                current
            }
        }
    }

    private fun processPatternPredicateSequence(prior: Pattern.Predicate): Pattern.Predicate {
        // should currently be pointing to /, so consuming it
        consume()
        val next = processPatternPredicateNext() ?: bail("Unexpected end of `.../...` statement")
        return when {
            prior is Pattern.UnboundPredicate && next is Pattern.UnboundPredicate -> {
                Pattern.UnboundSequence(prior, next)
            }

            else -> {
                Pattern.Sequence(prior, next)
            }
        }
    }

    private fun processPatternPredicateOr(prior: Pattern.Predicate): Pattern.Predicate {
        if (prior !is Pattern.UnboundPredicate) {
            bail("$prior is not a valid alternative path element!")
        }
        // should currently be pointing to |, so consuming it
        consume()
        val next = processPatternPredicateNext() ?: bail("Unexpected end of `...|...` statement")
        return when {
            next !is Pattern.UnboundPredicate -> {
                bail("$next is not a valid alternative path element!")
            }
            prior is Pattern.StatelessPredicate && next is Pattern.StatelessPredicate -> {
                Pattern.SimpleAlts(prior, next)
            }
            else -> {
                Pattern.Alts(prior, next)
            }
        }
    }

    /* helper extensions */

    private fun Token.asPatternElement(): Pattern.Element = when (this) {
        is Token.Binding -> Pattern.NamedBinding(this.name)
        is Token.Term -> Pattern.Exact(Quad.NamedTerm(value = value))
        is Token.PrefixedTerm -> Pattern.Exact(Quad.NamedTerm(value = resolve()))
        Token.Keyword.RdfTypePredicate -> Pattern.Exact(RDF.type)
        else -> expectedPatternElementOrBindingOrToken(Token.Keyword.RdfTypePredicate)
    }

    private fun Token.PrefixedTerm.resolve(): String {
        val uri = prefixes[namespace] ?: bail("Unknown prefix: `$namespace`")
        return uri + value
    }

}
