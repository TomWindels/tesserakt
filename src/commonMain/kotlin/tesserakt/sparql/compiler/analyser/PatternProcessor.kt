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
        processPatternSubject()
        return result
    }

    private fun processPatternSubject() {
        // either has a term as token, or we have to bail
        if (token is Token.Syntax) {
            return
        }
        subject = token.asPatternElement()
        // consuming the next token and going to the predicate section no matter what
        consume()
        processPatternPredicate()
    }

    private fun processPatternPredicateOrBail() {
        // continuing if the end of the query block hasn't been reached yet
        if (token != Token.Syntax.CurlyBracketEnd) {
            processPatternPredicate()
        }
    }

    private fun processPatternPredicate() {
        predicate = processPatternPredicateNext()
        while (true) {
            when (token) {
                Token.Syntax.PredicateOr -> {
                    predicate = processPatternPredicateOr(predicate)
                }
                Token.Syntax.ForwardSlash -> {
                    predicate = processPatternPredicateChain(predicate)
                }
                !is Token.Syntax -> {
                    // object, so not setting anything and returning instead
                    processPatternObject()
                    return
                }
                else -> expectedPatternElementOrBindingOrToken(
                    Token.Syntax.PredicateOr,
                    Token.Syntax.ForwardSlash
                )
            }
        }
    }

    /** Processes [!][(]<predicate>[)][*] **/
    private fun processPatternPredicateNext(): Pattern.Predicate {
        return if (token == Token.Syntax.ExclamationMark) {
            consume()
            Pattern.Not(processPatternPredicateContent())
        } else {
            processPatternPredicateContent()
        }
    }

    /** Processes [(]<predicate>[/|<predicate>][)][*] **/
    private fun processPatternPredicateContent() = when (token) {
        !is Token.Syntax, Token.Syntax.TypePredicate -> token.asPatternElement()
        Token.Syntax.RoundBracketStart -> {
            // token should be `(`, so consuming and continuing
            consume()
            var result = processPatternPredicateNext()
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
        else -> expectedPatternElementOrToken(Token.Syntax.RoundBracketStart)
    }.let { current ->
        // consuming the last token from the currently processed predicate
        consume()
        // consuming the star if possible
        if (token == Token.Syntax.Asterisk) {
            consume()
            Pattern.ZeroOrMore(current)
        } else {
            current
        }
    }

    private fun processPatternPredicateChain(prior: Pattern.Predicate): Pattern.Predicate {
        // should currently be pointing to /, so consuming it
        consume()
        val next = processPatternPredicateNext()
        return Pattern.Chain(prior, next)
    }

    private fun processPatternPredicateOr(prior: Pattern.Predicate): Pattern.Predicate {
        // should currently be pointing to |, so consuming it
        consume()
        val next = processPatternPredicateNext()
        return Pattern.Constrained(prior, next)
    }

    private fun processPatternObject() {
        expectPatternElementOrBinding()
        result.add(
            Pattern(
                s = subject,
                p = predicate,
                o = token.asPatternElement()
            )
        )
        consume()
        processPatternEnd()
    }

    private fun processPatternEnd() {
        when (token) {
            Token.Syntax.ObjectEnd -> {
                consume()
                processPatternObject()
            }
            Token.Syntax.PredicateEnd -> {
                consume()
                processPatternPredicateOrBail()
            }
            Token.Syntax.PatternEnd -> {
                consume()
                processPatternSubject()
            }
            else -> {
                // don't know what to do, so considering this to be finished
                return
            }
        }
    }

    /* helper extensions */

    private fun Token.asPatternElement(): Pattern.Element = when (this) {
        is Token.Binding -> Pattern.Binding(this)
        is Token.Term -> asExactPatternElement()
        Token.Syntax.TypePredicate -> Pattern.Exact(RDF.type)
        else -> expectedPatternElementOrBindingOrToken(Token.Syntax.TypePredicate)
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
