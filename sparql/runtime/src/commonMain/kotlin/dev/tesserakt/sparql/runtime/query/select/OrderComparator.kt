package dev.tesserakt.sparql.runtime.query.select

import dev.tesserakt.rdf.ontology.XSD
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.runtime.evaluation.BindingIdentifier
import dev.tesserakt.sparql.runtime.evaluation.TermIdentifier
import dev.tesserakt.sparql.runtime.evaluation.context.QueryContext
import dev.tesserakt.sparql.runtime.evaluation.mapping.Mapping
import dev.tesserakt.sparql.types.DateTime
import dev.tesserakt.sparql.types.Ordering
import kotlin.math.sign

class OrderComparator private constructor(
    private val context: QueryContext,
    private val elements: List<SortElement>
): Comparator<Mapping> {

    data class SortElement(
        val id: BindingIdentifier,
        /** 1 or -1 to satisfy ASC / DESC */
        val sign: Int,
    )

    override fun compare(a: Mapping, b: Mapping): Int {
        this.elements.forEach { element ->
            val left = a.get(element.id)
            val right = b.get(element.id)
            val cmp = compare(left, right)
            if (cmp != 0) {
                return element.sign * cmp.sign
            }
            /* else continuing */
        }
        return 0
    }

    /**
     * Compares the specific terms [left] and [right], returning `0` if they match exactly, a negative number
     *  if `left < right`, otherwise a positive number
     */
    private fun compare(left: TermIdentifier?, right: TermIdentifier?): Int {
        // covers the cases where they point to the exact same RDF term, or both are unbound (`null`)
        if (left == right) {
            return 0
        }
        // unbound values are the smallest possible value, so left being unbound here guarantees right is not unbound,
        //  and thus left is smaller than right
        if (left == null) {
            return -1
        }
        // mirrored logic from above
        if (right == null) {
            return 1
        }
        // both point to existing terms, and thus have to be resolved now to do the more concrete comparison
        return compare(
            a = context.resolveTerm(left.id),
            b = context.resolveTerm(right.id),
        )
    }

    companion object: Comparator<Quad.Element> {

        operator fun invoke(context: QueryContext, ast: Ordering) = OrderComparator(
            context = context,
            elements = ast
                .elements
                .map { element ->
                    SortElement(
                        id = BindingIdentifier(context.resolveBinding(element.binding.name)),
                        sign = when (element.mode) {
                            Ordering.Element.Mode.Ascending -> 1
                            // reverse ordering
                            Ordering.Element.Mode.Descending -> -1
                        },
                    )
                }
        )

        override fun compare(a: Quad.Element, b: Quad.Element): Int {
            return when (a) {
                is Quad.BlankTerm -> {
                    when (b) {
                        // no strict ordering between blank terms
                        is Quad.BlankTerm -> 0
                        // left is smaller, as blank terms are sorted first compared to other RDF terms
                        else -> -1
                    }
                }
                Quad.DefaultGraph -> {
                    when (b) {
                        // URIs are 'behind' blank terms
                        is Quad.BlankTerm -> 1
                        Quad.DefaultGraph -> 0
                        // anything else is 'after' it, including other URIs
                        else -> -1
                    }
                }
                is Quad.NamedTerm -> {
                    when (b) {
                        // URIs are 'behind' blank terms, and the default graph
                        is Quad.BlankTerm, Quad.DefaultGraph -> 1
                        // URIs are compared based on contents
                        is Quad.NamedTerm -> a.value.compareTo(b.value)
                        // only literals follow, which we are in front of
                        else -> -1
                    }
                }
                is Quad.LangString -> {
                    when (b) {
                        is Quad.LangString -> a.value.compareTo(b.value)

                        is Quad.Literal -> -1
                        is Quad.BlankTerm,
                        is Quad.NamedTerm,
                        Quad.DefaultGraph -> 1
                    }
                }
                is Quad.Literal -> {
                    when (b) {
                        is Quad.Literal -> compare(a, b)
                        // with lhs being a literal, anything that is not a literal comes before it
                        else -> 1
                    }
                }
            }
        }

        private fun compare(left: Quad.Literal, right: Quad.Literal): Int {
            if (left.type == right.type) {
                val comparator = Comparators[left.type]
                if (comparator != null) {
                    return comparator.invoke(left, right)
                }
            }
            // even though it's possible for left and right to represent valid numericals w/o checking their data types,
            //  it would also allow for non-XSD types to be compared, which is not supposed to happen
            if (left.hasNumericalType() && right.hasNumericalType()) {
                return NumericalComparison.invoke(left, right)
            }
            // falling back to lexical comparison
            return left.value.compareTo(right.value)
        }

        private val NumericalComparison = cmp@ { left: Quad.Literal, right: Quad.Literal ->
            val a = left.value.toDoubleOrNull() ?: return@cmp 0
            val b = right.value.toDoubleOrNull() ?: return@cmp 0
            (a - b).sign.toInt()
        }
        private val DateTimeComparison = cmp@ { left: Quad.Literal, right: Quad.Literal ->
            val a = left.value.toDateTimeOrNull() ?: return@cmp 0
            val b = right.value.toDateTimeOrNull() ?: return@cmp 0
            a.compareTo(b)
        }
        private val BooleanComparison = cmp@ { left: Quad.Literal, right: Quad.Literal ->
            val a = left.value.toBooleanStrictOrNull() ?: return@cmp 0
            val b = right.value.toBooleanStrictOrNull() ?: return@cmp 0
            a.compareTo(b)
        }
        private val StringComparison = cmp@ { left: Quad.Literal, right: Quad.Literal ->
            left.value.compareTo(right.value)
        }

        private val Comparators = mapOf(
            XSD.int to NumericalComparison,
            XSD.integer to NumericalComparison,
            XSD.long to NumericalComparison,
            XSD.float to NumericalComparison,
            XSD.double to NumericalComparison,
            XSD.decimal to NumericalComparison,

            XSD.boolean to BooleanComparison,

            XSD.string to StringComparison,

            XSD.dateTime to DateTimeComparison,
        )

        private val NumericalTypes = setOf(
            XSD.int,
            XSD.integer,
            XSD.long,
            XSD.float,
            XSD.double,
            XSD.decimal,
        )

        private fun Quad.Literal.hasNumericalType() = type in NumericalTypes

        private fun String.toDateTimeOrNull() = runCatching { DateTime.parse(this) }.getOrNull()

    }

}
