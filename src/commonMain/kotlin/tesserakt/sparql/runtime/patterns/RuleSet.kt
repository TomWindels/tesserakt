package tesserakt.sparql.runtime.patterns

import tesserakt.rdf.types.Triple
import tesserakt.sparql.compiler.types.Pattern
import tesserakt.sparql.compiler.types.Patterns
import kotlin.jvm.JvmInline

@JvmInline
value class RuleSet(
    val rules: List<QueryRule>
) {

    data class QueryRule(
        val s: Element,
        val p: Element,
        val o: Element
    ) {

        sealed interface Element {

            @JvmInline
            value class Exact(val term: Triple.Term): Element

            @JvmInline
            value class Binding(val name: String): Element

        }

        private val constraints = listOf(
            (s as? Element.Exact)?.term,
            (p as? Element.Exact)?.term,
            (o as? Element.Exact)?.term
        )

        internal val bindings = listOf(
            (s as? Element.Binding)?.name,
            (p as? Element.Binding)?.name,
            (o as? Element.Binding)?.name
        )

        /**
         * Processes the incoming triple, returns `null` if no match is found, otherwise an array of size 3
         *  with the bounded values (if any)
         */
        internal fun process(triple: Triple): Array<Triple.Term?>? {
            if (!fits(triple)) {
                return null
            }
            // FIXME: bindings is currently only used here, so can be optimised
            return Array(3) { i -> triple[i].takeIf { bindings[i] != null } }
        }

        /** Checks if any `Exact` matches aren't satisfied (meaning the triple doesn't apply here) **/
        internal fun fits(triple: Triple): Boolean {
            constraints.forEachIndexed { i, constraint ->
                if (constraint != null && constraint != triple[i]) {
                    return false
                }
            }
            return true
        }
    }

    companion object {

        fun from(patterns: Patterns) =
            RuleSet(rules = patterns.toFilterRules().also { println(it) })

        /* helpers */

        private fun Patterns.toFilterRules(): List<QueryRule> = buildList {
            // always incrementing the blank id with the number of new pattern id's to make sure there's no
            //  incorrect overlap happening between rules
            var blankId = 0
            this@toFilterRules.forEach { pattern ->
                val new = pattern.toFilterRules(blankId = blankId)
                blankId += new.size
                addAll(new)
            }
        }.distinct()

        private fun Pattern.toFilterRules(blankId: Int): List<QueryRule> = buildList {
            var subject = s.asFilterElement()
            var `object`: QueryRule.Element
            var blank = blankId
            val predicates = p.extractFilterElements()
            for (i in 0 until predicates.size - 1) {
                // setting the intermediate object
                `object` = QueryRule.Element.Binding("q_b${blank++}")
                // adding the current iteration
                add(QueryRule(subject, predicates[i], `object`))
                // moving the subject
                subject = `object`
            }
            // adding the final one, pointing to the object
            add(QueryRule(subject, predicates.last(), o.asFilterElement()))
        }

        private fun Pattern.Subject.asFilterElement(): QueryRule.Element {
            return when (this) {
                is Pattern.Binding -> QueryRule.Element.Binding(name)
                is Pattern.Exact -> QueryRule.Element.Exact(value)
            }
        }

        private fun Pattern.Object.asFilterElement(): QueryRule.Element {
            return when (this) {
                is Pattern.Binding -> QueryRule.Element.Binding(name)
                is Pattern.Exact -> QueryRule.Element.Exact(value)
            }
        }

        private fun Pattern.Predicate.extractFilterElements(): List<QueryRule.Element> = buildList {
            when (this@extractFilterElements) {
                is Pattern.Binding -> add(QueryRule.Element.Binding(name))
                is Pattern.Exact -> add(QueryRule.Element.Exact(value))
                is Pattern.Chain -> list.forEach { addAll(it.extractFilterElements()) }
                is Pattern.Constrained -> TODO()
                is Pattern.Not -> TODO()
                is Pattern.Repeating -> TODO()
            }
        }

    }

}
