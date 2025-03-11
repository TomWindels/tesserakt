package dev.tesserakt.sparql.compiler.analyser

import dev.tesserakt.sparql.types.ast.ExpressionAST
import dev.tesserakt.sparql.compiler.lexer.Token
import dev.tesserakt.sparql.compiler.lexer.Token.Companion.bindingName
import dev.tesserakt.sparql.compiler.lexer.Token.Companion.literalNumericValue
import dev.tesserakt.sparql.compiler.lexer.Token.Companion.literalTextValue

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
            ExpressionAST.Conditional(lhs = expr, rhs = rhs, operand = filterOperand)
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
            ExpressionAST.NumericLiteralValue(token.literalNumericValue)
                .also { consume() }
        }
        is Token.StringLiteral -> {
            ExpressionAST.StringLiteralValue(token.literalTextValue)
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

    private val Token.operator: ExpressionAST.MathOp.Operator?
        get() = when (this) {
            Token.Symbol.OpPlus -> ExpressionAST.MathOp.Operator.SUM
            Token.Symbol.OpMinus -> ExpressionAST.MathOp.Operator.DIFF
            Token.Symbol.ForwardSlash -> ExpressionAST.MathOp.Operator.DIV
            Token.Symbol.Asterisk -> ExpressionAST.MathOp.Operator.MUL
            else -> null
        }

    private val Token.filterOperand: ExpressionAST.Conditional.Operand?
        get() = when (this) {
            Token.Symbol.AngularBracketStart -> ExpressionAST.Conditional.Operand.LESS_THAN
            Token.Symbol.AngularBracketEnd -> ExpressionAST.Conditional.Operand.GREATER_THAN
            Token.Symbol.LTEQ -> ExpressionAST.Conditional.Operand.LESS_THAN_OR_EQ
            Token.Symbol.GTEQ -> ExpressionAST.Conditional.Operand.GREATER_THAN_OR_EQ
            Token.Symbol.Equals -> ExpressionAST.Conditional.Operand.EQUAL
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
    private fun processAggregationFunction(): ExpressionAST.BindingAggregate {
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
        return ExpressionAST.BindingAggregate(type = type, input = input, distinct = distinct)
    }

    // processes & consumes structures like `concat("A", ?s)`
    private fun processRegularFunction(): ExpressionAST.FuncCall {
        val name = token.syntax
        consume()
        expectToken(Token.Symbol.RoundBracketStart)
        consume()
        if (token == Token.Symbol.RoundBracketEnd) {
            return ExpressionAST.FuncCall(name = name, args = emptyList())
        }
        val args = mutableListOf<ExpressionAST>()
        while (true) {
            args += nextAggregationOrBindingOrLiteral()
            when (token) {
                Token.Symbol.RoundBracketEnd -> break
                Token.Symbol.Comma -> consume()
                else -> expectedToken(Token.Symbol.RoundBracketEnd, Token.Symbol.Comma)
            }
        }
        consume()
        return ExpressionAST.FuncCall(name = name, args = args)
    }

    private fun Token.aggType() = when (this) {
        Token.Keyword.AggCount -> ExpressionAST.BindingAggregate.Type.COUNT
        Token.Keyword.AggSum -> ExpressionAST.BindingAggregate.Type.SUM
        Token.Keyword.AggMin -> ExpressionAST.BindingAggregate.Type.MIN
        Token.Keyword.AggMax -> ExpressionAST.BindingAggregate.Type.MAX
        Token.Keyword.AggAvg -> ExpressionAST.BindingAggregate.Type.AVG
        Token.Keyword.AggGroupConcat -> ExpressionAST.BindingAggregate.Type.GROUP_CONCAT
        Token.Keyword.AggSample -> ExpressionAST.BindingAggregate.Type.SAMPLE
        else -> bail()
    }

}