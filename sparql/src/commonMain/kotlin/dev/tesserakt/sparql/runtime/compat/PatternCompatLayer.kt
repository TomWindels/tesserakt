package dev.tesserakt.sparql.runtime.compat

import dev.tesserakt.sparql.compiler.ast.PatternAST
import dev.tesserakt.sparql.compiler.ast.PatternsAST
import dev.tesserakt.sparql.runtime.types.PatternASTr
import dev.tesserakt.sparql.runtime.types.PatternsASTr

class PatternCompatLayer(
    val onUnionCreated: (blocks: List<PatternsASTr>) -> Unit
): CompatLayer<PatternsAST, PatternsASTr>() {

    override fun convert(source: PatternsAST): PatternsASTr = PatternsASTr(
        buildList { source.forEach { it.insert(this) } }
    )

    /* helpers */

    private fun PatternAST.insert(results: MutableList<PatternASTr>) {
        return p.insert(results, s.convert(), o.nameOrInsert(results))
    }

    private fun PatternAST.Predicate.insert(
        results: MutableList<PatternASTr>,
        start: PatternASTr.Subject,
        end: PatternASTr.Object
    ) {
        when (this) {
            is PatternAST.Chain -> {
                var s = start
                for (i in 0 ..< chain.size - 1) {
                    val o = PatternASTr.GeneratedBinding(++id)
                    val element = PatternASTr(
                        s = s,
                        p = chain[i].convertToSinglePredicate() ?: bail("Input is incompatible"),
                        o = o
                    )
                    results.add(element)
                    s = o
                }
                val element = PatternASTr(
                    s = s,
                    p = chain.last().convertToSinglePredicate() ?: bail("Input is incompatible"),
                    o = end
                )
                results.add(element)
            }

            is PatternAST.Alts -> {
                val splitUp = split()
                if (splitUp.size == 1) {
                    splitUp.first().insert(results, start, end)
                } else {
                    // inserting them all as unions
                    val blocks = mutableListOf<PatternsASTr>()
                    splitUp.forEach { path ->
                        val content = mutableListOf<PatternASTr>()
                        path.insert(content, start, end)
                        blocks.add(PatternsASTr(content))
                    }
                    onUnionCreated(blocks)
                }
            }

            is PatternAST.Binding,
            is PatternAST.Exact,
            is PatternAST.Not,
            is PatternAST.OneOrMore,
            is PatternAST.ZeroOrMore -> results.add(
                PatternASTr(s = start, p = convertToSinglePredicate() ?: bail("Input is incompatible"), o = end)
            )
        }
    }

    /**
     * Splits up the incoming alternative paths into different segments. The returned list is at least of size 1. If
     *  the input consists contains alternatives that are paths or repeating segments, they are split up into multiple
     *  predicates inside the returned list, so they can be split up into unions for runtime compatibility.
     */
    private fun PatternAST.Alts.split(): List<PatternAST.Predicate> {
        // total amount of predicates
        val result = mutableListOf<PatternAST.Predicate>()
        // collection of "normal" alt paths that are allowed in the ASTr representation (can be mapped to fixed
        //  predicate in subsequent steps)
        val regular = mutableListOf<PatternAST.Predicate>()
        allowed.forEach { predicate ->
            when (predicate) {
                is PatternAST.Alts ->
                    predicate.split().let { processed ->
                        // if the last is of type alts, then it can be directly added here, otherwise, they are all
                        //  part of the regular split up
                        val alts = processed.last()
                        if (alts is PatternAST.Alts) {
                            // adding its contents to the regular list
                            regular.addAll(alts.allowed)
                            // only adding the others
                            result.addAll(processed.subList(0, processed.size - 1))
                        } else {
                            // adding them all instead
                            result.addAll(processed)
                        }
                    }

                is PatternAST.Chain ->
                    result.add(predicate)

                is PatternAST.Exact,
                is PatternAST.Not ->
                    regular.add(predicate)

                is PatternAST.OneOrMore,
                is PatternAST.ZeroOrMore,
                is PatternAST.Binding ->
                    result.add(predicate)
            }
        }
        if (regular.isNotEmpty()) {
            result.add(PatternAST.Alts(regular))
        }
        return result
    }

    private fun PatternAST.Object.nameOrInsert(results: MutableList<PatternASTr>): PatternASTr.Object =
        when (this) {
            is PatternAST.BlankObject ->
                PatternASTr.GeneratedBinding(++id).also { name -> insert(results, name) }

            is PatternAST.Binding ->
                PatternASTr.RegularBinding(name)

            is PatternAST.Exact ->
                PatternASTr.Exact(term)
        }

    private fun PatternAST.BlankObject.insert(
        results: MutableList<PatternASTr>,
        name: PatternASTr.GeneratedBinding
    ) {
        properties.forEach { (p, o) ->
            p.insert(results, name, o.nameOrInsert(results))
        }
    }

    private fun PatternAST.Subject.convert(): PatternASTr.Subject = when (this) {
        is PatternAST.Binding -> PatternASTr.RegularBinding(name)
        is PatternAST.Exact -> PatternASTr.Exact(term)
    }

    /**
     * Converts the predicate to a single RT version if possible, `null` otherwise
     */
    private fun PatternAST.Predicate.convertToSinglePredicate(): PatternASTr.Predicate? {
        return when (this) {
            is PatternAST.Binding -> {
                PatternASTr.RegularBinding(name)
            }
            is PatternAST.OneOrMore -> {
                when (value) {
                    is PatternAST.Binding -> {
                        PatternASTr.OneOrMoreBound(predicate = PatternASTr.RegularBinding(value.name))
                    }
                    else -> {
                        PatternASTr.OneOrMoreFixed(predicate = value.convertToSingleFixedPredicate() ?: return null)
                    }
                }
            }
            is PatternAST.ZeroOrMore -> {
                when (value) {
                    is PatternAST.Binding -> {
                        PatternASTr.ZeroOrMoreBound(predicate = PatternASTr.RegularBinding(value.name))
                    }
                    else -> {
                        PatternASTr.ZeroOrMoreFixed(predicate = value.convertToSingleFixedPredicate() ?: return null)
                    }
                }
            }
            is PatternAST.Alts,
            is PatternAST.Chain,
            is PatternAST.Exact,
            is PatternAST.Not -> {
                convertToSingleFixedPredicate()
            }
        }
    }

    /**
     * Converts the predicate to a single fixed RT version if possible, `null` otherwise
     */
    private fun PatternAST.Predicate.convertToSingleFixedPredicate(): PatternASTr.FixedPredicate? {
        return when (this) {
            is PatternAST.Binding -> null
            is PatternAST.Exact -> PatternASTr.Exact(term)
            is PatternAST.Chain -> null
            is PatternAST.Alts ->
                allowed.map { it.convertToSingleFixedPredicate() ?: return null }.let { PatternASTr.Alts(it) }
            is PatternAST.Not ->
                PatternASTr.Inverse(predicate.convertToSingleFixedPredicate() ?: return null)
            is PatternAST.OneOrMore -> null // not a fixed predicate
            is PatternAST.ZeroOrMore -> null // not a fixed predicate
        }
    }

    companion object {

        // id used to uniquely create new runtime blank terms within the same process; this approach is not 100%
        //  foolproof but compiling and converting a single query in multiple processes where this static value can
        //  differ is not an expected use case
        private var id = -1

    }

}
