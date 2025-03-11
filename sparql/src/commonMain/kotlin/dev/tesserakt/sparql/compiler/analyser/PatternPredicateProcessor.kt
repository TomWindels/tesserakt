package dev.tesserakt.sparql.compiler.analyser

import dev.tesserakt.rdf.ontology.RDF
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.types.ast.PatternAST
import dev.tesserakt.sparql.compiler.lexer.Token

class PatternPredicateProcessor: Analyser<PatternAST.Predicate?>() {

    override fun _process(): PatternAST.Predicate? {
        return processPatternPredicate()
    }

    private fun processPatternPredicate(): PatternAST.Predicate? {
        var predicate = processPatternPredicateNext() ?: return null
        while (true) {
            when (token) {
                Token.Symbol.PredicateOr -> {
                    predicate = processPatternPredicateOr(predicate)
                }
                Token.Symbol.ForwardSlash -> {
                    predicate = processPatternPredicateChain(predicate)
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
    private fun processPatternPredicateNext(): PatternAST.Predicate? {
        return if (token == Token.Symbol.ExclamationMark) {
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
        is Token.Term, is Token.PrefixedTerm, is Token.Binding, Token.Keyword.RdfTypePredicate -> token.asPatternElement()
        Token.Symbol.RoundBracketStart -> {
            consume()
            var result = processPatternPredicateNext() ?: bail("Unexpected end of `(...)` statement")
            while (true) {
                result = when (token) {
                    Token.Symbol.RoundBracketEnd -> break
                    Token.Symbol.PredicateOr -> processPatternPredicateOr(result)
                    Token.Symbol.ForwardSlash -> processPatternPredicateChain(result)
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
                consume()
                PatternAST.ZeroOrMore(current)
            }
            Token.Symbol.OpPlus -> {
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
        is Token.Term -> PatternAST.Exact(Quad.NamedTerm(value = value))
        is Token.PrefixedTerm -> PatternAST.Exact(Quad.NamedTerm(value = resolve()))
        Token.Keyword.RdfTypePredicate -> PatternAST.Exact(RDF.type)
        else -> expectedPatternElementOrBindingOrToken(Token.Keyword.RdfTypePredicate)
    }

    private fun Token.PrefixedTerm.resolve(): String {
        val uri = prefixes[namespace] ?: bail("Unknown prefix: `$namespace`")
        return uri + value
    }

}
