package core.sparql.compiler

import core.rdf.Triple
import core.sparql.compiler.Token.Syntax.*

class Analyser internal constructor(
    // iterator for the sequence being observed
    private val input: Iterator<Token>
) {

    internal data class IntermediateResult(
        val prefixes: MutableMap<String, String> = mutableMapOf(),
        val bindings: MutableSet<Pattern.Binding> = mutableSetOf(),
        var grabAllBindings: Boolean = false,
        val patterns: MutableList<Pattern> = mutableListOf()
    ) {

        lateinit var subject: Pattern.Subject
        lateinit var predicate: Pattern.Predicate
        lateinit var `object`: Pattern.Object

    }

    private val result = IntermediateResult()
    // TODO: ref to the active query, so subqueries can be referenced later,
    //  and these need pointer to the parent query for correct resolving when ending a subquery
    private var query = result

    // current token, actually kept here so `peek` does not actively `consume()`
    private lateinit var token: Token
    // to generate appropriate stacktraces
    private var index = 0

    init {
        consumeOrBail()
        processQueryStart()
    }

    /* actual parsers, calling each other recursively along the query */

    private fun processQueryStart() = when (token) {
        Prefix -> processPrefix()
        Select -> processSelectQueryStart()
        Construct -> bail("Construct queries are currently not supported.")
        else -> expectedToken(
            Prefix,
            Select,
            Construct
        )
    }

    private fun processPrefix() {
        // current token should be `PREFIX`, so consuming the next two tokens for the actual prefix
        consumeOrBail()
        val name = (token as Token.Term).value
        consumeOrBail()
        val value = (token as Token.Term).value
        // adding it to the table of prefixes
        result.prefixes[name] = value
        // advancing the rest of the query
        consumeOrBail()
        // will either go back processing another prefix, or
        return processQueryStart()
    }

    private fun processSelectQueryStart() {
        // consuming the previous token (SELECT, old binding, prefix, ...)
        consumeOrBail()
        // now expecting either binding name, star or the WHERE clause
        return when (token) {
            is Token.Term -> {
                // has to be a binding
                query.bindings.add((token as? Token.Term)?.asBinding() ?: bail("Expected binding, got $token"))
                // still processing the start
                processSelectQueryStart()
            }
            Star -> {
                query.grabAllBindings = true
                // still processing the start
                processSelectQueryStart()
            }
            Where -> {
                consumeOrBail()
                expectToken(CurlyBracketStart)
                // actually processing the query body now
                processQueryBody()
            }
            else -> expectedPatternElementOrToken(Star, Where)
        }
    }

    private fun processQueryBody() {
        consumeOrBail()
        when (token) {
            is Token.Term -> {
                processPatternSubject()
            }
            CurlyBracketStart -> {
                // either starts a subquery or a new pattern for use in UNION
                bail("Subqueries are currently not supported!")
            }
            else -> expectedPatternElementOrToken(
                CurlyBracketStart
            )
        }
    }

    private fun processPatternSubject() {
        // should have a term, converting it to a subject
        result.subject = (token as Token.Term).asSubject()
        // consuming the next token and going to the predicate section no matter what
        consumeOrBail()
        processPatternPredicate()
    }

    private fun processPatternPredicate() {
        when (token) {
            RoundBracketStart -> {
                // consuming it and setting it
                consumeOrBail()
                processPatternPredicate()
            }
            is Token.Term -> {
                // setting and continuing
                result.predicate = (token as Token.Term).asPredicate()
            }
            else -> expectedPatternElementOrToken(RoundBracketStart)
        }
    }

    private fun processPatternObject() {

    }

    private fun processPatternEnd() {

    }

    /** Consumes the next token. Bails if no other token is available **/
    private fun consumeOrBail() {
        if (!input.hasNext()) {
            bail("Unexpected end of input (last token is $token)")
        } else {
            // simplified version of `singleOrNull()` that does not check if there are tokens remaining, removing
            //  the additional `take(1)` call
            token = input.next()
            ++index
        }
    }

    // TODO: later: consumeOrYield for resulting query if end has been reached

    private fun expectToken(vararg tokens: Token) {
        if (token !in tokens) {
            expectedToken(*tokens)
        }
    }

    private fun expectPatternElementOrToken(vararg tokens: Token) {
        if (token !is Token.Term && token !in tokens) {
            expectedPatternElementOrToken(*tokens)
        }
    }

    private fun expectedToken(vararg tokens: Token): Nothing {
        bail("Unexpected $token, expected any of ${tokens.joinToString { it.syntax }}")
    }

    private fun expectedPatternElementOrToken(vararg tokens: Token): Nothing {
        bail("Unexpected $token, expected pattern element or any of ${tokens.joinToString { it.syntax }}")
    }

    private fun bail(reason: String = "Internal compiler error"): Nothing {
        throw StructuralError(problem = "Failed at token $index", description = reason)
    }

    /* helper extensions */

    private fun Token.Term.asBinding(): Pattern.Binding? {
        return if (value[0] == '?') {
            Pattern.Binding(value.substring(1))
        } else {
            null
        }
    }

    private fun Token.Term.asSubject(): Pattern.Subject =
        asBinding() ?: Pattern.Exact(asNamedTerm())

    private fun Token.Term.asPredicate(): Pattern.Predicate =
        asBinding() ?: Pattern.Exact(asNamedTerm())

    private fun Token.Term.asNamedTerm(): Triple.NamedTerm {
        return if (value == "a") {
            // TODO: use the actual ontology objects
            Triple.NamedTerm(
                value = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"
            )
        } else if (value.contains(':')) {
            Triple.NamedTerm(
                value = result.prefixes[value.substringBefore(':')]!! + value.substringAfter(':')
            )
        } else {
            // removing the `<`, `>`
            Triple.NamedTerm(
                value = value.substring(1, value.length - 2)
            )
        }
    }

}
