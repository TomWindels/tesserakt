package dev.tesserakt.sparql.compiler.analyser

import dev.tesserakt.sparql.compiler.ast.ExpressionAST
import dev.tesserakt.sparql.compiler.lexer.Token
import dev.tesserakt.sparql.compiler.lexer.Token.Companion.bindingName
import dev.tesserakt.sparql.compiler.lexer.Token.Companion.literalNumericValue

/**
 * Processes structures like `MIN(?s)`, `AVG(?s) - MIN(?p)`, `COUNT(DISTINCT ?s)`
 */
class AggregatorProcessor: Analyser<ExpressionAST>() {

    override fun _process(): ExpressionAST {
        val expr = processMathStatement()
        // filters should be top level afaik, so checking it after completing the math expression stuff
        val filterOperand = token.filterOperand
        return if (filterOperand != null) {
            consume()
            val rhs = processMathStatement()
            ExpressionAST.Filter(lhs = expr, rhs = rhs, operand = filterOperand)
        } else {
            expr
        }
    }

    // processes & consumes structures like `max(?s) - avg(?p)`
    private fun processMathStatement(): ExpressionAST {
        val builder = ExpressionAST.MathOp.Builder(nextAggregationOrBindingOrLiteral())
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

    private fun nextAggregationOrBindingOrLiteral(): ExpressionAST = when (token) {
        is Token.Binding -> {
            ExpressionAST.BindingValues(token.bindingName)
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
            ExpressionAST.MathOp.Negative.of(nextAggregationOrBindingOrLiteral())
        }
        is Token.NumericLiteral -> {
            ExpressionAST.LiteralValue(token.literalNumericValue)
                .also { consume() }
        }
        else -> expectedBindingOrLiteralOrToken(
            Token.Keyword.AggCount,
            Token.Keyword.AggMin,
            Token.Keyword.AggMax,
            Token.Keyword.AggAvg,
            Token.Symbol.RoundBracketStart
        )
    }

    private val Token.operator: ExpressionAST.MathOp.Operator?
        get() = when (this) {
            Token.Symbol.OpPlus -> ExpressionAST.MathOp.Operator.SUM
            Token.Symbol.OpMinus -> ExpressionAST.MathOp.Operator.DIFFERENCE
            Token.Symbol.ForwardSlash -> ExpressionAST.MathOp.Operator.DIVISION
            Token.Symbol.Asterisk -> ExpressionAST.MathOp.Operator.PRODUCT
            else -> null
        }

    private val Token.filterOperand: ExpressionAST.Filter.Operand?
        get() = when (this) {
            Token.Symbol.AngularBracketStart -> ExpressionAST.Filter.Operand.LESS_THAN
            Token.Symbol.AngularBracketEnd -> ExpressionAST.Filter.Operand.GREATER_THAN
            Token.Symbol.LTEQ -> ExpressionAST.Filter.Operand.LESS_THAN_OR_EQ
            Token.Symbol.GTEQ -> ExpressionAST.Filter.Operand.GREATER_THAN_OR_EQ
            Token.Symbol.Equals -> ExpressionAST.Filter.Operand.EQUAL
            else -> null
        }

    // processes & consumes structures like `(max(?s) - avg(?p))`
    private fun processAggregationGroup(): ExpressionAST {
        // simply calling the `processAggregationStatement` again, level of recursion depth = number of parentheses
        // consuming the '('
        consume()
        return processMathStatement().also {
            expectToken(Token.Symbol.RoundBracketEnd)
            consume()
        }
    }

    // processes & consumes structures like `max(?s)`
    private fun processAggregationFunction(): ExpressionAST.FuncCall {
        val type = token.aggType()
        consume()
        expectToken(Token.Symbol.RoundBracketStart)
        consume()
        val distinct = if (token == Token.Keyword.Distinct) {
            consume()
            true
        } else false
        expectBinding()
        val input = ExpressionAST.BindingValues(token.bindingName)
        consume()
        expectToken(Token.Symbol.RoundBracketEnd)
        consume()
        return ExpressionAST.FuncCall(type = type, input = input, distinct = distinct)
    }

    private fun Token.aggType() = when (this) {
        Token.Keyword.AggCount -> ExpressionAST.FuncCall.Type.COUNT
        Token.Keyword.AggSum -> ExpressionAST.FuncCall.Type.SUM
        Token.Keyword.AggMin -> ExpressionAST.FuncCall.Type.MIN
        Token.Keyword.AggMax -> ExpressionAST.FuncCall.Type.MAX
        Token.Keyword.AggAvg -> ExpressionAST.FuncCall.Type.AVG
        Token.Keyword.AggGroupConcat -> ExpressionAST.FuncCall.Type.GROUP_CONCAT
        Token.Keyword.AggSample -> ExpressionAST.FuncCall.Type.SAMPLE
        else -> bail()
    }

}