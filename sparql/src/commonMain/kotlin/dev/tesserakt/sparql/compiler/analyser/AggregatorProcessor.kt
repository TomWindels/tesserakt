package dev.tesserakt.sparql.compiler.analyser

import dev.tesserakt.sparql.compiler.lexer.Token
import dev.tesserakt.sparql.compiler.lexer.Token.Companion.bindingName
import dev.tesserakt.sparql.compiler.lexer.Token.Companion.literalNumericValue
import dev.tesserakt.sparql.compiler.lexer.Token.Companion.literalTextValue
import dev.tesserakt.sparql.types.runtime.element.Expression

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
            Expression.Conditional(lhs = expr, rhs = rhs, operand = filterOperand)
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
            Expression.NumericLiteralValue(token.literalNumericValue)
                .also { consume() }
        }
        is Token.StringLiteral -> {
            Expression.StringLiteralValue(token.literalTextValue)
                .also { consume() }
        }
        Token.Keyword.StringLength,
        Token.Keyword.Concat -> {
            processRegularFunction()
        }
        else -> expectedBindingOrLiteralOrToken(
            Token.Keyword.StringLength,
            Token.Keyword.Concat,
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
            Token.Symbol.OpMinus -> Expression.MathOp.Operator.DIFF
            Token.Symbol.ForwardSlash -> Expression.MathOp.Operator.DIV
            Token.Symbol.Asterisk -> Expression.MathOp.Operator.MUL
            else -> null
        }

    private val Token.filterOperand: Expression.Conditional.Operand?
        get() = when (this) {
            Token.Symbol.AngularBracketStart -> Expression.Conditional.Operand.LESS_THAN
            Token.Symbol.AngularBracketEnd -> Expression.Conditional.Operand.GREATER_THAN
            Token.Symbol.LTEQ -> Expression.Conditional.Operand.LESS_THAN_OR_EQ
            Token.Symbol.GTEQ -> Expression.Conditional.Operand.GREATER_THAN_OR_EQ
            Token.Symbol.Equals -> Expression.Conditional.Operand.EQUAL
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
    private fun processAggregationFunction(): Expression.BindingAggregate {
        val type = token.aggType()
        consume()
        expectToken(Token.Symbol.RoundBracketStart)
        consume()
        val distinct = if (token == Token.Keyword.Distinct) {
            consume()
            true
        } else false
        expectBinding()
        val input = Expression.BindingValues(token.bindingName)
        consume()
        expectToken(Token.Symbol.RoundBracketEnd)
        consume()
        return Expression.BindingAggregate(type = type, input = input, distinct = distinct)
    }

    // processes & consumes structures like `concat("A", ?s)`
    private fun processRegularFunction(): Expression.FuncCall {
        val name = token.syntax
        consume()
        expectToken(Token.Symbol.RoundBracketStart)
        consume()
        if (token == Token.Symbol.RoundBracketEnd) {
            return Expression.FuncCall(name = name, args = emptyList())
        }
        val args = mutableListOf<Expression>()
        while (true) {
            args += nextAggregationOrBindingOrLiteral()
            when (token) {
                Token.Symbol.RoundBracketEnd -> break
                Token.Symbol.Comma -> consume()
                else -> expectedToken(Token.Symbol.RoundBracketEnd, Token.Symbol.Comma)
            }
        }
        consume()
        return Expression.FuncCall(name = name, args = args)
    }

    private fun Token.aggType() = when (this) {
        Token.Keyword.AggCount -> Expression.BindingAggregate.Type.COUNT
        Token.Keyword.AggSum -> Expression.BindingAggregate.Type.SUM
        Token.Keyword.AggMin -> Expression.BindingAggregate.Type.MIN
        Token.Keyword.AggMax -> Expression.BindingAggregate.Type.MAX
        Token.Keyword.AggAvg -> Expression.BindingAggregate.Type.AVG
        Token.Keyword.AggGroupConcat -> Expression.BindingAggregate.Type.GROUP_CONCAT
        Token.Keyword.AggSample -> Expression.BindingAggregate.Type.SAMPLE
        else -> bail()
    }

}