package dev.tesserakt.sparql.compiler.analyser

import dev.tesserakt.sparql.compiler.ast.Aggregation
import dev.tesserakt.sparql.compiler.lexer.Token
import dev.tesserakt.sparql.compiler.lexer.Token.Companion.bindingName
import dev.tesserakt.sparql.compiler.lexer.Token.Companion.literalNumericValue

/**
 * Processes structures like `MIN(?s)`, `AVG(?s) - MIN(?p)`, `COUNT(DISTINCT ?s)`
 */
class AggregatorProcessor: Analyser<Aggregation.Expression>() {

    override fun _process(): Aggregation.Expression {
        return processAggregationStatement()
    }

    // processes & consumes structures like `max(?s) - avg(?p)`
    private fun processAggregationStatement(): Aggregation.Expression {
        val builder = Aggregation.MathOp.Builder(nextAggregationOrBindingOrLiteral())
        var operator = token.operator
        while (operator != null) {
            consume()
            builder.add(
                operator = operator,
                operand = nextAggregationOrBindingOrLiteral()
            )
            operator = token.operator
        }
        return builder.build()
    }

    private fun nextAggregationOrBindingOrLiteral(): Aggregation.Expression = when (token) {
        Token.Keyword.Distinct -> {
            consume()
            expectBinding()
            Aggregation.DistinctBindingValues(token.bindingName)
                .also { consume() }
        }
        is Token.Binding -> {
            Aggregation.BindingValues(token.bindingName)
                .also { consume() }
        }
        Token.Keyword.FunCount,
        Token.Keyword.FunMin,
        Token.Keyword.FunMax,
        Token.Keyword.FunAvg -> {
            processAggregationFunction()
        }
        Token.Symbol.RoundBracketStart -> {
            processAggregationGroup()
        }
        Token.Symbol.OpMinus -> {
            consume()
            Aggregation.MathOp.Negative.of(nextAggregationOrBindingOrLiteral())
        }
        is Token.NumericLiteral -> {
            Aggregation.LiteralValue(token.literalNumericValue)
                .also { consume() }
        }
        else -> expectedBindingOrLiteralOrToken(
            Token.Keyword.Distinct,
            Token.Keyword.FunCount,
            Token.Keyword.FunMin,
            Token.Keyword.FunMax,
            Token.Keyword.FunAvg,
            Token.Symbol.RoundBracketStart
        )
    }

    private fun nextAggregationOrBinding(): Aggregation.Expression = when (token) {
        Token.Keyword.Distinct -> {
            consume()
            expectBinding()
            Aggregation.DistinctBindingValues(token.bindingName)
                .also { consume() }
        }
        is Token.Binding -> {
            Aggregation.BindingValues(token.bindingName)
                .also { consume() }
        }
        Token.Keyword.FunCount,
        Token.Keyword.FunMin,
        Token.Keyword.FunMax,
        Token.Keyword.FunAvg, -> {
            processAggregationFunction()
        }
        Token.Symbol.RoundBracketStart -> {
            processAggregationGroup()
        }
        else -> expectedBindingOrToken(
            Token.Keyword.Distinct,
            Token.Keyword.FunCount,
            Token.Keyword.FunMin,
            Token.Keyword.FunMax,
            Token.Keyword.FunAvg,
            Token.Symbol.RoundBracketStart
        )
    }

    private val Token.operator: Aggregation.MathOp.Operator?
        get() = when (this) {
            Token.Symbol.OpPlus -> Aggregation.MathOp.Operator.SUM
            Token.Symbol.OpMinus -> Aggregation.MathOp.Operator.DIFFERENCE
            Token.Symbol.ForwardSlash -> Aggregation.MathOp.Operator.DIVISION
            Token.Symbol.Asterisk -> Aggregation.MathOp.Operator.PRODUCT
            else -> null
        }

    // processes & consumes structures like `(max(?s) - avg(?p))`
    private fun processAggregationGroup(): Aggregation.Expression {
        // simply calling the `processAggregationStatement` again, level of recursion depth = number of parentheses
        // consuming the '('
        consume()
        return processAggregationStatement().also {
            expectToken(Token.Symbol.RoundBracketEnd)
            consume()
        }
    }

    // processes & consumes structures like `max(?s)`
    private fun processAggregationFunction(): Aggregation.FuncCall {
        val type = when (token) {
            Token.Keyword.FunCount -> Aggregation.FuncCall.Type.COUNT
            Token.Keyword.FunMin -> Aggregation.FuncCall.Type.MIN
            Token.Keyword.FunMax -> Aggregation.FuncCall.Type.MAX
            Token.Keyword.FunAvg -> Aggregation.FuncCall.Type.AVG
            else -> bail()
        }
        consume()
        expectToken(Token.Symbol.RoundBracketStart)
        consume()
        val input = nextAggregationOrBinding()
        expectToken(Token.Symbol.RoundBracketEnd)
        consume()
        return Aggregation.FuncCall(type = type, input = input)
    }

}