package dev.tesserakt.sparql.runtime.query

import dev.tesserakt.rdf.ontology.XSD
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.runtime.evaluation.BindingIdentifier
import dev.tesserakt.sparql.runtime.evaluation.BindingIdentifierSet
import dev.tesserakt.sparql.runtime.evaluation.TermIdentifier
import dev.tesserakt.sparql.runtime.evaluation.TermIdentifier.Companion.get
import dev.tesserakt.sparql.runtime.evaluation.context.QueryContext
import dev.tesserakt.sparql.runtime.evaluation.mapping.Mapping
import dev.tesserakt.sparql.types.DateTime
import dev.tesserakt.sparql.types.Expression
import dev.tesserakt.sparql.types.Expression.*
import kotlin.jvm.JvmInline

object Expression {

    sealed interface OperationValue {

        object Unbound: OperationValue {
            override fun equals(other: Any?) = false
        }

        @JvmInline
        value class SingleValue(val term: Quad.Element) : OperationValue {

            override fun toString(): String {
                return term.toString()
            }

        }

        @JvmInline
        value class DateValue(val value: DateTime) : OperationValue {

            override fun toString(): String {
                return value.toString()
            }

        }

        @JvmInline
        value class SingleValueIdentifier(val term: TermIdentifier) : OperationValue {

            override fun toString(): String {
                return term.toString()
            }

        }

        @JvmInline
        value class SingleMapping(val mapping: Mapping) : OperationValue {

            override fun toString(): String {
                return mapping.toString()
            }

        }

    }

    interface Operation {

        fun bindings(): BindingIdentifierSet

        fun eval(input: OperationValue): OperationValue

        fun eval(mapping: Mapping): OperationValue {
            return eval(input = mapping.into())
        }

        companion object {
            fun from(context: QueryContext, expr: Expression): Operation {
                return when (expr) {
                    is BindingAggregate -> TODO()
                    is BindingValues ->
                        ValueLookUpOperation(binding = BindingIdentifier(context, name = expr.name))

                    is UriValue ->
                        object: Operation {

                            override fun bindings(): BindingIdentifierSet {
                                return BindingIdentifierSet.EMPTY
                            }

                            override fun eval(input: OperationValue): OperationValue {
                                return OperationValue.SingleValue(term = expr.uri)
                            }

                            override fun toString(): String {
                                return expr.uri.toString()
                            }

                        }

                    is Calculation -> when (expr.operator) {
                        Calculation.Operator.SUM -> MathOpEval.Sum(
                            context = context,
                            lhs = from(context, expr.lhs),
                            rhs = from(context, expr.rhs)
                        )
                        Calculation.Operator.SUB -> MathOpEval.Sub(
                            context = context,
                            lhs = from(context, expr.lhs),
                            rhs = from(context, expr.rhs)
                        )
                        Calculation.Operator.MUL -> MathOpEval.Mul(
                            context = context,
                            lhs = from(context, expr.lhs),
                            rhs = from(context, expr.rhs)
                        )
                        Calculation.Operator.DIV -> MathOpEval.Div(
                            context = context,
                            lhs = from(context, expr.lhs),
                            rhs = from(context, expr.rhs)
                        )
                        Calculation.Operator.AND -> AndEval(
                            from(context = context, expr = expr.lhs),
                            from(context, expr.rhs)
                        )
                        Calculation.Operator.OR -> OrEval(
                            lhs = from(context = context, expr = expr.lhs),
                            rhs = from(context, expr.rhs)
                        )
                        Calculation.Operator.CMP_LT -> ComparisonEval.LT(
                            context = context,
                            left = from(context, expr.lhs),
                            right = from(context, expr.rhs)
                        )
                        Calculation.Operator.CMP_LE -> ComparisonEval.LTEQ(
                            context = context,
                            left = from(context, expr.lhs),
                            right = from(context, expr.rhs)
                        )
                        Calculation.Operator.CMP_EQ -> ComparisonEval.EQ(
                            context = context,
                            left = from(context, expr.lhs),
                            right = from(context, expr.rhs)
                        )
                        Calculation.Operator.CMP_NEQ -> ComparisonEval.NEQ(
                            context = context,
                            left = from(context, expr.lhs),
                            right = from(context, expr.rhs)
                        )
                        Calculation.Operator.CMP_GE -> ComparisonEval.GTEQ(
                            context = context,
                            left = from(context, expr.lhs),
                            right = from(context, expr.rhs)
                        )
                        Calculation.Operator.CMP_GT -> ComparisonEval.GT(
                            context = context,
                            left = from(context, expr.lhs),
                            right = from(context, expr.rhs)
                        )
                    }

                    is FuncCall -> BuiltinFunction.from(context, expr)
                    is Negative -> TODO()
                    is NumericLiteralValue -> ConstantValueOperation(Quad.Literal(expr.value).into())
                    is DateLiteralValue -> ConstantValueOperation(expr.timestamp.into())
                    is BooleanLiteralValue -> ConstantValueOperation(Quad.Literal(expr.value).into())
                    is StringLiteralValue -> ConstantValueOperation(Quad.Literal(expr.value).into())
                }
            }
        }

    }

