package tesserakt.sparql.compiler.analyser

import tesserakt.sparql.compiler.types.Pattern
import tesserakt.sparql.compiler.types.SelectQueryAST
import tesserakt.sparql.compiler.types.Token

class SelectQueryProcessor: Analyser<SelectQueryAST>() {

    private lateinit var builder: SelectQueryAST.Builder

    override fun _process(): SelectQueryAST {
        builder = SelectQueryAST.Builder()
        processSelectQueryStart()
        return builder.build()
    }

    private fun processSelectQueryStart() {
        // consuming the SELECT token
        consumeOrBail()
        // now expecting either binding name, star or the WHERE clause
        when (token) {
            is Token.Binding -> {
                builder.addToOutput(Pattern.Binding(token as Token.Binding))
                // consuming it
                consumeOrBail()
                // continuing only accepting bindings, aggregations/operations or the start of the query
                processSelectQueryBindingOrBody()
            }
            Token.Syntax.RoundBracketStart -> {
                builder.addToOutput(use(AggregationProcessor()))
                // continuing only accepting bindings, aggregations/operations or the start of the query
                processSelectQueryBindingOrBody()
            }
            Token.Syntax.Asterisk -> {
                builder.setEverythingAsOutput()
                // only optionally `WHERE` is allowed
                consumeOrBail()
                expectToken(Token.Syntax.Where, Token.Syntax.CurlyBracketStart)
                if (token == Token.Syntax.Where) {
                    consumeOrBail()
                }
                expectToken(Token.Syntax.CurlyBracketStart)
                // processing the body now
                builder.body = use(QueryBodyProcessor())
            }
            Token.Syntax.Where -> {
                consumeOrBail()
                expectToken(Token.Syntax.CurlyBracketStart)
                // actually processing the query body now
                builder.body = use(QueryBodyProcessor())
            }
            else -> expectedBindingOrToken(Token.Syntax.Asterisk, Token.Syntax.Where, Token.Syntax.RoundBracketStart)
        }
    }

    private tailrec fun processSelectQueryBindingOrBody() {
        // now expecting either binding name, star or the WHERE clause
        when (token) {
            is Token.Binding -> {
                builder.addToOutput(Pattern.Binding(token as Token.Binding))
                // consuming it
                consumeOrBail()
                // still processing the start
                processSelectQueryBindingOrBody()
            }
            Token.Syntax.RoundBracketStart -> {
                builder.addToOutput(use(AggregationProcessor()))
                // still processing the start
                processSelectQueryBindingOrBody()
            }
            Token.Syntax.Where -> {
                consumeOrBail()
                expectToken(Token.Syntax.CurlyBracketStart)
                // actually processing the query body now
                builder.body = use(QueryBodyProcessor())
            }
            Token.Syntax.CurlyBracketStart -> {
                // actually processing the query body now
                builder.body = use(QueryBodyProcessor())
            }
            else -> expectedBindingOrToken(
                Token.Syntax.Where,
                Token.Syntax.CurlyBracketStart,
                Token.Syntax.RoundBracketStart
            )
        }
    }

}
