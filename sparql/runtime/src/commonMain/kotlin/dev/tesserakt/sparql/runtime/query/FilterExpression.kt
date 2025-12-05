package dev.tesserakt.sparql.runtime.query

import dev.tesserakt.rdf.ontology.XSD
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.Quad.Companion.asLiteralTerm
import dev.tesserakt.sparql.runtime.evaluation.BindingIdentifier
import dev.tesserakt.sparql.runtime.evaluation.TermIdentifier
import dev.tesserakt.sparql.runtime.evaluation.TermIdentifier.Companion.get
import dev.tesserakt.sparql.runtime.evaluation.context.QueryContext
import dev.tesserakt.sparql.runtime.evaluation.mapping.Mapping
import dev.tesserakt.sparql.runtime.query.FilterExpression.MathOpEval.*
import dev.tesserakt.sparql.types.DateTime
import dev.tesserakt.sparql.types.Expression
import dev.tesserakt.sparql.types.Expression.*
import kotlin.jvm.JvmInline

class FilterExpression(val context: QueryContext, expr: Expression) {

    sealed interface OperationValue {
        object Unbound: OperationValue {
            override fun equals(other: Any?) = false
        }
        @JvmInline
        value class SingleValue(val term: Quad.Element) : OperationValue
        @JvmInline
        value class DateValue(val value: DateTime) : OperationValue
        @JvmInline
        value class SingleValueIdentifier(val term: TermIdentifier) : OperationValue
        @JvmInline
        value class SingleMapping(val mapping: Mapping) : OperationValue
    }

    fun interface Operation {

        fun eval(input: OperationValue): OperationValue

        companion object {
            fun from(context: QueryContext, expr: Expression): Operation {
                return when (expr) {
                    is BindingAggregate -> TODO()
                    is BindingValues ->
                        ValueLookUpOperation(binding = BindingIdentifier(context, name = expr.name))

                    is UriValue ->
                        Operation { OperationValue.SingleValue(term = expr.uri) }

                    is Calculation -> when (expr.operator) {
                        Calculation.Operator.SUM -> Sum(context = context, lhs = from(context, expr.lhs), rhs = from(context, expr.rhs))
                        Calculation.Operator.SUB -> Sub(context = context, lhs = from(context, expr.lhs), rhs = from(context, expr.rhs))
                        Calculation.Operator.MUL -> Mul(context = context, lhs = from(context, expr.lhs), rhs = from(context, expr.rhs))
                        Calculation.Operator.DIV -> Div(context = context, lhs = from(context, expr.lhs), rhs = from(context, expr.rhs))
                        Calculation.Operator.AND -> AndEval(from(context = context, expr = expr.lhs), from(context, expr.rhs))
                        Calculation.Operator.OR -> OrEval(from(context = context, expr = expr.lhs), from(context, expr.rhs))
                        Calculation.Operator.CMP_LT -> ComparisonEval.LT(context = context, left = from(context, expr.lhs), right = from(context, expr.rhs))
                        Calculation.Operator.CMP_LE -> ComparisonEval.LTEQ(context = context, left = from(context, expr.lhs), right = from(context, expr.rhs))
                        Calculation.Operator.CMP_EQ -> ComparisonEval.EQ(context = context, left = from(context, expr.lhs), right = from(context, expr.rhs))
                        Calculation.Operator.CMP_NEQ -> ComparisonEval.NEQ(context = context, left = from(context, expr.lhs), right = from(context, expr.rhs))
                        Calculation.Operator.CMP_GE -> ComparisonEval.GTEQ(context = context, left = from(context, expr.lhs), right = from(context, expr.rhs))
                        Calculation.Operator.CMP_GT -> ComparisonEval.GT(context = context, left = from(context, expr.lhs), right = from(context, expr.rhs))
                    }

                    is FuncCall -> BuiltinFunction.from(context, expr)
                    is Negative -> TODO()
                    is NumericLiteralValue -> ConstantValueOperation(expr.value.asLiteralTerm().into())
                    is DateLiteralValue -> ConstantValueOperation(expr.timestamp.into())
                    is BooleanLiteralValue -> ConstantValueOperation(expr.value.asLiteralTerm().into())
                    is StringLiteralValue -> ConstantValueOperation(expr.value.asLiteralTerm().into())
                }
            }
        }

    }

    sealed interface ComparisonEval : Operation {