    sealed interface ComparisonEval : Operation {

        class EQ(val context: QueryContext, private val left: Operation, private val right: Operation) : ComparisonEval {

            override fun bindings(): BindingIdentifierSet {
                return left.bindings() + right.bindings()
            }

            override fun eval(input: OperationValue): OperationValue {
                val comparison = compare(context, left.eval(input), right.eval(input)) ?: return Quad.Literal(false)
                    .into()
                return Quad.Literal((comparison == 0)).into()
            }

            override fun toString(): String {
                return "($left) == ($right)"
            }

        }

        class NEQ(val context: QueryContext, private val left: Operation, private val right: Operation) : ComparisonEval {

            override fun bindings(): BindingIdentifierSet {
                return left.bindings() + right.bindings()
            }

            override fun eval(input: OperationValue): OperationValue {
                val comparison = compare(context, left.eval(input), right.eval(input)) ?: return Quad.Literal(false).into()
                return Quad.Literal((comparison != 0)).into()
            }

            override fun toString(): String {
                return "($left) != ($right)"
            }

        }

        class LT(val context: QueryContext, private val left: Operation, private val right: Operation) : ComparisonEval {

            override fun bindings(): BindingIdentifierSet {
                return left.bindings() + right.bindings()
            }

            override fun eval(input: OperationValue): OperationValue {
                val comparison = compare(context, left.eval(input), right.eval(input)) ?: return Quad.Literal(false).into()
                return Quad.Literal(comparison < 0).into()
            }

            override fun toString(): String {
                return "($left) < ($right)"
            }

        }


        class GT(val context: QueryContext, private val left: Operation, private val right: Operation) : ComparisonEval {

            override fun bindings(): BindingIdentifierSet {
                return left.bindings() + right.bindings()
            }

            override fun eval(input: OperationValue): OperationValue {
                val comparison = compare(context, left.eval(input), right.eval(input)) ?: return Quad.Literal(false).into()
                return Quad.Literal(comparison > 0).into()
            }

            override fun toString(): String {
                return "($left) > ($right)"
            }

        }

        class LTEQ(val context: QueryContext, private val left: Operation, private val right: Operation) : ComparisonEval {

            override fun bindings(): BindingIdentifierSet {
                return left.bindings() + right.bindings()
            }

            override fun eval(input: OperationValue): OperationValue {
                val comparison = compare(context, left.eval(input), right.eval(input)) ?: return Quad.Literal(false).into()
                return Quad.Literal(comparison <= 0).into()
            }

            override fun toString(): String {
                return "($left) <= ($right)"
            }

        }

        class GTEQ(val context: QueryContext, private val left: Operation, private val right: Operation) : ComparisonEval {

            override fun bindings(): BindingIdentifierSet {
                return left.bindings() + right.bindings()
            }

            override fun eval(input: OperationValue): OperationValue {
                val comparison = compare(context, left.eval(input), right.eval(input)) ?: return Quad.Literal(false).into()
                return Quad.Literal(comparison >= 0).into()
            }

            override fun toString(): String {
                return "($left) >= ($right)"
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
                val leftNumber = left.numericalValue
                val rightNumber = right.numericalValue
                if (leftNumber != null && rightNumber != null) {
                    return leftNumber.compareTo(rightNumber)
                }
                if (left.isDateTimeValue() && right.isDateTimeValue()) {
                    DateTime.parse(left.value).compareTo(DateTime.parse(right.value))
                }
                throw UnsupportedOperationException("Cannot compare literals with types ${left.type} and ${right.type}")
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
            return Quad.Literal(eval(left, right)).into()
        }

        final override fun bindings(): BindingIdentifierSet {
            return lhs.bindings() + rhs.bindings()
        }

        override fun toString(): String {
            val op = when (this) {
                is Div -> '/'
                is Mul -> '*'
                is Sub -> '-'
                is Sum -> '+'
            }
            return "($lhs) $op ($rhs)"
        }

