package tesserakt.sparql.compiler.analyser

import tesserakt.sparql.compiler.types.Aggregation
import tesserakt.sparql.compiler.types.Token

class AggregatorProcessor: Analyser<Aggregation>() {

    override fun _process(): Aggregation {
        // consuming the first `(`
        consumeOrBail()
        val aggregation = processAggregationOperation()
        // should now be pointing to `AS`, with as next token a binding for the output
        expectToken(Token.Syntax.As)
        consumeOrBail()
        expectBinding()
        val result = Aggregation(
            root = aggregation,
            output = token as Token.Binding
        )
        // consuming it as it is part of the result
        consumeOrBail()
        // also consuming the closing `)`
        expectToken(Token.Syntax.RoundBracketEnd)
        consumeOrBail()
        return result
    }

    private fun nextAggregationOrBindingOrLiteral(): Aggregation.Aggregator = when (token) {
        Token.Syntax.Distinct -> {
            consumeOrBail()
            expectBinding()
            Aggregation.DistinctBindingValues(token as Token.Binding)
                .also { consumeOrBail() }
        }
        is Token.Binding -> {
            Aggregation.BindingValues(token as Token.Binding)
                .also { consumeOrBail() }
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
        is Token.NumericLiteral -> {
            Aggregation.LiteralValue(token as Token.NumericLiteral)
                .also { consumeOrBail() }
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
            consumeOrBail()
            expectBinding()
            Aggregation.DistinctBindingValues(token as Token.Binding)
                .also { consumeOrBail() }
        }
        is Token.Binding -> {
            Aggregation.BindingValues(token as Token.Binding)
                .also { consumeOrBail() }
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

    // processes & consumes structures like `max(?s) - avg(?p)`
    private fun processAggregationOperation(): Aggregation.Aggregator {
        val builder = Aggregation.MathOperation.Builder(nextAggregationOrBindingOrLiteral())
        var operator = token.operator
        while (operator != null) {
            consumeOrBail()
            builder.add(
                operator = operator,
                aggregator = nextAggregationOrBindingOrLiteral()
            )
            operator = token.operator
        }
        return builder.build()
    }

    private val Token.operator: Aggregation.MathOperation.Operator?
        get() = when (this) {
            Token.Syntax.OpPlus -> Aggregation.MathOperation.Operator.SUM
            Token.Syntax.OpMinus -> Aggregation.MathOperation.Operator.DIFFERENCE
            Token.Syntax.ForwardSlash -> Aggregation.MathOperation.Operator.DIVISION
            Token.Syntax.Asterisk -> Aggregation.MathOperation.Operator.PRODUCT
            else -> null
        }

    // processes & consumes structures like `(max(?s) - avg(?p))`
    private fun processAggregationGroup(): Aggregation.Aggregator {
        // simply calling the `processAggregationStatement` again, level of recursion depth = number of parentheses
        consumeOrBail()
        return processAggregationOperation().also {
            expectToken(Token.Syntax.RoundBracketEnd)
            consumeOrBail()
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
        consumeOrBail()
        expectToken(Token.Syntax.RoundBracketStart)
        consumeOrBail()
        val input = nextAggregationOrBinding()
        expectToken(Token.Syntax.RoundBracketEnd)
        consumeOrBail()
        return Aggregation.Builtin(type = type, input = input)
    }

}