        class EQ(val context: QueryContext, private val left: Operation, private val right: Operation) : ComparisonEval {

            override fun eval(input: OperationValue): OperationValue {
                val comparison = compare(context, left.eval(input), right.eval(input)) ?: return false.asLiteralTerm().into()
                return (comparison == 0).asLiteralTerm().into()
            }

        }

        class NEQ(val context: QueryContext, private val left: Operation, private val right: Operation) : ComparisonEval {

            override fun eval(input: OperationValue): OperationValue {
                val comparison = compare(context, left.eval(input), right.eval(input)) ?: return false.asLiteralTerm().into()
                return (comparison != 0).asLiteralTerm().into()
            }

        }

        class LT(val context: QueryContext, private val left: Operation, private val right: Operation) : ComparisonEval {

            override fun eval(input: OperationValue): OperationValue {
                val comparison = compare(context, left.eval(input), right.eval(input)) ?: return false.asLiteralTerm().into()
                return (comparison < 0).asLiteralTerm().into()
            }
        }


        class GT(val context: QueryContext, private val left: Operation, private val right: Operation) : ComparisonEval {

            override fun eval(input: OperationValue): OperationValue {
                val comparison = compare(context, left.eval(input), right.eval(input)) ?: return false.asLiteralTerm().into()
                return (comparison > 0).asLiteralTerm().into()
            }
        }

        class LTEQ(val context: QueryContext, private val left: Operation, private val right: Operation) : ComparisonEval {

            override fun eval(input: OperationValue): OperationValue {
                val comparison = compare(context, left.eval(input), right.eval(input)) ?: return false.asLiteralTerm().into()
                return (comparison <= 0).asLiteralTerm().into()
            }

        }

        class GTEQ(val context: QueryContext, private val left: Operation, private val right: Operation) : ComparisonEval {

            override fun eval(input: OperationValue): OperationValue {
                val comparison = compare(context, left.eval(input), right.eval(input)) ?: return false.asLiteralTerm().into()
                return (comparison >= 0).asLiteralTerm().into()
            }

        }

        companion object {

            /**
             * A generic comparison evaluator, capable of interpreting combinations of literals and date time
             *  representations. Returns the integer value of a `compare` evaluation between [left] and [right]
             *  (i.e. `left.compareTo(right)`), or `null` if the context did not yield any results or the combination of
             *  types is invalid
             */
            private fun compare(context: QueryContext, left: OperationValue, right: OperationValue): Int? {
                return when {
                    // this variant could've been optimised as it's evaluation will always yield the same result
                    left is OperationValue.DateValue && right is OperationValue.DateValue -> {
                        left.value.compareTo(right.value)
                    }
                    // one of the branches is a constant, the other is data-dependant
                    left is OperationValue.DateValue -> {
                        // assuming `right` produces a literal that can be interpreted as a date time
                        val r = right.getTerm(context) ?: return null
                        if (r !is Quad.TypedLiteral || !r.isDateTimeValue()) {
                            return null
                        }
                        // TODO: check why this one doesn't seem to work
                        left.value.compareTo(DateTime.parseOrNull(r.value) ?: return null)
                    }
                    right is OperationValue.DateValue -> {
                        // simply reversing the result so we end up in the branch above
                        compare(context, right, left)?.let { -it }
                    }
                    // assuming they're valid terms (now or after mapping)
                    else -> {
                        try {
                            val a = left.getTerm(context) ?: return null
                            val b = right.getTerm(context) ?: return null
                            // bailing out early if EQ
                            if (a == b) {
                                return 0
                            }
                            // we can't compare when one of them is not a literal
                            if (a !is Quad.TypedLiteral || b !is Quad.TypedLiteral) {
                                return 1
                            }
                            return compare(a.typedLiteral, b.typedLiteral)
                        } catch (_: UnsupportedOperationException) {
                            // incompatible types
                            null
                        }
                    }
                }
            }

            /**
             * Compares [left] value with the specified value for order. Returns zero if [left] value is equal to the
             *  specified [right] value, a negative number if it's less than [right], or a positive number if it's
             *  greater than [right].
             */
            private fun compare(left: Quad.TypedLiteral, right: Quad.TypedLiteral): Int {
                return when {
                    left.isNumericalValue() && right.isNumericalValue() ->
                        left.numericalValue.compareTo(right.numericalValue)

                    left.isDateTimeValue() && right.isDateTimeValue() ->
                        DateTime.parse(left.value).compareTo(DateTime.parse(right.value))

                    else ->
                        throw UnsupportedOperationException("Cannot compare literals with types ${left.type} and ${right.type}")
                }
            }

        }

    }