        fun eval(lhs: Number, rhs: Number): Number {
            return when {
                lhs is Double || rhs is Double -> {
                    eval(lhs.toDouble(), rhs.toDouble())
                }
                lhs is Float || rhs is Float -> {
                    eval(lhs.toFloat(), rhs.toFloat())
                }
                lhs is Long || rhs is Long -> {
                    eval(lhs.toLong(), rhs.toLong())
                }
                else -> {
                    eval(lhs.toInt(), rhs.toInt())
                }
            }
        }

        abstract fun eval(lhs: Int, rhs: Int): Int

        abstract fun eval(lhs: Float, rhs: Float): Float

        abstract fun eval(lhs: Double, rhs: Double): Double

        abstract fun eval(lhs: Long, rhs: Long): Long

        class Sum(context: QueryContext, lhs: Operation, rhs: Operation) : MathOpEval(context, lhs, rhs) {

            override fun eval(lhs: Int, rhs: Int): Int {
                return lhs + rhs
            }

            override fun eval(lhs: Float, rhs: Float): Float {
                return lhs + rhs
            }

            override fun eval(lhs: Double, rhs: Double): Double {
                return lhs + rhs
            }

            override fun eval(lhs: Long, rhs: Long): Long {
                return lhs + rhs
            }

        }

        class Sub(context: QueryContext, lhs: Operation, rhs: Operation) : MathOpEval(context, lhs, rhs) {

            override fun eval(lhs: Int, rhs: Int): Int {
                return lhs - rhs
            }

            override fun eval(lhs: Float, rhs: Float): Float {
                return lhs - rhs
            }

            override fun eval(lhs: Double, rhs: Double): Double {
                return lhs - rhs
            }

            override fun eval(lhs: Long, rhs: Long): Long {
                return lhs - rhs
            }
        }

        class Mul(context: QueryContext, lhs: Operation, rhs: Operation) : MathOpEval(context, lhs, rhs) {

            override fun eval(lhs: Int, rhs: Int): Int {
                return lhs * rhs
            }

            override fun eval(lhs: Float, rhs: Float): Float {
                return lhs * rhs
            }

            override fun eval(lhs: Double, rhs: Double): Double {
                return lhs * rhs
            }

            override fun eval(lhs: Long, rhs: Long): Long {
                return lhs * rhs
            }

        }

        class Div(context: QueryContext, lhs: Operation, rhs: Operation) : MathOpEval(context, lhs, rhs) {

            override fun eval(lhs: Int, rhs: Int): Int {
                return lhs / rhs
            }

            override fun eval(lhs: Float, rhs: Float): Float {
                return lhs / rhs
            }

            override fun eval(lhs: Double, rhs: Double): Double {
                return lhs / rhs
            }

            override fun eval(lhs: Long, rhs: Long): Long {
                return lhs / rhs
            }

        }

    }

    class AndEval(val lhs: Operation, val rhs: Operation) : Operation {

        override fun bindings(): BindingIdentifierSet {
            return lhs.bindings() + rhs.bindings()
        }

        override fun eval(input: OperationValue): OperationValue {
            return Quad.Literal(lhs.eval(input).isTrue() && rhs.eval(input).isTrue()).into()
        }

        override fun toString(): String {
            return "($lhs) && ($rhs)"
        }

    }

    class OrEval(val lhs: Operation, val rhs: Operation) : Operation {

        override fun bindings(): BindingIdentifierSet {
            return lhs.bindings() + rhs.bindings()
        }

        override fun eval(input: OperationValue): OperationValue {
            return Quad.Literal(lhs.eval(input).isTrue() || rhs.eval(input).isTrue()).into()
        }

        override fun toString(): String {
            return "($lhs) || ($rhs)"
        }

    }

    @JvmInline
    private value class ValueLookUpOperation(val binding: BindingIdentifier) : Operation {

        override fun bindings(): BindingIdentifierSet {
            return bindingIdentifierSetOf(binding)
        }

        override fun eval(input: OperationValue): OperationValue {
            return input.mapping.get(binding).into()
        }

        override fun toString(): String {
            return "?${binding.name}"
        }

    }

    @JvmInline
    private value class ConstantValueOperation<V: OperationValue>(val constant: V) : Operation {

        override fun bindings(): BindingIdentifierSet {
            return BindingIdentifierSet.EMPTY
        }

        override fun eval(input: OperationValue): OperationValue {
            return constant
        }

        override fun toString(): String {
            return constant.toString()
        }

    }

