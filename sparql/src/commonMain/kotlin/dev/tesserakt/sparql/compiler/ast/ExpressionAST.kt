package dev.tesserakt.sparql.compiler.ast

import kotlin.jvm.JvmInline
import kotlin.math.absoluteValue


sealed interface ExpressionAST : ASTNode {

    // TODO: these require grouping for non-aggregated bindings in the select statement (see compile test case `GROUP BY ?g`)
    data class FuncCall(
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

    sealed class MathOp(val operands: List<ExpressionAST>) : ExpressionAST {

        enum class Operator {
            SUM, PRODUCT, DIFFERENCE, DIVISION
        }

        class Sum(operands: Iterable<ExpressionAST>) : MathOp(
            operands = operands
                .flatMap { if (it is Sum) it.operands else listOf(it.stabilised()) }
                .stabilised(0) { first, second -> first.toDouble() + second.toDouble() }
                .sortedBy { it.sortValue }
        ) {
            override fun toString() = operands.joinToString(prefix = "(", postfix = ")", separator = " + ")
        }

        class Multiplication(operands: Iterable<ExpressionAST>) : MathOp(
            operands = operands
                .flatMap { if (it is Multiplication) it.operands else listOf(it.stabilised()) }
                .stabilised(1) { first, second -> first.toDouble() * second.toDouble() }
                .sortedBy { it.sortValue }
        ) {
            override fun toString() = operands.joinToString(prefix = "(", postfix = ")", separator = " * ")
        }

        override fun equals(other: Any?) =
            other != null &&
            other is MathOp &&
            this::class.simpleName!! == other::class.simpleName!! &&
            operands.size == other.operands.size &&
            operands.containsAll(other.operands)

        /* = - value */
        @JvmInline
        value class Negative(val value: ExpressionAST) : ExpressionAST {
            override fun toString() = "- $value"

            companion object {
                fun of(value: ExpressionAST) = if (value is Negative) value.value else Negative(value)
            }

        }

        /* = 1 / value */
        @JvmInline
        value class Inverse(val value: ExpressionAST) : ExpressionAST {
            override fun toString() = "1 / $value"

            companion object {
                fun of(value: ExpressionAST) = if (value is Inverse) value.value else Inverse(value)
            }

        }

        override fun hashCode(): Int = this::class.simpleName!!.hashCode() + operands.hashCode()

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
                when (operator) {
                    Operator.DIFFERENCE -> {
                        operators.add(Operator.SUM)
                        operands.add(Negative.of(operand))
                    }

                    Operator.DIVISION -> {
                        operators.add(Operator.PRODUCT)
                        operands.add(Inverse.of(operand))
                    }

                    Operator.SUM, Operator.PRODUCT -> {
                        operators.add(operator)
                        operands.add(operand)
                    }
                }
            }

            /**
             * Builds an aggregated version of the statement. IMPORTANT: this is a destructive operation, leaving the
             *  `Builder` instance in a not-so-usable state (the result becomes the first operand for the next usage)
             */
            fun build(): ExpressionAST {
                // first grouping all multiplication statements
                var i = 0
                while (i < operators.size) {
                    if (operators[i] == Operator.PRODUCT) {
                        fuse(i) { first, second -> Multiplication(listOf(first, second)) }
                    } else {
                        ++i
                    }
                }
                // followed by a single sum statement if necessary
                return if (operands.size > 1) {
                    Sum(operands)
                } else {
                    operands.first().stabilised()
                }
            }

            private inline fun fuse(
                index: Int,
                action: (first: ExpressionAST, second: ExpressionAST) -> ExpressionAST
            ) {
                val operand1: ExpressionAST
                val operand2: ExpressionAST
                val op1 = operands[index]
                val op2 = operands[index + 1]
                if (op1.sortValue > op2.sortValue) {
                    operand1 = op1
                    operand2 = op2
                } else {
                    operand2 = op1
                    operand1 = op2
                }
                val new = action(operand1, operand2)
                // the operator has been consumed, so nuking that one
                operators.removeAt(index)
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
    ): ExpressionAST {
        enum class Operand {
            GREATER_THAN, GREATER_THAN_OR_EQ, LESS_THAN, LESS_THAN_OR_EQ, EQUAL
        }
    }

    @JvmInline
    value class BindingValues(val name: String) : ExpressionAST {
        override fun toString() = "?$name"
    }

    @JvmInline
    value class LiteralValue(val value: Number) : ExpressionAST {
        override fun toString() = value.toString()
    }
}

/**
 * A helper value used to stably sort operations
 */
@Suppress("RecursivePropertyAccessor")
private val ExpressionAST.sortValue: Int
    get() = when (this) {
        is ExpressionAST.BindingValues -> name.hashCode().absoluteValue % 100 + 1
        is ExpressionAST.LiteralValue -> value.hashCode().absoluteValue % 100 + 3
        is ExpressionAST.FuncCall -> input.sortValue * 100 + (type.ordinal + 1) * 10
        is ExpressionAST.Conditional -> lhs.sortValue * 10000 + rhs.sortValue * 100 + (operand.ordinal + 1) * 10
        is ExpressionAST.MathOp.Inverse -> -value.sortValue - 1
        is ExpressionAST.MathOp.Negative -> -value.sortValue - 2
        is ExpressionAST.MathOp.Sum -> operands
            .sumOf { it.sortValue } * 1000 + 100

        is ExpressionAST.MathOp.Multiplication -> operands
            .sumOf { it.sortValue } * 1000 + 200

    }

/**
 * Produces the constant value associated with this expression if possible, `null` otherwise
 */
@Suppress("RecursivePropertyAccessor")
private val ExpressionAST.stableValue: Number?
    get() = when (this) {
        is ExpressionAST.BindingValues -> null
        is ExpressionAST.LiteralValue -> value
        is ExpressionAST.FuncCall -> null // can only be applied to bindings, so no stable value
        is ExpressionAST.Conditional -> null
        is ExpressionAST.MathOp.Inverse -> value.stableValue?.let { 1 / it.toDouble() }
        is ExpressionAST.MathOp.Negative -> value.stableValue?.let { -it.toDouble() }
        is ExpressionAST.MathOp.Sum -> operands
            .sumOf { it.stableValue?.toDouble() ?: return null }

        is ExpressionAST.MathOp.Multiplication -> operands
            .fold(initial = 1.0) { value, element -> (element.stableValue?.toDouble() ?: return null) * value }

    }

/**
 * Attempts to replace `this` by a literal value constant, if possible
 */
private fun ExpressionAST.stabilised() = stableValue?.let { ExpressionAST.LiteralValue(it) } ?: this

/**
 * Stabilises the incoming aggregations, replacing all where possible with a `LiteralValue` as last element
 *  of the returned list. Aggregation of two elements is done using the provided `aggregator`
 */
private fun Iterable<ExpressionAST>.stabilised(
    neutral: Number,
    aggregator: (first: Number, second: Number) -> Number
): List<ExpressionAST> {
    var aggregated = neutral
    val remaining = mutableListOf<ExpressionAST>()
    forEach { element ->
        element.stableValue
            ?.let { stabilised -> aggregated = aggregator(aggregated, stabilised) }
            ?: run { remaining.add(element) }
    }
    if (neutral != aggregated) {
        remaining.add(ExpressionAST.LiteralValue(aggregated))
    }
    return remaining
}
