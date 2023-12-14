package tesserakt.sparql.runtime.patterns

import tesserakt.rdf.types.Triple
import tesserakt.sparql.runtime.types.Bindings
import tesserakt.util.addFront

// TODO: make helper method for when repeating with a specific term as s & o, making those additional regular rules
//  (should be equivalent)
internal data class RepeatingRule(
    // the intended start element
    private val s: Element.Binding,
    // the repeating element
    // TODO: make two variants of this rule type: one with repeating any non-binding predicate, and one for only binding
    //  predicate
    private val p: Element.Exact,
    // the intended final result
    private val o: Element.Binding,
    // TODO: make a different rule type depending on the value of this boolean
    private val optional: Boolean
    // TODO make the DT for the bind predicate a map, key being the bound predicate
) : QueryRule<RepeatingRule.Connections>() {

    /** Values extracted from a triple with matching predicate **/
    data class Segment(
        val start: Triple.Term,
        val end:   Triple.Term
    ) {
        override fun toString() = "$start -> $end"
    }

    class Connections {

        private val segments = mutableListOf<Segment>()
        // all path variations from the segments above (paths and its individual subsections)
        // important: not a set, as double exact paths should also double the amount of results!
        private val variations = mutableListOf<Segment>()

        fun getAllPaths(): List<Segment> {
            return variations
        }

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
                // growing these latest variations again until they can't grow anymore
                extending = extending.flatMap { b -> segments.filter { s -> s.end == b.start } }
            }
            // doing the same in the other direction
            extending = segments.filter { s -> s.start == segment.start }
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

    fun expand(input: List<Bindings>, data: Connections): List<Bindings> {
        val variations = data.getAllPaths()
        return if (optional) {
            input.flatMap { bindings ->
                val start = bindings[s.name]
                val end = bindings[o.name]
                when {
                    start != null && end != null -> {
                        // counting the amount of paths lead up to our required start - to - end destination
                        val count = variations
                            .count { s -> s.start == start && s.end == end }
                        // resulting `count` instances of the same binding, no additional data required
                        List(count) { bindings }
                    }
                    start != null -> {
                        variations
                            .filter { it.start == start }
                            .map { it.asBindings() + bindings }
                            // adding a null-length relation, meaning end == start
                            .addFront(bindings + (o.name to start))
                    }
                    end != null -> {
                        variations
                            .filter { it.end == end }
                            .map { it.asBindings() + bindings }
                            // adding a null-length relation, meaning end == start
                            .addFront(bindings + (s.name to end))
                    }
                    else -> {
                        variations
                            .map { it.asBindings() + bindings }
                    }
                }
            }
        } else {
            input.flatMap { bindings ->
                val start = bindings[s.name]
                val end = bindings[o.name]
                when {
                    start != null && end != null -> {
                        // counting the amount of paths lead up to our required start - to - end destination
                        val count = variations
                            .count { s -> s.start == start && s.end == end }
                        // resulting `count` instances of the same binding, no additional data required
                        List(count) { bindings }
                    }
                    start != null -> {
                        variations
                            .filter { it.start == start }
                            .map { it.asBindings() + bindings }
                    }
                    end != null -> {
                        variations
                            .filter { it.end == end }
                            .map { it.asBindings() + bindings }
                    }
                    else -> {
                        variations
                            .map { it.asBindings() + bindings }
                    }
                }
            }
        }
    }

    fun insertAndReturnNewPaths(triple: Triple, data: Connections): List<Bindings> {
        val segment = process(triple) ?: return emptyList()
        val paths = data.getAllNewPathsAndInsert(segment)
        return paths.map { it.asBindings() }
    }

    fun quickInsert(triple: Triple, data: Connections) {
        val segment = process(triple) ?: return
        // the returned list isn't used by the callee, but is still necessary to generate so `getAllPaths()` functions
        //  properly
        data.getAllNewPathsAndInsert(segment)
    }

    override fun newState(): Connections = Connections()

    /**
     * Processes the incoming triple, returns `null` if no match is found, otherwise a connection object
     *  containing a fixed/bound/open start/end
     */
    private fun process(triple: Triple): Segment? {
        if (p.term != triple.p) {
            return null
        }
        val entry = Segment(
            start = triple.s,
            end = triple.o
        )
        return entry
    }

    private fun Segment.asBindings() = mapOf(s.name to start, o.name to end)

}