    class BooleanCoercionOperation(val context: QueryContext, val parent: Operation) : Operation {

        override fun bindings(): BindingIdentifierSet {
            return parent.bindings()
        }

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

        override fun toString(): String {
            return "BOOL(${parent})"
        }

    }

    private fun Quad.Element.into() = OperationValue.SingleValue(this)

    private fun DateTime.into() = OperationValue.DateValue(this)

    private fun Quad.Element?.into() = this?.let { OperationValue.SingleValue(this) } ?: OperationValue.Unbound

    private fun TermIdentifier?.into() = this?.let { OperationValue.SingleValueIdentifier(this) } ?: OperationValue.Unbound

    private fun Mapping.into() = OperationValue.SingleMapping(this)

    fun OperationValue.getTerm(context: QueryContext): Quad.Element? = when (this) {
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

    object BuiltinFunction {

        fun LANG(context: QueryContext, arg: Operation) = object: Operation {

            override fun bindings(): BindingIdentifierSet {
                return arg.bindings()
            }

            override fun eval(input: OperationValue): OperationValue {
                val term = arg.evalToSingleQuadElementOrNull(context, input)
                if (term !is Quad.LangString) {
                    return OperationValue.Unbound
                }
                return Quad.Literal(term.language).into()
            }

            override fun toString(): String {
                return "LANG($arg)"
            }

        }

        fun LANGMATCHES(context: QueryContext, tag: Operation, range: Operation) = object: Operation {

            override fun bindings(): BindingIdentifierSet {
                return tag.bindings() + range.bindings()
            }

            override fun eval(input: OperationValue): OperationValue {
                val tagValue = tag.evalToSingleQuadElementOrNull(context, input)
                // simple literal expected, as we're doing string matching
                if (tagValue !is Quad.SimpleLiteral) {
                    return OperationValue.Unbound
                }
                val tag = tagValue.value
                // same goes for the tag range
                val tagRange = range.evalToSingleQuadElementOrNull(context, input)
                // simple literal expected, as we're doing string matching
                if (tagRange !is Quad.SimpleLiteral || tagRange.type != XSD.string) {
                    return OperationValue.Unbound
                }
                val range = tagRange.value
                // now regular matching can be applied
                // special case first, where "*" matches all (non-empty!) language tags
                return if (range == "*") {
                    Quad.Literal(tag.isNotEmpty()).into()
                } else {
                    val currentLang = tag.substringBefore('-')
                    Quad.Literal(currentLang.contentEquals(range, ignoreCase = true)).into()
                }
            }

            override fun toString(): String {
                return "LANGMATCHES($tag, $range)"
            }

        }

        fun DATETIME(context: QueryContext, param: Operation) = object: Operation {

            override fun bindings(): BindingIdentifierSet {
                return param.bindings()
            }

            override fun eval(input: OperationValue): OperationValue {
                val termValue = param.evalToSingleQuadElementOrNull(context, input)
                if (termValue !is Quad.TypedLiteral || termValue.type != XSD.dateTime) {
                    return OperationValue.Unbound
                }
                return OperationValue.DateValue(DateTime.parse(termValue.value))
            }

            override fun toString(): String {
                return "DATETIME($param)"
            }

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

private fun Quad.TypedLiteral.isDateTimeValue(): Boolean {
    return type == XSD.dateTime
}

private val Quad.TypedLiteral.numericalValue: Number?
    get() {
        if (!this.type.value.startsWith(XSD.base_uri)) {
            return null
        }
        return when (this.type.value.subSequence(XSD.base_uri.length, this.type.value.length)) {
            "double", "decimal" -> value.toDoubleOrNull()
            "float" -> value.toFloatOrNull()
            "long" -> value.toLongOrNull()
            "int", "integer" -> value.toIntOrNull()
            "short" -> value.toShortOrNull()
            else -> null
        }
    }

private fun Number.compareTo(other: Number): Int {
    return this.toDouble().compareTo(other.toDouble())
}

private fun Expression.OperationValue.isTrue(): Boolean = when (this) {
    is Expression.OperationValue.SingleMapping -> false
    is Expression.OperationValue.SingleValue -> term == Quad.Literal(true)
    is Expression.OperationValue.SingleValueIdentifier -> false
    is Expression.OperationValue.DateValue -> false
    is Expression.OperationValue.Unbound -> false
}

private fun DateTime.Companion.parseOrNull(str: String): DateTime? =
    runCatching { DateTime.parse(str) }.getOrNull()
