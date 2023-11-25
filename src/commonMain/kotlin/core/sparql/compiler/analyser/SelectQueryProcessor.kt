package core.sparql.compiler.analyser

import core.sparql.compiler.Pattern
import core.sparql.compiler.Token

class SelectQueryProcessor: Analyser<SelectQueryProcessor.IntermediateResult>() {

    data class IntermediateResult(
        val prefixes: MutableMap<String, String> = mutableMapOf(),
        val bindings: MutableSet<Pattern.Binding> = mutableSetOf(),
        var grabAllBindings: Boolean = false,
        val patterns: MutableList<Pattern> = mutableListOf()
    )

    private lateinit var result: IntermediateResult

    override fun _process(): IntermediateResult {
        result = IntermediateResult()
        processQueryStart()
        return result
    }

    private fun processQueryStart() = when (token) {
        Token.Syntax.Prefix -> processPrefix()
        Token.Syntax.Select -> processSelectQueryStart()
        Token.Syntax.Construct -> bail("Construct queries are currently not supported.")
        else -> expectedToken(
            Token.Syntax.Prefix,
            Token.Syntax.Select,
            Token.Syntax.Construct
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
                result.bindings.add((token as? Token.Term)?.asBinding() ?: bail("Expected binding, got $token"))
                // still processing the start
                processSelectQueryStart()
            }
            Token.Syntax.Star -> {
                result.grabAllBindings = true
                // still processing the start
                processSelectQueryStart()
            }
            Token.Syntax.Where -> {
                consumeOrBail()
                expectToken(Token.Syntax.CurlyBracketStart)
                // actually processing the query body now
                processQueryBody()
            }
            else -> expectedPatternElementOrToken(Token.Syntax.Star, Token.Syntax.Where)
        }
    }

    private fun processQueryBody() {
        consumeOrBail()
        when (token) {
            is Token.Term -> {
                result.patterns.addAll(PatternProcessor(prefixes = result.prefixes).chain(this))
            }
            Token.Syntax.CurlyBracketStart -> {
                // either starts a subquery or a new pattern for use in UNION
                bail("Subqueries are currently not supported!")
            }
            else -> expectedPatternElementOrToken(
                Token.Syntax.CurlyBracketStart
            )
        }
    }

}
