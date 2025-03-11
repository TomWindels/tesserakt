@file:Suppress("NOTHING_TO_INLINE")

package dev.tesserakt.sparql.types.ast

import kotlin.jvm.JvmInline


sealed interface ExpressionAST : ASTElement {

    // TODO: these require grouping for non-aggregated bindings in the select statement (see compile test case `GROUP BY ?g`)
    data class BindingAggregate(
        val type: Type,
        val input: BindingValues,
        val distinct: Boolean
    ) : ExpressionAST {

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
        val args: List<ExpressionAST>
    ) : ExpressionAST {

        override fun toString() = "$name(${args.joinToString()})"

    }

    sealed class MathOp : ExpressionAST {

        enum class Operator {
            SUM, MUL, DIFF, DIV
        }

        data class Sum(val lhs: ExpressionAST, val rhs: ExpressionAST) : MathOp() {
            override fun toString() = "($lhs + $rhs)"
        }

        data class Diff(val lhs: ExpressionAST, val rhs: ExpressionAST) : MathOp() {
            override fun toString() = "($lhs - $rhs)"
        }

        data class Mul(val lhs: ExpressionAST, val rhs: ExpressionAST) : MathOp() {
            override fun toString() = "($lhs * $rhs)"
        }

        data class Div(val lhs: ExpressionAST, val rhs: ExpressionAST) : MathOp() {
            override fun toString() = "($lhs / $rhs)"
        }

        /* = - value */
        @JvmInline
        value class Negative(val value: ExpressionAST) : ExpressionAST {
            override fun toString() = "(- $value)"

            companion object {
                fun of(value: ExpressionAST) = if (value is Negative) value.value else Negative(value)
            }

        }

        /**
         * Builder for grouping numerical operators read in LTR and fixes the ordering to respect order of operations
         */
        class Builder(
            // the first term of the statement
            start: ExpressionAST
        ) {

            // only + & * are stored here, see `add()`
            private val operators = mutableListOf<Operator>()
            private val operands = mutableListOf(start)

            fun add(operator: Operator, operand: ExpressionAST) {
                operators.add(operator)
                operands.add(operand)
            }

            /**
             * Builds an aggregated version of the statement. IMPORTANT: this is a destructive operation, leaving the
             *  `Builder` instance in a not-so-usable state (the result becomes the first operand for the next usage)
             */
            fun build(): ExpressionAST {
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

    data class Conditional(
        val lhs: ExpressionAST,
        val rhs: ExpressionAST,
        val operand: Operand
    ) : ExpressionAST {
        enum class Operand {
            GREATER_THAN, GREATER_THAN_OR_EQ, LESS_THAN, LESS_THAN_OR_EQ, EQUAL
        }
    }

    /**
     * BIND(`expression` AS ?`target`)
     */
    // not an ExpressionAST subtype as this cannot be directly used in other expressions
    data class BindingStatement(
        val expression: ExpressionAST,
        val target: String
    )

    @JvmInline
    value class BindingValues(val name: String) : ExpressionAST {
        override fun toString() = "?$name"
    }

    @JvmInline
    value class NumericLiteralValue(val value: Number) : ExpressionAST {
        override fun toString() = value.toString()
    }

    @JvmInline
    value class StringLiteralValue(val value: String) : ExpressionAST {
        override fun toString() = value
    }

}

/* helpers */

private val ExpressionAST.MathOp.Operator.order: Int get() = when (this) {
    ExpressionAST.MathOp.Operator.SUM -> 1
    ExpressionAST.MathOp.Operator.DIFF -> 1
    ExpressionAST.MathOp.Operator.MUL -> 2
    ExpressionAST.MathOp.Operator.DIV -> 2
}

private fun ExpressionAST.MathOp.Operator.create(lhs: ExpressionAST, rhs: ExpressionAST): ExpressionAST.MathOp = when (this) {
    ExpressionAST.MathOp.Operator.SUM -> ExpressionAST.MathOp.Sum(lhs, rhs)
    ExpressionAST.MathOp.Operator.DIFF -> ExpressionAST.MathOp.Diff(lhs, rhs)
    ExpressionAST.MathOp.Operator.MUL -> ExpressionAST.MathOp.Mul(lhs, rhs)
    ExpressionAST.MathOp.Operator.DIV -> ExpressionAST.MathOp.Div(lhs, rhs)
}
