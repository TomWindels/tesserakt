package dev.tesserakt.sparql.compiler.analyser

import dev.tesserakt.sparql.compiler.ast.ExpressionAST
import dev.tesserakt.sparql.compiler.ast.PatternAST
import dev.tesserakt.sparql.compiler.ast.SelectQueryAST
import dev.tesserakt.sparql.compiler.lexer.Token

class SelectQueryProcessor: Analyser<SelectQueryAST>() {

    private lateinit var builder: SelectQueryAST.Builder

    override fun _process(): SelectQueryAST {
        builder = SelectQueryAST.Builder()
        processSelectQueryStart()
        return builder.build()
    }

    private fun processSelectQueryStart() {
        // consuming the SELECT token
        consume()
        // now expecting either binding name, star or the WHERE clause
        when (token) {
            is Token.Binding -> {
                builder.addToOutput(PatternAST.Binding(token as Token.Binding))
                // consuming it
                consume()
                // continuing only accepting bindings, aggregations/operations or the start of the query
                processSelectQueryBindingOrBody()
            }
            Token.Symbol.RoundBracketStart -> {
                builder.addToOutput(use(AggregationProcessor()))
                // continuing only accepting bindings, aggregations/operations or the start of the query
                processSelectQueryBindingOrBody()
            }
            Token.Symbol.Asterisk -> {
                builder.setEverythingAsOutput()
                // only optionally `WHERE` is allowed
                consume()
                expectToken(Token.Keyword.Where, Token.Symbol.CurlyBracketStart)
                if (token == Token.Keyword.Where) {
                    consume()
                }
                expectToken(Token.Symbol.CurlyBracketStart)
                // actually processing the query body now
                processBody()
            }
            Token.Keyword.Where -> {
                consume()
                expectToken(Token.Symbol.CurlyBracketStart)
                // actually processing the query body now
                processBody()
            }
            else -> expectedBindingOrToken(Token.Symbol.Asterisk, Token.Keyword.Where, Token.Symbol.RoundBracketStart)
        }
    }

    private tailrec fun processSelectQueryBindingOrBody() {
        // now expecting either binding name, star or the WHERE clause
        when (token) {
            is Token.Binding -> {
                builder.addToOutput(PatternAST.Binding(token as Token.Binding))
                // consuming it
                consume()
                // still processing the start
                processSelectQueryBindingOrBody()
            }
            Token.Symbol.RoundBracketStart -> {
                builder.addToOutput(use(AggregationProcessor()))
                // still processing the start
                processSelectQueryBindingOrBody()
            }
            Token.Keyword.Where -> {
                consume()
                expectToken(Token.Symbol.CurlyBracketStart)
                // actually processing the query body now
                processBody()
            }
            Token.Symbol.CurlyBracketStart -> {
                processBody()
            }
            else -> expectedBindingOrToken(
                Token.Keyword.Where,
                Token.Symbol.CurlyBracketStart,
                Token.Symbol.RoundBracketStart
            )
        }
    }

    private fun processBody() {
        // consuming the starting `{`
        consume()
        // actually processing the query body now
        builder.body = use(QueryBodyProcessor())
        // reading potential modifiers
        processQueryEnd()
    }

    private fun processQueryEnd() {
        while (true) {
            when (token) {
                Token.Keyword.Order -> processOrdering()
                Token.Keyword.Group -> processGrouping()
                else -> return // nothing for us to consume here
            }
        }
    }

    private fun processOrdering() {
        if (builder.ordering != null) {
            bail("Multiple order statements are not supported!")
        }
        expectToken(Token.Keyword.Order)
        consume()
        expectToken(Token.Keyword.By)
        consume()
        builder.ordering = use(AggregatorProcessor())
    }

    private fun processGrouping() {
        if (builder.grouping != null) {
            bail("Multiple group statements are not supported!")
        }
        expectToken(Token.Keyword.Group)
        consume()
        expectToken(Token.Keyword.By)
        consume()
        builder.grouping = use(AggregatorProcessor())
        if (token == Token.Keyword.Having) {
            consume()
            expectToken(Token.Symbol.RoundBracketStart)
            consume()
            builder.groupingFilter = use(AggregatorProcessor()) as? ExpressionAST.Filter
                ?: bail("Group filter expression expected")
            expectToken(Token.Symbol.RoundBracketEnd)
            consume()
        }
    }

}
