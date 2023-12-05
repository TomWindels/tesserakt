package core.sparql.compiler.types

import kotlin.jvm.JvmInline

data class Aggregation(
    val root: Aggregator,
    val output: Token.Binding
) {

    sealed interface Aggregator

    // TODO: these require grouping for non-aggregated bindings in the select statement (see compile test case `GROUP BY ?g`)
    data class Builtin(
        val type: Type,
        val input: Aggregator
    ): Aggregator {
        enum class Type {
           MIN,
           MAX,
           COUNT,
           AVG,
        }

        override fun toString() = "${type.name}($input)"
    }

    class MathOperation private constructor(
        val operands: List<Aggregator>,
        val operator: Operator,
    ): Aggregator {
        enum class Operator(
            internal val order: Int,
            internal val symbol: String
        ) {
           SUM(0, "+"),
           PRODUCT(1, "*"),
           DIFFERENCE(0, "-"),
           DIVISION(1, "/"),
        }

        // a constructor capable of flattening the input
        // `private` as this assumes the same level of "depth" in the statement (no grouping) for its flattening
        //  logic
        private constructor(
            vararg operands: Aggregator,
            operator: Operator
        ): this(
            operands = operands.flatMap { if (it is MathOperation && it.operator == operator) it.operands else listOf(it) },
            operator = operator
        )

        override fun toString() = operands.joinToString(prefix = "(", postfix = ")", separator = " ${operator.symbol} ")

        /**
         * Builder for grouping numerical operators read in LTR and fixes the ordering to respect order of operations
         */
        class Builder(
            // the first term of the statement
            start: Aggregator
        ) {

            private val operators = mutableListOf<Operator>()
            private val operands = mutableListOf(start)

            fun add(operator: Operator, aggregator: Aggregator) {
                operators.add(operator)
                operands.add(aggregator)
            }

            /**
             * Builds an aggregated version of the statement. IMPORTANT: this is a destructive operation, leaving the
             *  `Builder` instance in a not-so-usable state (the result becomes the first operand for the next usage)
             */
            fun build(): Aggregator {
                // finding the highest priority operators first, and creating their statements
                // e.g. a + b / c - d * e * f
                //            ^       ^   ^
                var order = 1
                while (order >= 0) {
                    var i = 0
                    while (i < operators.size) {
                        if (operators[i].order == order) {
                            fuse(i) { first, second, operator -> MathOperation(first, second, operator = operator) }
                        } else {
                            ++i
                        }
                    }
                    --order
                }
                return operands.first()
            }

            private inline fun fuse(
                index: Int,
                action: (first: Aggregator, second: Aggregator, operator: Operator) -> Aggregator
            ) {
                val new = action(operands[index], operands[index + 1], operators[index])
                // the operator has been consumed, so nuking that one
                operators.removeAt(index)
                // removing one and setting the swapping the other one
                operands.removeAt(index)
                operands[index] = new
            }

        }

    }

    @JvmInline
    value class BindingValues(val element: Token.Binding): Aggregator {
        override fun toString() = "?${element.name}"
    }

    @JvmInline
    value class DistinctBindingValues(val element: Token.Binding): Aggregator {
        override fun toString() = "DISTINCT ?${element.name}"
    }

    @JvmInline
    value class LiteralValue(val literal: Token.NumericLiteral): Aggregator {
        override fun toString() = literal.syntax
    }

}
