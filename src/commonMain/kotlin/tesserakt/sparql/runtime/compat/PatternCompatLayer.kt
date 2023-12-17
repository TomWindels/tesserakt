package tesserakt.sparql.runtime.compat

import tesserakt.sparql.compiler.types.PatternAST
import tesserakt.sparql.compiler.types.PatternsAST
import tesserakt.sparql.runtime.types.PatternASTr
import tesserakt.sparql.runtime.types.PatternsASTr

class PatternCompatLayer: CompatLayer<PatternsAST, PatternsASTr>() {

    // TODO: have a "split up" interface lateinit var available for when more complex groups are formed, so
    //  union's can be created of the various more-complex groups instead of bailing

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

            is PatternAST.Alts,
            is PatternAST.Binding,
            is PatternAST.Exact,
            is PatternAST.Not,
            is PatternAST.OneOrMore,
            is PatternAST.ZeroOrMore -> results.add(
                PatternASTr(s = start, p = convertToSinglePredicate() ?: bail("Input is incompatible"), o = end)
            )
        }
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
