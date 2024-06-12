package dev.tesserakt.sparql.runtime.incremental.compat

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.compiler.ast.PatternAST
import dev.tesserakt.sparql.compiler.ast.PatternsAST
import dev.tesserakt.sparql.runtime.common.types.Pattern
import dev.tesserakt.sparql.runtime.incremental.types.Patterns
import dev.tesserakt.sparql.runtime.incremental.types.Query.QueryBody
import dev.tesserakt.sparql.runtime.incremental.types.StatementsSegment
import dev.tesserakt.sparql.runtime.incremental.types.Union

class PatternCompatLayer(
    val appendUnion: (blocks: List<StatementsSegment>) -> Unit
) : IncrementalCompatLayer<PatternsAST, Patterns>() {

    private var _id = 0

    override fun convert(source: PatternsAST): Patterns {
        val ctx = StatementsBuilder()
        source.forEach {
            ctx.insert(it)
        }
        ctx.unions.forEach { union -> appendUnion(union) }
        return Patterns(ctx.patterns)
    }

    private interface ProcessingContext {
        fun insert(pattern: Pattern)
        fun insert(union: List<StatementsSegment>)
    }

    private inner class StatementsBuilder(
        val patterns: MutableList<Pattern> = mutableListOf(),
        val unions: MutableList<List<StatementsSegment>> = mutableListOf()
    ) : ProcessingContext {
        override fun insert(pattern: Pattern) {
            patterns.add(pattern)
        }

        override fun insert(union: List<StatementsSegment>) {
            unions.add(union)
        }

        fun build() =
            StatementsSegment(QueryBody(patterns = Patterns(items = patterns), unions = unions.map { Union(it) }, optional = emptyList()))
    }

    private fun ProcessingContext.insert(pattern: PatternAST) {
        insert(subj = pattern.s, pred = pattern.p, obj = pattern.o)
    }

    private fun ProcessingContext.insert(
        subj: PatternAST.Subject,
        pred: PatternAST.Predicate,
        obj: PatternAST.Object
    ) {
        when (pred) {
            is PatternAST.Alts -> {
                val segments = pred.allowed.map { alt ->
                    val builder = StatementsBuilder()
                    builder.insert(
                        subj = subj,
                        pred = alt,
                        obj = obj
                    )
                    builder.build()
                }
                insert(segments)
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
                insert(
                    pattern = Pattern(
                        s = subj.toPatternSubject(),
                        p = Pattern.RegularBinding(pred.name),
                        o = obj.toPatternObject(this)
                    )
                )
            }

            is PatternAST.Exact -> {
                insert(
                    pattern = Pattern(
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
                insert(
                    pattern = Pattern(
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
        context: ProcessingContext
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
            if (mapped.all { it is Pattern.UnboundPredicate }) {
                Pattern.UnboundAlts(mapped.unsafeCast())
            } else {
                Pattern.Alts(mapped)
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
        is PatternAST.Alts -> Pattern.UnboundAlts(allowed = allowed.map { it.toUnboundPatternPredicateOrBail() })
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
