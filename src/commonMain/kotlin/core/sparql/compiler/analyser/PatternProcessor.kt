package core.sparql.compiler.analyser

import core.rdf.Triple
import core.sparql.compiler.Pattern
import core.sparql.compiler.Token

class PatternProcessor(
    private val prefixes: Map<String, String>
): Analyser<List<Pattern>>() {

    private lateinit var subject: Pattern.Subject
    private lateinit var predicate: Pattern.Predicate

    private val result = mutableListOf<Pattern>()

    override fun _process(): List<Pattern> {
        result.clear()
        processPatternSubject()
        return result
    }

    private fun processPatternSubject() {
        // either has a term as token, or we have to bail
        if (token !is Token.Term) {
            return
        }
        subject = (token as Token.Term).asSubject()
        // consuming the next token and going to the predicate section no matter what
        consumeOrBail()
        processPatternPredicate()
    }

    private fun processPatternPredicate() {
        when (token) {
            Token.Syntax.RoundBracketStart -> {
                processPatternPredicateGroup()
            }
            is Token.Term -> {
                // setting and continuing to see if there's a chain or object next
                predicate = (token as Token.Term).asPredicate()
                consumeOrBail()
                processPatternPredicateEnd()
            }
            else -> expectedPatternElementOrToken(Token.Syntax.RoundBracketStart)
        }
    }

    private fun processPatternPredicateEnd() {
        // if the current token is a term, its interpreted as an object,
        // otherwise, predicate chain can also be expected
        when (token) {
            is Token.Term -> processPatternObject()
            Token.Syntax.PredicateOr -> processPatternPredicate()
            Token.Syntax.PredicateChain -> processPatternPredicateChain()
            else -> expectedPatternElementOrToken(Token.Syntax.PredicateOr, Token.Syntax.PredicateChain)
        }
    }

    private fun processPatternPredicateGroup(): Pattern.Predicate {
        consumeOrBail()
        var result: Pattern.Predicate = when (token) {
            Token.Syntax.RoundBracketStart -> processPatternPredicateGroup()
            is Token.Term -> (token as Token.Term).asPredicate()
            else -> expectedPatternElementOrToken(Token.Syntax.RoundBracketStart)
        }
        while (true) {
            when (token) {
                Token.Syntax.RoundBracketEnd -> {
                    return result
                }
                Token.Syntax.PredicateOr -> {
                    consumeOrBail()
                    val extra = when (token) {
                        Token.Syntax.RoundBracketStart -> processPatternPredicateGroup()
                        is Token.Term -> (token as Token.Term).asPredicate()
                        else -> expectedPatternElementOrToken(Token.Syntax.RoundBracketStart)
                    }
                    // FIXME use extra
                }
                Token.Syntax.PredicateChain -> {
                    consumeOrBail()
                    val extra = when (token) {
                        Token.Syntax.RoundBracketStart -> processPatternPredicateGroup()
                        is Token.Term -> (token as Token.Term).asPredicate()
                        else -> expectedPatternElementOrToken(Token.Syntax.RoundBracketStart)
                    }
                    // FIXME use extra
                }
                else -> expectedToken(
                    Token.Syntax.RoundBracketEnd,
                    Token.Syntax.PredicateOr,
                    Token.Syntax.PredicateChain
                )
            }
        }
    }

    private fun processPatternObject() {
        expectPatternElementOrToken()
        result.add(
            Pattern(
                s = subject,
                p = predicate,
                o = (token as Token.Term).asObject()
            )
        )
        consumeOrBail()
        processPatternEnd()
    }

    private fun processPatternEnd() {
        when (token) {
            Token.Syntax.ObjectEnd -> {
                consumeOrBail()
                processPatternObject()
            }
            Token.Syntax.PredicateEnd -> {
                consumeOrBail()
                processPatternPredicate()
            }
            Token.Syntax.PatternEnd -> {
                consumeOrBail()
                processPatternSubject()
            }
            else -> {
                // don't know what to do, so considering this to be finished
                return
            }
        }
    }

    /* helper extensions */

    private fun Token.Term.asSubject(): Pattern.Subject =
        asBinding() ?: Pattern.Exact(asNamedTerm())

    private fun Token.Term.asPredicate(): Pattern.Predicate =
        asBinding() ?: Pattern.Exact(asNamedTerm())

    private fun Token.Term.asObject(): Pattern.Object =
        asBinding() ?: Pattern.Exact(asNamedTerm())

    private fun Token.Term.asNamedTerm(): Triple.NamedTerm {
        return if (value == "a") {
            // TODO: use the actual ontology objects
            Triple.NamedTerm(
                value = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"
            )
        } else if (value.contains(':')) {
            Triple.NamedTerm(
                value = prefixes[value.substringBefore(':')]!! + value.substringAfter(':')
            )
        } else {
            // removing the `<`, `>`
            Triple.NamedTerm(
                value = value.substring(1, value.length - 2)
            )
        }
    }

}
