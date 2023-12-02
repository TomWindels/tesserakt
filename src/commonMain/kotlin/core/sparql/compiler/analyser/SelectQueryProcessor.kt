package core.sparql.compiler.analyser

import core.sparql.compiler.types.Pattern.Companion.asBinding
import core.sparql.compiler.types.Patterns
import core.sparql.compiler.types.SelectQueryAST
import core.sparql.compiler.types.Token

@Suppress("SpellCheckingInspection")
class SelectQueryProcessor: Analyser<SelectQueryAST>() {

    private lateinit var builder: SelectQueryAST.Builder

    override fun _process(): SelectQueryAST {
        builder = SelectQueryAST.Builder()
        processSelectQueryStart()
        return builder.build()
    }

    private fun processSelectQueryStart() {
        // consuming the previous token (SELECT, old binding, prefix, ...)
        consumeOrBail()
        // now expecting either binding name, star or the WHERE clause
        return when (token) {
            is Token.Binding -> {
                builder.bindings.add(token.asBinding() ?: bail("Expected binding, got $token"))
                // still processing the start
                processSelectQueryStart()
            }
            Token.Syntax.Star -> {
                builder.grabAllBindings = true
                // still processing the start
                processSelectQueryStart()
            }
            Token.Syntax.Where -> {
                consumeOrBail()
                expectToken(Token.Syntax.CurlyBracketStart)
                // actually processing the query body now
                processQueryBody()
            }
            else -> expectedBindingOrToken(Token.Syntax.Star, Token.Syntax.Where)
        }
    }

    private fun processQueryBody() {
        consumeOrBail()
        when (token) {
            // binding or term, so the start of a block is happening here
            !is Token.Syntax -> {
                builder.body.addPatterns(use(PatternProcessor()))
            }
            Token.Syntax.CurlyBracketStart -> processSubqueryBody()
            else -> expectedPatternElementOrBindingOrToken(
                Token.Syntax.CurlyBracketStart
            )
        }
    }

    private fun processSubqueryBody() {
        // should be a `{`
        consumeOrBail()
        when (token) {
            // binding or term, so the start of a block is happening here
            !is Token.Syntax -> processUnion()
            else -> bail("Complex subqueries are currently not supported!")
        }
    }

    private fun processUnion() {
        val patterns = mutableListOf<Patterns>()
        while (true) {
            patterns.add(use(PatternProcessor()))
            expectToken(Token.Syntax.CurlyBracketEnd)
            consumeOrBail()
            if (token == Token.Syntax.Union) {
                // continuing
                consumeOrBail()
                expectToken(Token.Syntax.CurlyBracketStart)
                consumeOrBail()
                expectPatternElementOrBinding()
                // looping back up top
            } else {
                // inserting all patterns and exiting
                builder.body.addUnion(patterns)
                return
            }
        }
    }

}
