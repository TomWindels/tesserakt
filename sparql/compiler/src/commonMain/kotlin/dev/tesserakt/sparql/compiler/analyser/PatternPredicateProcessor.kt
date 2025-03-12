package dev.tesserakt.sparql.compiler.analyser

import dev.tesserakt.rdf.ontology.RDF
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.compiler.lexer.Token
import dev.tesserakt.sparql.types.TriplePattern

class PatternPredicateProcessor: Analyser<TriplePattern.Predicate?>() {

    override fun _process(): TriplePattern.Predicate? {
        return processPatternPredicate()
    }

    private fun processPatternPredicate(): TriplePattern.Predicate? {
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
    private fun processPatternPredicateNext(): TriplePattern.Predicate? {
        return if (token == Token.Symbol.ExclamationMark) {
            consume()
            val inner = processPatternPredicateContent() ?: bail("Unexpected end of `!...` statement")
            when (inner) {
                is TriplePattern.SimpleAlts -> {
                    TriplePattern.Negated(
                        terms = inner
                    )
                }
                is TriplePattern.StatelessPredicate -> {
                    TriplePattern.Negated(
                        terms = TriplePattern.SimpleAlts(allowed = listOf(inner))
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
                is TriplePattern.Predicate -> {
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
                if (current !is TriplePattern.UnboundPredicate) {
                    bail("$current cannot be part of a repeating pattern!")
                }
                consume()
                TriplePattern.ZeroOrMore(current)
            }
            Token.Symbol.OpPlus -> {
                if (current !is TriplePattern.UnboundPredicate) {
                    bail("$current cannot be part of a repeating pattern!")
                }
                consume()
                TriplePattern.OneOrMore(current)
            }
            else -> {
                current
            }
        }
    }

    private fun processPatternPredicateSequence(prior: TriplePattern.Predicate): TriplePattern.Predicate {
        // should currently be pointing to /, so consuming it
        consume()
        val next = processPatternPredicateNext() ?: bail("Unexpected end of `.../...` statement")
        return when {
            prior is TriplePattern.UnboundPredicate && next is TriplePattern.UnboundPredicate -> {
                TriplePattern.UnboundSequence(prior, next)
            }

            else -> {
                TriplePattern.Sequence(prior, next)
            }
        }
    }

    private fun processPatternPredicateOr(prior: TriplePattern.Predicate): TriplePattern.Predicate {
        if (prior !is TriplePattern.UnboundPredicate) {
            bail("$prior is not a valid alternative path element!")
        }
        // should currently be pointing to |, so consuming it
        consume()
        val next = processPatternPredicateNext() ?: bail("Unexpected end of `...|...` statement")
        return when {
            next !is TriplePattern.UnboundPredicate -> {
                bail("$next is not a valid alternative path element!")
            }
            prior is TriplePattern.StatelessPredicate && next is TriplePattern.StatelessPredicate -> {
                TriplePattern.SimpleAlts(prior, next)
            }
            else -> {
                TriplePattern.Alts(prior, next)
            }
        }
    }

    /* helper extensions */

    private fun Token.asPatternElement(): TriplePattern.Element = when (this) {
        is Token.Binding -> TriplePattern.NamedBinding(this.name)
        is Token.Term -> TriplePattern.Exact(Quad.NamedTerm(value = value))
        is Token.PrefixedTerm -> TriplePattern.Exact(Quad.NamedTerm(value = resolve()))
        Token.Keyword.RdfTypePredicate -> TriplePattern.Exact(RDF.type)
        else -> expectedPatternElementOrBindingOrToken(Token.Keyword.RdfTypePredicate)
    }

    private fun Token.PrefixedTerm.resolve(): String {
        val uri = prefixes[namespace] ?: bail("Unknown prefix: `$namespace`")
        return uri + value
    }

}
