package dev.tesserakt.sparql.runtime.incremental.state

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.runtime.common.types.Pattern
import dev.tesserakt.sparql.runtime.common.util.Debug
import dev.tesserakt.sparql.runtime.core.Mapping
import dev.tesserakt.sparql.runtime.core.mappingOf
import dev.tesserakt.sparql.runtime.core.pattern.bindingName
import dev.tesserakt.sparql.runtime.core.pattern.matches
import dev.tesserakt.sparql.runtime.incremental.collection.mutableJoinCollection
import dev.tesserakt.sparql.runtime.incremental.delta.Delta

internal sealed class IncrementalTriplePatternState<P : Pattern.Predicate>(
    val s: Pattern.Subject,
    val p: P,
    val o: Pattern.Object
): MutableJoinState {

    sealed class ArrayBackedPattern<P : Pattern.Predicate>(
        subj: Pattern.Subject,
        pred: P,
        obj: Pattern.Object
    ) : IncrementalTriplePatternState<P>(subj, pred, obj) {

        private val data = mutableJoinCollection(bindingNamesOf(subj, pred, obj))

        override val cardinality get() = data.mappings.size

        final override fun process(delta: Delta.Data) {
            when (delta) {
                is Delta.DataAddition -> {
                    val new = peek(delta)
                    data.addAll(new)
                }

                is Delta.DataDeletion -> {
                    val removed = peek(delta)
                    data.removeAll(removed)
                }
            }
        }

        final override fun join(mapping: Mapping): List<Mapping> {
            Debug.onArrayPatternJoinExecuted()
            return data.join(mapping)
        }

        // as these are "stateless" compared to prior data, the operation type associated with the delta is irrelevant

        final override fun peek(delta: Delta.DataAddition): List<Mapping> {
            return peek(delta.value)
        }

        abstract fun peek(quad: Quad): List<Mapping>

    }

    data class ExactPattern(
        val subj: Pattern.Subject,
        val pred: Pattern.Exact,
        val obj: Pattern.Object
    ) : ArrayBackedPattern<Pattern.Exact>(subj, pred, obj) {

        override fun peek(quad: Quad): List<Mapping> {
            if (!subj.matches(quad.s) || !pred.matches(quad.p) || !obj.matches(quad.o)) {
                return emptyList()
            }
            // checking to see if there's any matches with the given triple
            val match = mappingOf(
                subj.bindingName to quad.s,
                obj.bindingName to quad.o
            )
            return listOf(match)
        }

    }

    data class BindingPattern(
        val subj: Pattern.Subject,
        val pred: Pattern.Binding,
        val obj: Pattern.Object
    ) : ArrayBackedPattern<Pattern.Binding>(subj, pred, obj) {

        override fun peek(quad: Quad): List<Mapping> {
            if (!subj.matches(quad.s) || !obj.matches(quad.o)) {
                return emptyList()
            }
            // checking to see if there's any matches with the given triple
            val match = mappingOf(
                subj.bindingName to quad.s,
                pred.name to quad.p,
                obj.bindingName to quad.o
            )
            return listOf(match)
        }

    }

    data class NegatedPattern(
        val subj: Pattern.Subject,
        val pred: Pattern.Negated,
        val obj: Pattern.Object
    ) : ArrayBackedPattern<Pattern.Negated>(subj, pred, obj) {

        override fun peek(quad: Quad): List<Mapping> {
            if (!subj.matches(quad.s) || pred.term == quad.p || !obj.matches(quad.o)) {
                return emptyList()
            }
            // checking to see if there's any matches with the given triple
            val match = mappingOf(
                subj.bindingName to quad.s,
                obj.bindingName to quad.o
            )
            return listOf(match)
        }

    }

    class RepeatingPattern(
        subj: Pattern.Subject,
        pred: Pattern.RepeatingPredicate,
        obj: Pattern.Object
    ) : IncrementalTriplePatternState<Pattern.RepeatingPredicate>(subj, pred, obj) {

        private val state = when (pred) {
            is Pattern.ZeroOrMore -> IncrementalPathState.zeroOrMore(
                start = subj,
                predicate = pred,
                end = obj
            )

            is Pattern.OneOrMore -> IncrementalPathState.oneOrMore(
                start = subj,
                predicate = pred,
                end = obj
            )
        }

        override val cardinality: Int get() = state.cardinality

        override fun process(delta: Delta.Data) {
            state.process(delta.value)
        }

        override fun peek(delta: Delta.DataAddition): List<Mapping> {
            return state.peek(delta.value)
        }

        override fun peek(delta: Delta.DataDeletion): List<Mapping> {
            // TODO()
            return emptyList()
        }

        override fun join(mapping: Mapping): List<Mapping> {
            // TODO(perf) state API improvements re: single mapping insertion
            return state.join(listOf(mapping))
        }

    }

    class AltPattern(
        s: Pattern.Subject,
        p: Pattern.Alts,
        o: Pattern.Object
    ) : IncrementalTriplePatternState<Pattern.Alts>(s, p, o) {

        private val states = p.allowed.map { p -> Pattern(s, p, o).createIncrementalPatternState() }

        override val cardinality: Int get() = states.sumOf { it.cardinality }

        override fun process(delta: Delta.Data) {
            states.forEach { it.process(delta) }
        }

        override fun peek(delta: Delta.DataAddition): List<Mapping> {
            return states.flatMap { it.peek(delta) }
        }

        override fun peek(delta: Delta.DataDeletion): List<Mapping> {
            return states.flatMap { it.peek(delta) }
        }

        override fun join(mapping: Mapping): List<Mapping> {
            return states.flatMap { it.join(mapping) }
        }

    }

    class SimpleAltPattern(
        s: Pattern.Subject,
        p: Pattern.SimpleAlts,
        o: Pattern.Object
    ) : IncrementalTriplePatternState<Pattern.SimpleAlts>(s, p, o) {

        private val states = p.allowed.map { p -> Pattern(s, p, o).createIncrementalPatternState() }

        override val cardinality: Int get() = states.sumOf { it.cardinality }

        override fun process(delta: Delta.Data) {
            states.forEach { it.process(delta) }
        }

        override fun peek(delta: Delta.DataAddition): List<Mapping> {
            return states.flatMap { it.peek(delta) }
        }

        override fun peek(delta: Delta.DataDeletion): List<Mapping> {
            return states.flatMap { it.peek(delta) }
        }

        override fun join(mapping: Mapping): List<Mapping> {
            return states.flatMap { it.join(mapping) }
        }

    }

    class SequencePattern(
        s: Pattern.Subject,
        p: Pattern.Sequence,
        o: Pattern.Object
    ) : IncrementalTriplePatternState<Pattern.Sequence>(s, p, o) {

        private val tree: JoinTree

        private val mappings = mutableListOf<Mapping>()
        override val cardinality: Int get() = mappings.size

        init {
            require(p.chain.size > 1)
            val chain = ArrayList<Pattern>(p.chain.size)
            var start = s
            (0 until p.chain.size - 1).forEach { i ->
                val p = p.chain[i]
                val end = generateBinding()
                chain.add(Pattern(start, p, end))
                start = end.toSubject()
            }
            chain.add(Pattern(start, p.chain.last(), o))
            tree = JoinTree(chain)
        }

        override fun process(delta: Delta.Data) {
            tree.process(delta)
        }

        override fun peek(delta: Delta.DataAddition): List<Mapping> {
            // the tree is built up using regular patterns only, meaning that there's a guarantee that all resulting
            //  solutions are additions
            return tree.peek(delta).map { it.value }
        }

        override fun join(mapping: Mapping): List<Mapping> {
            // the tree is built up using regular patterns only, meaning that evaluating everything as an addition
            //  should give correct results
            return tree.join(Delta.BindingsAddition(mapping)).map { it.value }
        }

    }

    class UnboundedSequencePattern(
        subj: Pattern.Subject,
        pred: Pattern.UnboundSequence,
        obj: Pattern.Object
    ) : IncrementalTriplePatternState<Pattern.UnboundSequence>(subj, pred, obj) {

        private val tree: JoinTree

        private val mappings = mutableListOf<Mapping>()
        override val cardinality: Int get() = mappings.size

        init {
            require(pred.chain.size > 1)
            val chain = ArrayList<Pattern>(pred.chain.size)
            var start = subj
            (0 until pred.chain.size - 1).forEach { i ->
                val p = pred.chain[i]
                val end = generateBinding()
                chain.add(Pattern(start, p, end))
                start = end.toSubject()
            }
            chain.add(Pattern(start, pred.chain.last(), obj))
            tree = JoinTree(chain)
        }

        override fun process(delta: Delta.Data) {
            tree.process(delta)
        }

        override fun peek(delta: Delta.DataAddition): List<Mapping> {
            // the tree is built up using regular patterns only, meaning that there's a guarantee that all resulting
            //  solutions are additions
            return tree.peek(delta).map { it.value }
        }

        override fun join(mapping: Mapping): List<Mapping> {
            // the tree is built up using regular patterns only, meaning that evaluating everything as an addition
            //  should give correct results
            return tree.join(Delta.BindingsAddition(mapping)).map { it.value }
        }

    }

    final override val bindings: Set<String> = bindingNamesOf(s, p, o)

    /**
     * Denotes the number of matches it contains, useful for quick cardinality calculations (e.g., joining this state
     *  on an empty solution results in [cardinality] results, or a size of 0 guarantees no results will get generated)
     */
    protected abstract val cardinality: Int

    abstract fun peek(delta: Delta.DataAddition): List<Mapping>

    open fun peek(delta: Delta.DataDeletion): List<Mapping> = peek(delta = Delta.DataAddition(delta.value))

    abstract fun join(mapping: Mapping): List<Mapping>

    // triple patterns can only get new results upon getting new data and lose results upon removing data, so two
    //  specialised delta functions can be made instead, that are mapped here once
    final override fun peek(delta: Delta.Data): List<Delta.Bindings> {
        return when (delta) {
            is Delta.DataAddition -> peek(delta).map { Delta.BindingsAddition(it) }
            is Delta.DataDeletion -> peek(delta).map { Delta.BindingsDeletion(it) }
        }
    }

    // triple patterns can only get new results upon getting new data and lose results upon removing data, so two
    //  specialised delta functions can be made instead, that are mapped here once
    final override fun join(delta: Delta.Bindings): List<Delta.Bindings> {
        return when (delta) {
            is Delta.BindingsAddition -> join(delta.value).map { Delta.BindingsAddition(it) }
            is Delta.BindingsDeletion -> join(delta.value).map { Delta.BindingsDeletion(it) }
        }
    }

    final override fun toString() = "$s $p $o - cardinality $cardinality"

    companion object {

        fun Pattern.createIncrementalPatternState(): IncrementalTriplePatternState<*> = when (p) {
            is Pattern.Exact -> ExactPattern(s, p, o)
            is Pattern.Negated -> NegatedPattern(s, p, o)
            is Pattern.Alts -> AltPattern(s, p, o)
            is Pattern.SimpleAlts -> SimpleAltPattern(s, p, o)
            is Pattern.Sequence -> SequencePattern(s, p, o)
            is Pattern.RepeatingPredicate -> RepeatingPattern(s, p, o)
            is Pattern.UnboundSequence -> UnboundedSequencePattern(s, p, o)
            is Pattern.Binding -> BindingPattern(s, p, o)
        }

    }

}

/* helpers */

private fun Pattern.Object.toSubject(): Pattern.Subject = when (this) {
    is Pattern.GeneratedBinding -> this
    is Pattern.RegularBinding -> this
    is Pattern.Exact -> this
}

private var generatedBindingIndex = 0

private fun generateBinding() = Pattern.GeneratedBinding(id = generatedBindingIndex++)
