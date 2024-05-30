package dev.tesserakt.sparql.runtime.incremental.patterns.rules.repeating

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.runtime.incremental.patterns.rules.QueryRule
import dev.tesserakt.sparql.runtime.common.types.Bindings
import dev.tesserakt.sparql.runtime.incremental.types.Pattern

// TODO: make helper method for when repeating with a specific term as s & o, making those additional regular rules
//  (should be equivalent)
internal abstract class RepeatingRule<DT: Any>(
    // the intended start element
    protected val s: Pattern.Binding,
    // the intended final result
    protected val o: Pattern.Binding,
    // TODO make the DT for the bind predicate a map, key being the bound predicate
) : QueryRule<DT>() {

    class Connections {

        data class Segment(
            val start: Quad.Term,
            val end:   Quad.Term
        ) {
            override fun toString() = "$start -> $end"
        }

        private val segments = mutableListOf<Segment>()
        // all path variations from the segments above (paths and its individual subsections)
        // important: not a set, as double exact paths should also double the amount of results!
        private val variations = mutableListOf<Segment>()

        fun getAllPaths() = variations

        fun countAllConnectionsBetween(from: Quad.Term, to: Quad.Term): Int =
            variations.count { it.start == from && it.end == to }

        // TODO: later, the return type can safely be altered to just contain all end points
        fun getAllPathsStartingFrom(term: Quad.Term): List<Segment> =
            variations.filter { it.start == term }

        // TODO: later, the return type can safely be altered to just contain all starting points
        fun getAllPathsEndingAt(term: Quad.Term): List<Segment> =
            variations.filter { it.end == term }

        /**
         * Creates a list of all segments that become valid by inserting this triple, and inserts the triple
         * 4 cases are considered:
         *  * completely unconnected      -> 1 binding (start and end from this triple)
         *  * only the end is connected   -> N bindings, only starting with the start from this triple, endings from
         *                                   the binding and chain
         *  * only the start is connected -> M bindings, starting with the start of the chain until (&incl) the triple,
         *                                   only ending with the end of the triple
         *  * both endpoints join a path  -> N + M bindings, starting with the start of the chain until (&incl) the
         *                                   triple, ending with the end of the triple and the parts after that from
         *                                   the end chain
         */
        fun getAllNewPathsAndInsert(segment: Segment): List<Segment> {
            val result = mutableListOf(segment)
            // finding all segments that end with this one and creating new variations from it
            var extending = segments.filter { s -> s.end == segment.start }
            while (extending.isNotEmpty()) {
                // the combination of these segments before it and extended with this segment can now be inserted as new
                //  variations
                result.addAll(
                    extending.map { Segment(start = it.start, end = segment.end) }
                )
                // all these starting points found in `extending` also now have to connect with everything that follows;
                //  => doing this here
                // continuations starts with all extensions of the provided `segment`, and grows from there
                var continuations = List(extending.size) { segments.filter { s -> s.start == segment.end } }
                while (continuations.any { it.isNotEmpty() }) {
                    val t = continuations.flatMapIndexed { i, c ->
                        val start = extending[i].start
                        c.map { Segment(start = start, end = it.end) }
                    }
                    result.addAll(t)
                    continuations = continuations.map { b -> b.flatMap { segments.filter { s -> s.start == it.end } } }
                }

                // growing these latest variations again until they can't grow anymore
                extending = extending.flatMap { b -> segments.filter { s -> s.end == b.start } }
            }
            // doing the same in the other direction
            extending = segments.filter { s -> s.start == segment.end }
            while (extending.isNotEmpty()) {
                // the combination of these segments after it and extended with this segment can now be inserted as new
                //  variations
                result.addAll(
                    extending.map { Segment(start = segment.start, end = it.end) }
                )
                // growing these latest variations again until they can't grow anymore
                extending = extending.flatMap { b -> segments.filter { s -> s.start == b.end } }
            }
            // inserting all new variations into the entire collection of variations for easier reuse
            variations += result
            segments += segment
            return result
        }

        override fun toString() = segments.joinToString()

    }

    abstract fun expand(input: List<Bindings>, data: DT): List<Bindings>

    protected fun Connections.Segment.asBindings() = mapOf(s.name to start, o.name to end)

    companion object {

        fun repeatingOf(
            s: Pattern.Subject,
            p: Pattern.RepeatingPredicate,
            o: Pattern.Object
        ): RepeatingRule<*> = when {
            s is Pattern.Binding && o is Pattern.Binding -> when (p) {
                is Pattern.ZeroOrMoreBound -> ZeroOrMoreBindingPredicateRule(s, p.predicate, o)
                is Pattern.ZeroOrMoreFixed -> ZeroOrMoreFixedPredicateRule(s, p.predicate, o)
                is Pattern.OneOrMoreBound -> OneOrMoreBindingPredicateRule(s, p.predicate, o)
                is Pattern.OneOrMoreFixed -> OneOrMoreFixedPredicateRule(s, p.predicate, o)
            }
            else -> throw UnsupportedOperationException("Using fixed s/o terms in repeating rules is currently unsupported!")
        }

    }

}
