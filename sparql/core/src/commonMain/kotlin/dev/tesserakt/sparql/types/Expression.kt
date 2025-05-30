package dev.tesserakt.sparql.types

import kotlin.jvm.JvmInline

sealed interface Expression : QueryAtom {

    // TODO: these require grouping for non-aggregated bindings in the select statement (see compile test case `GROUP BY ?g`)
    data class BindingAggregate(
        val type: Type,
        val input: BindingValues,
        val distinct: Boolean
    ) : Expression {

        enum class Type {
            COUNT,
            SUM,
            MIN,
            MAX,
            AVG,
            GROUP_CONCAT,
            SAMPLE
        }

        override fun toString() = "${type.name}($input)"

    }

    data class FuncCall(
        val name: String,
        val args: List<Expression>
    ) : Expression {

        override fun toString() = "$name(${args.joinToString()})"

    }

    /* = - value */
    @JvmInline
    value class Negative(val value: Expression) : Expression {
        override fun toString() = "(- $value)"

        companion object {
            fun of(value: Expression) = if (value is Negative) value.value else Negative(value)
        }

    }

    data class MathOp(
        val lhs: Expression,
        val rhs: Expression,
        val operator: Operator,
    ) : Expression {

        enum class Operator(val sign: Char) {
            SUM('+'), MUL('⨉'), SUB('-'), DIV('÷')
        }

        override fun toString(): String {
            return "($lhs ${operator.sign} $rhs)"
        }

        /**
         * Builder for grouping numerical operators read in LTR and fixes the ordering to respect order of operations
         */
        class Builder(
            // the first term of the statement
            start: Expression
        ) {

            // only + & * are stored here, see `add()`
            private val operators = mutableListOf<Operator>()
            private val operands = mutableListOf(start)

            fun add(operator: Operator, operand: Expression) {
                operators.add(operator)
                operands.add(operand)
            }

            /**
             * Builds an aggregated version of the statement. IMPORTANT: this is a destructive operation, leaving the
             *  `Builder` instance in a not-so-usable state (the result becomes the first operand for the next usage)
             */
            fun build(): Expression {
                // first grouping all multiplication statements
                if (operands.size == 1) {
                    return operands.single()
                }
                var order = operators.maxOf { it.order }
                while (order >= 1) {
                    var i = 0
                    while (i < operators.size) {
                        if (operators[i].order == order) {
                            fuse(i)
                        } else {
                            ++i
                        }
                    }
                    --order
                }
                return operands.single()
            }

            private inline fun fuse(index: Int) {
                val operand1 = operands[index]
                val operand2 = operands[index + 1]
                // the operator is being consumed, so only using it to create the expression
                val new = operators.removeAt(index).create(operand1, operand2)
                // removing one and setting the swapping the other one
                operands.removeAt(index)
                operands[index] = new
            }

        }

    }

    data class Comparison(
        val lhs: Expression,
        val rhs: Expression,
        val operator: Operator
    ) : Expression {
        enum class Operator {
            GREATER_THAN, GREATER_THAN_OR_EQ, LESS_THAN, LESS_THAN_OR_EQ, EQUAL, NOT_EQUAL
        }
    }

    /**
     * BIND(`expression` AS ?`target`)
     */
    // not an ExpressionAST subtype as this cannot be directly used in other expressions
    data class BindingStatement(
        val expression: Expression,
        val target: String
    )

    @JvmInline
    value class BindingValues(val name: String) : Expression {
        override fun toString() = "?$name"
    }

    @JvmInline
    value class NumericLiteralValue(val value: Number) : Expression {
        override fun toString() = value.toString()
    }

    @JvmInline
    value class BooleanLiteralValue(val value: Boolean) : Expression {
        override fun toString() = value.toString()
    }

    @JvmInline
    value class StringLiteralValue(val value: String) : Expression {
        override fun toString() = value
    }

}

/* helpers */

private val Expression.MathOp.Operator.order: Int get() = when (this) {
    Expression.MathOp.Operator.SUM -> 1
    Expression.MathOp.Operator.SUB -> 1
    Expression.MathOp.Operator.MUL -> 2
    Expression.MathOp.Operator.DIV -> 2
}

private fun Expression.MathOp.Operator.create(lhs: Expression, rhs: Expression): Expression.MathOp =
    Expression.MathOp(lhs, rhs, this)
