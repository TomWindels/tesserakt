package dev.tesserakt.sparql.compiler.analyser

import dev.tesserakt.rdf.ontology.XSD
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.compiler.lexer.Token
import dev.tesserakt.sparql.compiler.lexer.Token.Companion.bindingName
import dev.tesserakt.sparql.compiler.lexer.Token.Companion.literalNumericValue
import dev.tesserakt.sparql.compiler.lexer.Token.Companion.literalTextValue
import dev.tesserakt.sparql.types.Expression

/**
 * Processes structures like `MIN(?s)`, `AVG(?s) - MIN(?p)`, `COUNT(DISTINCT ?s)`
 */
class ExpressionProcessor: Analyser<Expression>() {

    override fun _process(): Expression {
        return processStatement()
    }

    // processes & consumes structures like `max(?s) - avg(?p)`, ends when reaching an
    //  unexpected token, such as a `)` not part of this statement, or reaching a lower bound threshold for
    //  the operator precedence
    private fun processStatement(): Expression {
        var result: Expression.Calculation = run {
            val lhs = nextOperand()
            val operator = token.operator
            if (operator == null) {
                return lhs
            }
            // continuing with our existing expression
            consume()
            val rhs = nextOperand()
            Expression.Calculation(lhs = lhs, rhs = rhs, operator = operator)
        }
        while (true) {
            val operator = token.operator ?: return result
            consume()
            val previousPrecedence = result.operator.precedence
            val currentPrecedence = operator.precedence
            when {
                previousPrecedence > currentPrecedence -> {
                    // regular chaining applies
                    result = Expression.Calculation(lhs = result, rhs = nextOperand(), operator = operator)
                }
                previousPrecedence < currentPrecedence -> {
                    // the last statement has to be replaced, as the new operation is the top one
                    result = Expression.Calculation(lhs = result.lhs, rhs = Expression.Calculation(lhs = result.rhs, rhs = nextOperand(), operator = operator), operator = result.operator)
                }
                else /* existingPrecedence = operator.precedence */ -> {
                    // regular chaining applies
                    result = Expression.Calculation(lhs = result, rhs = nextOperand(), operator = operator)
                }
            }
        }
    }

    private fun nextOperand(): Expression = when (val token = token) {
        is Token.Binding -> {
            Expression.BindingValues(token.bindingName)
                .also { consume() }
        }
        is Token.Uri -> {
            Expression.UriValue(Quad.NamedTerm(token.value))
                .also { consume() }
        }
        is Token.PrefixedTerm -> {
            Expression.UriValue(token.resolve())
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
            Expression.Negative.of(nextOperand())
        }
        is Token.NumericLiteral -> {
            Expression.NumericLiteralValue(token.literalNumericValue)
                .also { consume() }
        }
        is Token.StringLiteral -> {
            Expression.StringLiteralValue(token.literalTextValue)
                .also { consume() }
        }
        is Token.TypedLiteral -> {
            val datatype = when (token.datatype) {
                is Token.Uri -> Quad.NamedTerm(token.datatype.value)
                is Token.PrefixedTerm -> token.datatype.resolve()

                is Token.Binding,
                is Token.BlankTerm,
                is Token.StringLiteral,
                is Token.NumericLiteral,
                is Token.TypedLiteral -> bail("Invalid datatype: ${token.datatype}")
            }
            when (datatype) {
                XSD.dateTime, XSD.date -> {
                    Expression.DateLiteralValue(
                        Quad.Literal(
                            value = token.value,
                            type = datatype,
                        )
                    )
                }
                else -> {
                    throw IllegalArgumentException("Unknown literal type in expression: `${token.datatype}`")
                }
            }.also {
                consume()
            }
        }
        Token.Keyword.True -> {
            Expression.BooleanLiteralValue(true)
                .also { consume() }
        }
        Token.Keyword.False -> {
            Expression.BooleanLiteralValue(false)
                .also { consume() }
        }
        Token.Keyword.StringLength,
        Token.Keyword.Concat -> {
            processFuncCall()
        }
        else -> {
            expectedBindingOrLiteralOrToken(
                Token.Keyword.StringLength,
                Token.Keyword.Concat,
                Token.Keyword.AggCount,
                Token.Keyword.AggMin,
                Token.Keyword.AggMax,
                Token.Keyword.AggAvg,
                Token.Symbol.RoundBracketStart
            )
        }
    }

    private val Token.operator: Expression.Calculation.Operator?
        get() = when (this) {
            // math symbols
            Token.Symbol.OpPlus -> Expression.Calculation.Operator.SUM
            Token.Symbol.OpMinus -> Expression.Calculation.Operator.SUB
            Token.Symbol.ForwardSlash -> Expression.Calculation.Operator.DIV
            Token.Symbol.Asterisk -> Expression.Calculation.Operator.MUL
            // comparison symbols
            Token.Symbol.AngularBracketStart -> Expression.Calculation.Operator.CMP_LT
            Token.Symbol.LTEQ -> Expression.Calculation.Operator.CMP_LE
            Token.Symbol.Equals -> Expression.Calculation.Operator.CMP_EQ
            Token.Symbol.NotEquals -> Expression.Calculation.Operator.CMP_NEQ
            Token.Symbol.GTEQ -> Expression.Calculation.Operator.CMP_GE
            Token.Symbol.AngularBracketEnd -> Expression.Calculation.Operator.CMP_GT
            // chaining symbols
            Token.Symbol.ExprAnd -> Expression.Calculation.Operator.AND
            Token.Symbol.ExprOr -> Expression.Calculation.Operator.OR
            else -> null
        }

    private val Expression.Calculation.Operator.precedence: Int
        get() = when (this) {
            Expression.Calculation.Operator.SUM -> 3
            Expression.Calculation.Operator.SUB -> 3

            Expression.Calculation.Operator.MUL -> 4
            Expression.Calculation.Operator.DIV -> 4

            Expression.Calculation.Operator.AND -> 1
            Expression.Calculation.Operator.OR -> 0

            Expression.Calculation.Operator.CMP_LT -> 2
            Expression.Calculation.Operator.CMP_LE -> 2
            Expression.Calculation.Operator.CMP_EQ -> 2
            Expression.Calculation.Operator.CMP_NEQ -> 2
            Expression.Calculation.Operator.CMP_GE -> 2
            Expression.Calculation.Operator.CMP_GT -> 2
        }

    // processes & consumes structures like `(max(?s) - avg(?p))`
    private fun processAggregationGroup(): Expression {
        // simply calling the `processAggregationStatement` again, level of recursion depth = number of parentheses
        // consuming the '('
        expectToken(Token.Symbol.RoundBracketStart)
        consume()
        return processStatement().also {
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
    private fun processFuncCall(): Expression.FuncCall {
        val name = token.syntax
        consume()
        expectToken(Token.Symbol.RoundBracketStart)
        consume()
        if (token == Token.Symbol.RoundBracketEnd) {
            return Expression.FuncCall(name = name, args = emptyList())
        }
        val args = mutableListOf<Expression>()
        while (true) {
            args += nextOperand()
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