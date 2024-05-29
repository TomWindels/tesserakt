package dev.tesserakt.sparql.compiler.analyser

import dev.tesserakt.sparql.compiler.ast.Expression
import dev.tesserakt.sparql.compiler.lexer.Token
import dev.tesserakt.sparql.compiler.lexer.Token.Companion.bindingName
import dev.tesserakt.sparql.compiler.lexer.Token.Companion.literalNumericValue

/**
 * Processes structures like `MIN(?s)`, `AVG(?s) - MIN(?p)`, `COUNT(DISTINCT ?s)`
 */
class AggregatorProcessor: Analyser<Expression>() {

    override fun _process(): Expression {
        val expr = processMathStatement()
        // filters should be top level afaik, so checking it after completing the math expression stuff
        val filterOperand = token.filterOperand
        return if (filterOperand != null) {
            consume()
            val rhs = processMathStatement()
            Expression.Filter(lhs = expr, rhs = rhs, operand = filterOperand)
        } else {
            expr
        }
    }

    // processes & consumes structures like `max(?s) - avg(?p)`
    private fun processMathStatement(): Expression {
        val builder = Expression.MathOp.Builder(nextAggregationOrBindingOrLiteral())
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

    private fun nextAggregationOrBindingOrLiteral(): Expression = when (token) {
        Token.Keyword.Distinct -> {
            consume()
            expectBinding()
            Expression.DistinctBindingValues(token.bindingName)
                .also { consume() }
        }
        is Token.Binding -> {
            Expression.BindingValues(token.bindingName)
                .also { consume() }
        }
        Token.Keyword.AggCount,
        Token.Keyword.AggSum,
        Token.Keyword.AggMin,
        Token.Keyword.AggMax,
        Token.Keyword.AggAvg,
        Token.Keyword.AggGroupConcat,
        Token.Keyword.AggSample -> {
            processAggregationFunction()
        }
        Token.Symbol.RoundBracketStart -> {
            processAggregationGroup()
        }
        Token.Symbol.OpMinus -> {
            consume()
            Expression.MathOp.Negative.of(nextAggregationOrBindingOrLiteral())
        }
        is Token.NumericLiteral -> {
            Expression.LiteralValue(token.literalNumericValue)
                .also { consume() }
        }
        else -> expectedBindingOrLiteralOrToken(
            Token.Keyword.Distinct,
            Token.Keyword.AggCount,
            Token.Keyword.AggMin,
            Token.Keyword.AggMax,
            Token.Keyword.AggAvg,
            Token.Symbol.RoundBracketStart
        )
    }

    private fun nextAggregationOrBinding(): Expression = when (token) {
        Token.Keyword.Distinct -> {
            consume()
            expectBinding()
            Expression.DistinctBindingValues(token.bindingName)
                .also { consume() }
        }
        is Token.Binding -> {
            Expression.BindingValues(token.bindingName)
                .also { consume() }
        }
        Token.Keyword.AggCount,
        Token.Keyword.AggMin,
        Token.Keyword.AggMax,
        Token.Keyword.AggAvg, -> {
            processAggregationFunction()
        }
        Token.Symbol.RoundBracketStart -> {
            processAggregationGroup()
        }
        else -> expectedBindingOrToken(
            Token.Keyword.Distinct,
            Token.Keyword.AggCount,
            Token.Keyword.AggMin,
            Token.Keyword.AggMax,
            Token.Keyword.AggAvg,
            Token.Symbol.RoundBracketStart
        )
    }

    private val Token.operator: Expression.MathOp.Operator?
        get() = when (this) {
            Token.Symbol.OpPlus -> Expression.MathOp.Operator.SUM
            Token.Symbol.OpMinus -> Expression.MathOp.Operator.DIFFERENCE
            Token.Symbol.ForwardSlash -> Expression.MathOp.Operator.DIVISION
            Token.Symbol.Asterisk -> Expression.MathOp.Operator.PRODUCT
            else -> null
        }

    private val Token.filterOperand: Expression.Filter.Operand?
        get() = when (this) {
            Token.Symbol.AngularBracketStart -> Expression.Filter.Operand.LESS_THAN
            Token.Symbol.AngularBracketEnd -> Expression.Filter.Operand.GREATER_THAN
            Token.Symbol.LTEQ -> Expression.Filter.Operand.LESS_THAN_OR_EQ
            Token.Symbol.GTEQ -> Expression.Filter.Operand.GREATER_THAN_OR_EQ
            Token.Symbol.Equals -> Expression.Filter.Operand.EQUAL
            else -> null
        }

    // processes & consumes structures like `(max(?s) - avg(?p))`
    private fun processAggregationGroup(): Expression {
        // simply calling the `processAggregationStatement` again, level of recursion depth = number of parentheses
        // consuming the '('
        consume()
        return processMathStatement().also {
            expectToken(Token.Symbol.RoundBracketEnd)
            consume()
        }
    }

    // processes & consumes structures like `max(?s)`
    private fun processAggregationFunction(): Expression.FuncCall {
        val type = token.aggType()
        consume()
        expectToken(Token.Symbol.RoundBracketStart)
        consume()
        val input = nextAggregationOrBinding()
        expectToken(Token.Symbol.RoundBracketEnd)
        consume()
        return Expression.FuncCall(type = type, input = input)
    }

    private fun Token.aggType() = when (this) {
        Token.Keyword.AggCount -> Expression.FuncCall.Type.COUNT
        Token.Keyword.AggSum -> Expression.FuncCall.Type.SUM
        Token.Keyword.AggMin -> Expression.FuncCall.Type.MIN
        Token.Keyword.AggMax -> Expression.FuncCall.Type.MAX
        Token.Keyword.AggAvg -> Expression.FuncCall.Type.AVG
        Token.Keyword.AggGroupConcat -> Expression.FuncCall.Type.GROUP_CONCAT
        Token.Keyword.AggSample -> Expression.FuncCall.Type.SAMPLE
        else -> bail()
    }

}