    sealed class MathOpEval(
        val context: QueryContext,
        val lhs: Operation,
        val rhs: Operation,
    ): Operation {

        final override fun eval(input: OperationValue): OperationValue {
            val left = lhs.eval(input).getTerm(context)?.typedLiteral?.numericalValue ?: return OperationValue.Unbound
            val right = rhs.eval(input).getTerm(context)?.typedLiteral?.numericalValue ?: return OperationValue.Unbound
            return eval(left, right).asLiteralTerm().into()
        }

        abstract fun eval(lhs: Double, rhs: Double): Double

        class Sum(context: QueryContext, lhs: Operation, rhs: Operation) : MathOpEval(context, lhs, rhs) {

            override fun eval(lhs: Double, rhs: Double): Double {
                return lhs + rhs
            }

        }

        class Sub(context: QueryContext, lhs: Operation, rhs: Operation) : MathOpEval(context, lhs, rhs) {

            override fun eval(lhs: Double, rhs: Double): Double {
                return lhs - rhs
            }

        }

        class Mul(context: QueryContext, lhs: Operation, rhs: Operation) : MathOpEval(context, lhs, rhs) {

            override fun eval(lhs: Double, rhs: Double): Double {
                return lhs * rhs
            }

        }

        class Div(context: QueryContext, lhs: Operation, rhs: Operation) : MathOpEval(context, lhs, rhs) {

            override fun eval(lhs: Double, rhs: Double): Double {
                return lhs / rhs
            }

        }

    }

    class AndEval(val lhs: Operation, val rhs: Operation) : Operation {
        override fun eval(input: OperationValue): OperationValue {
            return (lhs.eval(input).isTrue() && rhs.eval(input).isTrue()).asLiteralTerm().into()
        }
    }

    class OrEval(val lhs: Operation, val rhs: Operation) : Operation {
        override fun eval(input: OperationValue): OperationValue {
            return (lhs.eval(input).isTrue() || rhs.eval(input).isTrue()).asLiteralTerm().into()
        }
    }

    @JvmInline
    private value class ValueLookUpOperation(private val binding: BindingIdentifier) : Operation {
        override fun eval(input: OperationValue): OperationValue {
            return input.mapping.get(binding).into()
        }
    }

    @JvmInline
    private value class ConstantValueOperation<V: OperationValue>(private val constant: V) : Operation {

        override fun eval(input: OperationValue): OperationValue {
            return constant
        }
    }

    private class BooleanCoercionOperation(val context: QueryContext, private val parent: Operation) : Operation {

        override fun eval(input: OperationValue): OperationValue {
            val result = parent.eval(input).getTerm(context)
            return when {
                result !is Quad.TypedLiteral -> {
                    throw IllegalStateException("Unexpected non-literal `$result` received!")
                }

                result.type == XSD.boolean -> {
                    result.into()
                }

                else -> {
                    throw IllegalStateException("Unexpected literal type `${result.type}`!")
                }
            }
        }
    }

    private val root = BooleanCoercionOperation(context, parent = Operation.from(context, expr))

    fun test(mapping: Mapping): Boolean {
        return root.eval(mapping.into()).getTerm(context) == true.asLiteralTerm()
    }

    companion object {

        private fun Quad.Element.into() = OperationValue.SingleValue(this)

        private fun DateTime.into() = OperationValue.DateValue(this)

        private fun Quad.Element?.into() = this?.let { OperationValue.SingleValue(this) } ?: OperationValue.Unbound

        private fun TermIdentifier?.into() = this?.let { OperationValue.SingleValueIdentifier(this) } ?: OperationValue.Unbound

        private fun Mapping.into() = OperationValue.SingleMapping(this)

        private fun OperationValue.getTerm(context: QueryContext): Quad.Element? = when (this) {
            is OperationValue.SingleValue -> term
            is OperationValue.SingleValueIdentifier -> context.get(term)
            // `is`, as `equals` is always false!
            is OperationValue.Unbound -> null
            else -> throw IllegalStateException("Single term value expected, but received a `${this::class.simpleName}` instead!")
        }

        private val OperationValue.mapping: Mapping
            get() = (this as? OperationValue.SingleMapping)?.mapping
                ?: throw IllegalStateException("Single mapping value expected, but received a `${this::class.simpleName}` instead!")

        private val Quad.Element.typedLiteral
            get() = (this as? Quad.TypedLiteral)
                ?: throw IllegalStateException("Literal term value expected, but received $this instead!")

    }

