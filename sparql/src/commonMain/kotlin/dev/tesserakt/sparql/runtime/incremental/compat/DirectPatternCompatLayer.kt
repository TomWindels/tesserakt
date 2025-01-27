package dev.tesserakt.sparql.runtime.incremental.compat

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.compiler.ast.PatternAST
import dev.tesserakt.sparql.compiler.ast.PatternsAST
import dev.tesserakt.sparql.runtime.common.types.Pattern
import dev.tesserakt.sparql.runtime.incremental.types.Patterns

/**
 * Similar compat layer as the regular [PatternCompatLayer], but with a more direct conversion method where no unions
 *  are being generated for alt path structures.
 */
object DirectPatternCompatLayer : IncrementalCompatLayer<PatternsAST, Patterns>() {

    private var _id = 0

    override fun convert(source: PatternsAST): Patterns {
        val patterns = mutableListOf<Pattern>()
        source.forEach { patterns.insert(it) }
        return Patterns(patterns)
    }

    private fun MutableList<Pattern>.insert(pattern: PatternAST) {
        insert(subj = pattern.s, pred = pattern.p, obj = pattern.o)
    }

    private fun MutableList<Pattern>.insert(
        subj: PatternAST.Subject,
        pred: PatternAST.Predicate,
        obj: PatternAST.Object
    ) {
        when (pred) {
            is PatternAST.Alts -> {
                add(
                    element = Pattern(
                        s = subj.toPatternSubject(),
                        p = Pattern.Alts(pred.allowed.map { it.toPatternPredicate() as Pattern.UnboundPredicate }),
                        o = obj.toPatternObject(this)
                    )
                )
            }

            is PatternAST.Chain -> {
                when (pred.chain.size) {
                    0 -> throw IllegalStateException("Empty predicate chain is not allowed!")
                    1 -> insert(subj, pred.chain.single(), obj)
                    else -> {
                        var start = subj
                        var end: PatternAST.Element
                        (0 until pred.chain.size - 1).forEach { i ->
                            end = generateBlank().toPatternASTObject()
                            insert(start, pred.chain[i], end)
                            start = end
                        }
                        insert(start, pred.chain.last(), obj)
                    }
                }
            }

            is PatternAST.Binding -> {
                add(
                    element = Pattern(
                        s = subj.toPatternSubject(),
                        p = Pattern.RegularBinding(pred.name),
                        o = obj.toPatternObject(this)
                    )
                )
            }

            is PatternAST.Exact -> {
                add(
                    element = Pattern(
                        s = subj.toPatternSubject(),
                        p = Pattern.Exact(pred.term),
                        o = obj.toPatternObject(this)
                    )
                )
            }

            is PatternAST.Not,
            is PatternAST.ZeroOrMore,
            is PatternAST.OneOrMore -> {
                // it's contents can't be further flattened, so inserting it directly
                add(
                    element = Pattern(
                        s = subj.toPatternSubject(),
                        p = pred.toPatternPredicate(),
                        o = obj.toPatternObject(this)
                    )
                )
            }
        }
    }

    private fun PatternAST.Subject.toPatternSubject(): Pattern.Subject = when (this) {
        is PatternAST.Binding -> Pattern.RegularBinding(name)
        is PatternAST.Exact -> Pattern.Exact(term)
    }

    private fun PatternAST.Object.toPatternObject(
        context: MutableList<Pattern>
    ): Pattern.Object = when (this) {
        is PatternAST.Binding -> Pattern.RegularBinding(name)
        is PatternAST.Exact -> Pattern.Exact(term)
        is PatternAST.BlankObject -> {
            val generated = generateBlank()
            val subj = generated.toPatternASTObject()
            properties.forEach {
                context.insert(
                    subj = subj,
                    pred = it.p,
                    obj = it.o
                )
            }
            generated
        }
    }

    private fun PatternAST.Predicate.toPatternPredicate(): Pattern.Predicate = when (this) {
        is PatternAST.Alts -> {
            val mapped = allowed.map { it.toPatternPredicate() }
            if (mapped.all { it is Pattern.StatelessPredicate }) {
                Pattern.SimpleAlts(mapped.unsafeCast())
            } else if (mapped.all { it is Pattern.UnboundPredicate }) {
                Pattern.Alts(mapped.unsafeCast())
            } else {
                throw IllegalArgumentException("Invalid combination of predicates found inside of alternate pattern group: $mapped")
            }
        }

        is PatternAST.Chain -> {
            val mapped = chain.map { it.toPatternPredicate() }
            if (mapped.all { it is Pattern.UnboundPredicate }) {
                Pattern.UnboundSequence(mapped.unsafeCast())
            } else {
                Pattern.Sequence(mapped)
            }
        }

        is PatternAST.Exact -> Pattern.Exact(term)
        is PatternAST.Not -> Pattern.Negated(term = predicate.termOrBail())
        is PatternAST.OneOrMore -> Pattern.OneOrMore(element = value.toUnboundPatternPredicateOrBail())
        is PatternAST.ZeroOrMore -> Pattern.ZeroOrMore(element = value.toUnboundPatternPredicateOrBail())
        is PatternAST.Binding -> Pattern.RegularBinding(name)
    }

    private fun PatternAST.Predicate.toUnboundPatternPredicateOrBail(): Pattern.UnboundPredicate = when (this) {
        is PatternAST.Alts -> toPatternPredicate() as Pattern.UnboundPredicate
        is PatternAST.Chain -> Pattern.UnboundSequence(chain = chain.map { it.toUnboundPatternPredicateOrBail() })
        is PatternAST.Exact -> Pattern.Exact(term)
        is PatternAST.Not -> Pattern.Negated(term = predicate.termOrBail())
        is PatternAST.OneOrMore -> Pattern.OneOrMore(element = value.toUnboundPatternPredicateOrBail())
        is PatternAST.ZeroOrMore -> Pattern.ZeroOrMore(element = value.toUnboundPatternPredicateOrBail())
        is PatternAST.Binding -> throw IllegalArgumentException("Invalid predicate usage! Binding `$name` cannot appear here.")
    }

    private fun generateBlank() = Pattern.GeneratedBinding(id = _id++)

    private fun Pattern.GeneratedBinding.toPatternASTObject() = PatternAST.Binding(name = name)

}

/* helpers */

private inline fun <R> Any.unsafeCast(): R {
    @Suppress("UNCHECKED_CAST")
    return this as R
}

private inline fun PatternAST.Predicate.termOrBail(): Quad.Term =
    (this as? PatternAST.Exact)?.term ?: throw IllegalArgumentException("IRI predicate expected, got `$this`")
