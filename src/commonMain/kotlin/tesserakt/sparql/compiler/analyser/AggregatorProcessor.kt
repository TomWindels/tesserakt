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

    private fun nextAggregationOrBinding(): Aggregation.Aggregator = when (token) {
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
    private fun processAggregationGroup(): Aggregation.Aggregator {
        // simply calling the `processAggregationStatement` again, level of recursion depth = number of parentheses
        // consuming the '('
        consume()
        return processAggregationStatement().also {
            expectToken(Token.Symbol.RoundBracketEnd)
            consume()
        }
    }

    // processes & consumes structures like `max(?s)`
    private fun processAggregationFunction(): Aggregation.Builtin {
        val type = when (token) {
            Token.Keyword.FunCount -> Aggregation.Builtin.Type.COUNT
            Token.Keyword.FunMin -> Aggregation.Builtin.Type.MIN
            Token.Keyword.FunMax -> Aggregation.Builtin.Type.MAX
            Token.Keyword.FunAvg -> Aggregation.Builtin.Type.AVG
            else -> bail()
        }
        consume()
        expectToken(Token.Symbol.RoundBracketStart)
        consume()
        val input = nextAggregationOrBinding()
        expectToken(Token.Symbol.RoundBracketEnd)
        consume()
        return Aggregation.Builtin(type = type, input = input)
    }

}