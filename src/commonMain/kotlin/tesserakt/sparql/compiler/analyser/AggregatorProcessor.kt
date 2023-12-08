package tesserakt.sparql.compiler.analyser

import tesserakt.sparql.compiler.types.Aggregation
import tesserakt.sparql.compiler.types.Token
import tesserakt.sparql.compiler.types.Token.Companion.bindingName
import tesserakt.sparql.compiler.types.Token.Companion.literalNumericValue

/**
 * Processes structures like `MIN(?s)`, `AVG(?s) - MIN(?p)`, `COUNT(DISTINCT ?s)`
 */
class AggregatorProcessor: Analyser<Aggregation.Aggregator>() {

    override fun _process(): Aggregation.Aggregator {
        return processAggregationStatement()
    }

    // processes & consumes structures like `max(?s) - avg(?p)`
    private fun processAggregationStatement(): Aggregation.Aggregator {
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

    private fun nextAggregationOrBindingOrLiteral(): Aggregation.Aggregator = when (token) {
        Token.Syntax.Distinct -> {
            consume()
            expectBinding()
            Aggregation.DistinctBindingValues(token.bindingName)
                .also { consume() }
        }
        is Token.Binding -> {
            Aggregation.BindingValues(token.bindingName)
                .also { consume() }
        }
        Token.Syntax.FunCount,
        Token.Syntax.FunMin,
        Token.Syntax.FunMax,
        Token.Syntax.FunAvg -> {
            processAggregationFunction()
        }
        Token.Syntax.RoundBracketStart -> {
            processAggregationGroup()
        }
        Token.Syntax.OpMinus -> {
            consume()
            Aggregation.MathOp.Negative.of(nextAggregationOrBindingOrLiteral())
        }
        is Token.NumericLiteral -> {
            Aggregation.LiteralValue(token.literalNumericValue)
                .also { consume() }
        }
        else -> expectedBindingOrLiteralOrToken(
            Token.Syntax.Distinct,
            Token.Syntax.FunCount,
            Token.Syntax.FunMin,
            Token.Syntax.FunMax,
            Token.Syntax.FunAvg,
            Token.Syntax.RoundBracketStart
        )
    }

    private fun nextAggregationOrBinding(): Aggregation.Aggregator = when (token) {
        Token.Syntax.Distinct -> {
            consume()
            expectBinding()
            Aggregation.DistinctBindingValues(token.bindingName)
                .also { consume() }
        }
        is Token.Binding -> {
            Aggregation.BindingValues(token.bindingName)
                .also { consume() }
        }
        Token.Syntax.FunCount,
        Token.Syntax.FunMin,
        Token.Syntax.FunMax,
        Token.Syntax.FunAvg, -> {
            processAggregationFunction()
        }
        Token.Syntax.RoundBracketStart -> {
            processAggregationGroup()
        }
        else -> expectedBindingOrToken(
            Token.Syntax.Distinct,
            Token.Syntax.FunCount,
            Token.Syntax.FunMin,
            Token.Syntax.FunMax,
            Token.Syntax.FunAvg,
            Token.Syntax.RoundBracketStart
        )
    }

    private val Token.operator: Aggregation.MathOp.Operator?
        get() = when (this) {
            Token.Syntax.OpPlus -> Aggregation.MathOp.Operator.SUM
            Token.Syntax.OpMinus -> Aggregation.MathOp.Operator.DIFFERENCE
            Token.Syntax.ForwardSlash -> Aggregation.MathOp.Operator.DIVISION
            Token.Syntax.Asterisk -> Aggregation.MathOp.Operator.PRODUCT
            else -> null
        }

    // processes & consumes structures like `(max(?s) - avg(?p))`
    private fun processAggregationGroup(): Aggregation.Aggregator {
        // simply calling the `processAggregationStatement` again, level of recursion depth = number of parentheses
        // consuming the '('
        consume()
        return processAggregationStatement().also {
            expectToken(Token.Syntax.RoundBracketEnd)
            consume()
        }
    }

    // processes & consumes structures like `max(?s)`
    private fun processAggregationFunction(): Aggregation.Builtin {
        val type = when (token) {
            Token.Syntax.FunCount -> Aggregation.Builtin.Type.COUNT
            Token.Syntax.FunMin -> Aggregation.Builtin.Type.MIN
            Token.Syntax.FunMax -> Aggregation.Builtin.Type.MAX
            Token.Syntax.FunAvg -> Aggregation.Builtin.Type.AVG
            else -> bail()
        }
        consume()
        expectToken(Token.Syntax.RoundBracketStart)
        consume()
        val input = nextAggregationOrBinding()
        expectToken(Token.Syntax.RoundBracketEnd)
        consume()
        return Aggregation.Builtin(type = type, input = input)
    }

}