    object BuiltinFunction {

        fun LANG(context: QueryContext, arg: Operation) = Operation {
            val term = arg.evalToSingleQuadElementOrNull(context, it)
            if (term !is Quad.LangString) {
                return@Operation OperationValue.Unbound
            }
            term.language.asLiteralTerm().into()
        }

        fun LANGMATCHES(context: QueryContext, tag: Operation, range: Operation) = Operation {
            val tagValue = tag.evalToSingleQuadElementOrNull(context, it)
            // simple literal expected, as we're doing string matching
            if (tagValue !is Quad.SimpleLiteral) {
                return@Operation OperationValue.Unbound
            }
            val tag = tagValue.value
            // same goes for the tag range
            val tagRange = range.evalToSingleQuadElementOrNull(context, it)
            // simple literal expected, as we're doing string matching
            if (tagRange !is Quad.SimpleLiteral || tagRange.type != XSD.string) {
                return@Operation OperationValue.Unbound
            }
            val range = tagRange.value
            // now regular matching can be applied
            // special case first, where "*" matches all (non-empty!) language tags
            return@Operation if (range == "*") {
                 tag.isNotEmpty().asLiteralTerm().into()
            } else {
                val currentLang = tag.substringBefore('-')
                currentLang.contentEquals(range, ignoreCase = true).asLiteralTerm().into()
            }
        }

        fun DATETIME(context: QueryContext, param: Operation) = Operation {
            val termValue = param.evalToSingleQuadElementOrNull(context, it)
            if (termValue !is Quad.TypedLiteral || termValue.type != XSD.dateTime) {
                return@Operation OperationValue.Unbound
            }
            OperationValue.DateValue(DateTime.parse(termValue.value))
        }

        fun from(context: QueryContext, call: FuncCall): Operation {
            fun matches(name: String) = call.name.contentEquals(name, ignoreCase = true)
            return when {
                matches("lang") -> {
                    check(call.args.size == 1)
                    LANG(context, Operation.from(context, call.args.single()))
                }
                matches("langmatches") -> {
                    check(call.args.size == 2)
                    LANGMATCHES(context, Operation.from(context, call.args[0]), Operation.from(context, call.args[1]))
                }
                matches(XSD.dateTime.value) -> {
                    check(call.args.size == 1)
                    DATETIME(context, Operation.from(context, call.args[0]))
                }
                else -> throw IllegalArgumentException("Unknown function identifier: `${call.name}`")
            }
        }

        private fun Operation.evalToSingleQuadElementOrNull(context: QueryContext, input: OperationValue): Quad.Element? =
            when (val value = eval(input)) {
                is OperationValue.SingleValueIdentifier -> {
                    context.get(value.term)
                }
                is OperationValue.SingleValue -> {
                    value.term
                }

                // invalid argument types to obtain a quad element, so yielding null
                is OperationValue.DateValue,
                is OperationValue.SingleMapping,
                OperationValue.Unbound -> null
            }

    }

}

private val numerals = setOf(XSD.long, XSD.int, XSD.double, XSD.float, XSD.integer)

private fun Quad.TypedLiteral.isNumericalValue(): Boolean {
    return type in numerals
}

private fun Quad.TypedLiteral.isDateTimeValue(): Boolean {
    return type == XSD.dateTime
}

private val Quad.TypedLiteral.numericalValue: Double
    get() = this.value.toDouble()

private fun FilterExpression.OperationValue.isTrue(): Boolean = when (this) {
    is FilterExpression.OperationValue.SingleMapping -> false
    is FilterExpression.OperationValue.SingleValue -> term == true.asLiteralTerm()
    is FilterExpression.OperationValue.SingleValueIdentifier -> false
    is FilterExpression.OperationValue.DateValue -> false
    FilterExpression.OperationValue.Unbound -> false
}

private fun DateTime.Companion.parseOrNull(str: String): DateTime? =
    runCatching { DateTime.parse(str) }.getOrNull()
