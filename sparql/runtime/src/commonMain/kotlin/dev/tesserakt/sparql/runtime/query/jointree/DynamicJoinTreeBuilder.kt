package dev.tesserakt.sparql.runtime.query.jointree

import dev.tesserakt.sparql.runtime.evaluation.BindingIdentifierSet
import dev.tesserakt.sparql.runtime.evaluation.context.QueryContext
import dev.tesserakt.sparql.runtime.query.FilterExpression
import dev.tesserakt.sparql.runtime.query.MutableJoinState
import dev.tesserakt.sparql.runtime.query.jointree.DynamicJoinTree.Node
import dev.tesserakt.sparql.util.Counter
import dev.tesserakt.sparql.util.ZeroCardinality
import dev.tesserakt.util.removeLastElement

internal object DynamicJoinTreeBuilder {

    /**
     * Produces a tree structure, returning its root node, that contains all provided [states] joined together using
     *  properties of the individual join states in the collection.
     * The various [filters] are applied in the tree at the most appropriate stages, grouping [states] where possible
     *  to improve performance.
     */
    fun build(
        context: QueryContext,
        states: List<MutableJoinState>,
        filters: List<FilterExpression>,
    ): Node {
        val states = states.mapTo(mutableListOf()) { state -> TreeSegment.leaf(state) }
        if (filters.isEmpty()) {
            return build(context, states, filters).node
        }
        // we need to apply filters that span across multiple states, as they cannot be passed down fully to any single
        //  state because of their requirements
        val combinedBindings = states.bindings()
        // we associate all of our spanning filters with their exact "dependencies", so we can form the most optimal
        //  set to join on first
        val spanningFilters = mutableMapOf<BindingIdentifierSet, ArrayList<FilterExpression>>()
        filters.forEach { filter ->
            val isApplicable = filter.bindings in combinedBindings
            // we do not possess all necessary bindings in a fully combined state, so there's no point passing it down
            //  anywhere
            if (!isApplicable) {
                return@forEach
            }
            // if any of our leaf states already contains all necessary bindings, that state is the one responsible
            //  for ensuring it emits no filter-violating results
            val isSpanning = states.none { state -> filter.bindings.unionSize(state.bindings) == filter.bindings.size }
            if (!isSpanning) {
                return@forEach
            }
            spanningFilters
                .getOrPut(filter.bindings) { arrayListOf() }
                .add(filter)
        }
        if (spanningFilters.isEmpty()) {
            // regular join tree construction possible
            return build(context, states, filters).node
        }
        // we have spanning filters we need to satisfy;
        // we apply the filters, starting from those with the 'smallest' requirements first, generating subtrees
        //  that replace the states they contain
        val bindingOccurrences = Counter<Int>() // binding identifiers
        spanningFilters.forEach { (bindings, filters) ->
            bindings.asIntIterable().forEach { bindingId ->
                bindingOccurrences.increment(bindingId, filters.size)
            }
        }
        val filterOrder = mutableListOf<BindingIdentifierSet>()
        val remaining = spanningFilters.keys.toMutableSet()
        while (remaining.isNotEmpty()) {
            val next = remaining.maxWith { a, b ->
                // the value with the highest average 'score' per binding is the next filter 'type' we want to
                //  process, so the more 'popular' bindings are pushed as low as possible
                // we also bias it towards bindings that have already been put in the 'to be processed' order,
                //  so we don't introduce new subtrees if it can be avoided
                val scoreA = a
                    .asIntIterable()
                    .sumOf { bindingId -> bindingOccurrences[bindingId] }.toDouble().div(a.size)
                    .plus(a.asIntIterable().count { id -> filterOrder.any { id in it } }.toDouble())
                val scoreB = b
                    .asIntIterable()
                    .sumOf { bindingId -> bindingOccurrences[bindingId] }.toDouble().div(b.size)
                    .plus(b.asIntIterable().count { id -> filterOrder.any { id in it } }.toDouble())
                scoreA.compareTo(scoreB)
            }
            filterOrder.add(next)
            remaining.remove(next)
        }
        // we now combine the filters, using our declared order, into new subtrees, that then apply these filters
        //  immediately
        filterOrder.forEach { filterBindings ->
            val filters = spanningFilters[filterBindings]
                // this should not happen, but we skip it just in case
                ?: return@forEach

            val patternIndexes = findOptimalGroup(
                target = filterBindings,
                states = states,
            ) ?: return@forEach // no path available, so would require a cartesian join -> we skip it
            // we consume all states referenced in the pattern index set, converting it into a larger, joined state
            val result = (1 ..< patternIndexes.size).fold(initial = states[patternIndexes[0]]) { segment, index ->
                val patternIndex = patternIndexes[index]
                val newSegment = states[patternIndex]
                val nextNewSegment = patternIndexes
                    .getOrNull(index + 1)
                    ?.let { states[it] }
                TreeSegment.join(
                    context = context,
                    first = segment,
                    second = newSegment,
                    // if we're at the end, we set the empty state, as we do not yet know what to index with
                    externalBindings = nextNewSegment?.bindings ?: BindingIdentifierSet.EMPTY,
                    // it should be correct to keep this list empty until the very last iteration
                    filters = filters,
                )
            }

            // before we insert the result, we remove all states we just consumed, as these are now (indirectly)
            //  accessible through our new resulting subtree
            if (patternIndexes.size == states.size) {
                // special case: we used all states to form our 'subtree' (not really a subtree anymore)
                return result.node
            } else {
                // as we don't know the exact order of elements, we do a clear and swap remove pass instead
                var j = states.size - 1
                patternIndexes.forEach { i ->
                    while (j in patternIndexes) {
                        --j
                    }
                    check(j >= 0)
                    states[i] = states[j]
                    --j
                }
                // all N elements at the end should now be duplicates, so we can safely remove these
                repeat(patternIndexes.size) {
                    states.removeLastElement()
                }
            }

            // we can now safely set our subtree as a valid state to use
            states.add(result)
        }

        // we now have a more complex hierarchy of subtrees and unused states we can build to our final tree
        return build(context, states, filters).node
    }

    /**
     * Finds a (sub)set of [TreeSegment] [states] that, when combined, result in mappings that contain at least
     *  the [target] binding set, whilst ensuring all inner states are connected (when evaluated in the suggested order).
     *
     * The return value represents a set of all required [states]' indices. The optimal join order is an estimate,
     *  as it creates no actual connected nodes that join the underlying states together, and is thus done without
     *  intermediate cardinalities.
     */
    private fun findOptimalGroup(
        target: BindingIdentifierSet,
        states: List<TreeSegment>,
    ): IntArray? {
        // base case: there are no additional bindings required
        if (target == BindingIdentifierSet.EMPTY) {
            return intArrayOf()
        }
        // we group all available states based on the bindings they 'provide', keeping the ones with the smallest
        //  cardinality
        val lut = states
            .withIndex()
            .groupingBy { it.value.bindings }
            .reduce { _, min, current ->
                if (current.value.node.cardinality < min.value.node.cardinality) {
                    current
                } else {
                    min
                }
            }
            .mapValues { it.value.index }

        // if we have multiple paths forward: we prioritize based on substantial differences in cardinality, followed
        //  by result length (less intermediate states), and finally by marginal differences in cardinality
        val comparator = Comparator<IntArray> { solutionA, solutionB ->
            val cardinalityA = solutionA.fold(ZeroCardinality) { c, index -> c + states[index].node.cardinality }
            val cardinalityB = solutionB.fold(ZeroCardinality) { c, index -> c + states[index].node.cardinality }
            if (cardinalityA.value < 0.5 * cardinalityB.value) {
                // we prefer solution A
                return@Comparator -1
            }
            if (cardinalityA.value * 0.5 > cardinalityB.value) {
                // we prefer solution B
                return@Comparator 1
            }
            if (solutionA.size < solutionB.size) {
                // we prefer solution A
                return@Comparator -1
            }
            if (solutionA.size > solutionB.size) {
                // we prefer solution B
                return@Comparator 1
            }
            cardinalityA.compareTo(cardinalityB)
        }

        // now we only need to evaluate the available states with distinct binding set they 'provide'
        // we check recursively to find the option that requires the least amount of states
        fun recurse(
            /**
             * The available bindings to join onto, used to ensure there is at least one binding overlap between
             *  the prior iteration(s) and the next suggested state
             */
            available: BindingIdentifierSet,
        ): IntArray? {
            val missing = target - available
            // base case - we have all bindings we require
            if (missing == BindingIdentifierSet.EMPTY) {
                return intArrayOf()
            }
            // if we have a state available that matches it directly and has at least one binding in common,
            //  we have the best possible solution
            val solutionAvailable = lut.keys.any { set ->
                set.intersectSize(available) != 0 && set.intersectSize(missing) == missing.size
            }
            if (solutionAvailable) {
                // we take the index that matches our requirement and has the lowest cardinality
                return intArrayOf(
                    lut
                        .filter { (set, _) -> set.intersectSize(available) != 0 && set.intersectSize(missing) == missing.size }
                        .values
                        .minBy { index -> states[index].node.cardinality }
                )
            }
            // we need to recurse deeper; we currently have no idea how many extra states we require to get to the end
            // there's no point in trying states that currently have no binding overlap (as that voids the contract),
            // nor if they don't introduce any new bindings to those we have 'discovered'
            val contenders = lut.filter { (set, _) ->
                val overlap = set.intersectSize(available)
                overlap != 0 && overlap < set.size
            }
            if (contenders.isEmpty()) {
                // no path forward, no connection possible
                return null
            }
            // we recurse, advancing using our contenders
            val solutions = contenders.mapNotNull { (set, id) ->
                val solution = recurse(
                    available = available + set,
                ) ?: return@mapNotNull null
                val result = IntArray(1 + solution.size)
                result[0] = id
                repeat(solution.size) { i ->
                    result[i + 1] = solution[i]
                }
                result
            }
            if (solutions.isEmpty()) {
                return null
            }
            if (solutions.size == 1) {
                return solutions[0]
            }
            return solutions.minWith(comparator)
        }
        // sending of the first version, in which we create one with every possible state to start with that is valid
        val solutions = lut.mapNotNull { (set, id) ->
            val solution = recurse(
                available = set,
            ) ?: return@mapNotNull null
            val result = IntArray(1 + solution.size)
            result[0] = id
            repeat(solution.size) { i ->
                result[i + 1] = solution[i]
            }
            result
        }
        if (solutions.isEmpty()) {
            return null
        }
        if (solutions.size == 1) {
            return solutions[0]
        }
        return solutions.minWith(comparator)
    }

    /**
     * Reduces all individual [TreeSegment]s listed in the [groups] collection to a single [TreeSegment],
     *  applying [filters] to connected segments where applicable
     */
    private fun build(
        context: QueryContext,
        groups: MutableList<TreeSegment>,
        filters: List<FilterExpression>,
    ): TreeSegment {
        // as long as not all groups have been merged into one, we find the best match pair to join together
        while (groups.size > 2) {
            val matches = findGroupMatch(groups)
            val a = groups.removeAt(matches.group2)
            val b = groups.removeAt(matches.group1)

            val segment = TreeSegment.join(
                context = context,
                first = a,
                second = b,
                // all other groups are 'external' to this segment, meaning that it can reasonably expect incoming
                //  mappings to have binding values associated with those defined in these sections
                externalBindings = groups.bindings(),
                // the `TreeSegment::join()` logic only retains the filters applicable to this node
                filters = filters,
            )

            groups.add(segment)
        }
        return if (groups.size == 2) {
            TreeSegment.join(
                context = context,
                first = groups[0],
                second = groups[1],
                filters = filters,
                // we set no initial index bindings as we may be the root element of a query that requires
                //  no specific values to join on;
                // if this changes, the owner of this (sub)tree can always call `reindex`
                externalBindings = BindingIdentifierSet.EMPTY,
            )
        } else {
            groups.single()
        }
    }

    private fun Iterable<TreeSegment>.bindings(): BindingIdentifierSet {
        val iter = iterator()
        if (!iter.hasNext()) {
            return BindingIdentifierSet.EMPTY
        }
        var r = iter.next().bindings
        while (iter.hasNext()) {
            r += iter.next().bindings
        }
        return r
    }

    class TreeSegment private constructor(
        /**
         * The underlying node representing this segment of the tree
         */
        val node: Node,
        /**
         * The node "length". Leaf nodes have length 1, a (dis)connected node with two leaf nodes have length 2, etc.
         *  Used in calculating the selectivity, so longer chains of leaf nodes are preferred, as these are expected to
         *  generate fewer intermediate results.
         */
        private val length: Int,
    ) {

        val bindings: BindingIdentifierSet get() = node.bindings

        fun getTotalBindingsCount(other: TreeSegment) =
            unionSize(bindings, other.bindings)

        fun getCommonBindingsCount(other: TreeSegment) =
            bindings.intersectSize(other.bindings)

        fun getTotalLength(other: TreeSegment) =
            length + other.length

        override fun toString(): String = "TreeNode(node: $node)"

        companion object {

            fun leaf(leaf: MutableJoinState) = TreeSegment(
                node = Node.Leaf(leaf),
                length = 1,
            )

            /**
             * Creates the most appropriate tree segment type based on the [first] and [second] segments' properties,
             *  possibly applying [filters] on top of it.
             * Uses the provided [externalBindings] to configure appropriate bindings to index on if applicable.
             */
            fun join(
                context: QueryContext,
                first: TreeSegment,
                second: TreeSegment,
                externalBindings: BindingIdentifierSet,
                filters: List<FilterExpression>,
            ): TreeSegment {
                // we *have* to use a connected segment if there are any filters that need to be applied, otherwise
                //  if the new group have internal binding overlap, having their combination cached is beneficial as the
                //  number of results obtained here are not the result of a cartesian join;
                //  otherwise, falling back on the indexes of the leafs themselves is as performant
                val filters = filters.filter { filter ->
                    // we only need to apply filters to our new segment if
                    // * we contain all bindings required by this filter to evaluate
                    // * neither of the children can (and do) evaluate the filter on their own
                    filter.bindings in (first.bindings + second.bindings) &&
                    filter.bindings.intersectSize(first.bindings) in 1 ..< filter.bindings.size &&
                    filter.bindings.intersectSize(second.bindings) in 1 ..< filter.bindings.size
                }
                return if (filters.isNotEmpty() || first.bindings.intersectSize(second.bindings) != 0) {
                    // we index on the bindings found in either first and/or second that are also available 'externally',
                    //  as we expect incoming mappings to join on that have these external bindings set
                    val indexes = externalBindings
                        .intersect(first.bindings + second.bindings)
                    connected(
                        context = context,
                        first = first,
                        second = second,
                        indexes = indexes,
                        filters = filters,
                    )
                } else {
                    disconnected(
                        first = first,
                        second = second,
                    )
                }
            }

            fun connected(
                context: QueryContext,
                first: TreeSegment,
                second: TreeSegment,
                /**
                 * The bindings to index on. It is recommended this contains all bindings that are joined on by the
                 *  parent state
                 */
                indexes: BindingIdentifierSet,
                /**
                 * Set of filters to apply after joining the two states together
                 */
                filters: List<FilterExpression>,
            ): TreeSegment {
                // requesting the child nodes to rehash themselves based on common bindings
                val common = first.node.bindings.intersect(second.node.bindings)
                first.node.reindex(common)
                second.node.reindex(common)
                // followed by construction of the connecting segment
                return TreeSegment(
                    node = Node.Connected(
                        context = context,
                        left = first.node,
                        right = second.node,
                        indexes = indexes,
                        filters = filters,
                    ),
                    length = first.length + second.length,
                )
            }

            fun disconnected(
                first: TreeSegment,
                second: TreeSegment,
            ) = TreeSegment(
                node = Node.Disconnected(first.node, second.node),
                length = first.length + second.length,
            ).also {
                // requesting the child nodes to rehash themselves based on common bindings
                val common = first.node.bindings.intersect(second.node.bindings)
                first.node.reindex(common)
                second.node.reindex(common)
            }

            /* helpers */

            private inline fun unionSize(left: BindingIdentifierSet, right: BindingIdentifierSet): Int {
                return if (left.size < right.size) {
                    right.size + left.asIntIterable().count { it !in right }
                } else {
                    left.size + right.asIntIterable().count { it !in left }
                }
            }

        }

    }

    private data class MatchResult(
        // the smaller index of the two
        val group1: Int,
        // the bigger index of the two
        val group2: Int,
    )

    /**
     * A comparable intermediate result type, comparing two [TreeSegment]s, storing intermediate statistics between them,
     *  making comparison for the best match possible (larger = better match)
     */
    private data class IntermediateMatchResult(
        val common: Int,
        val total: Int,
        val length: Int,
    ) : Comparable<IntermediateMatchResult> {

        constructor(a: TreeSegment, b: TreeSegment): this(
            common = a.getCommonBindingsCount(b),
            total = a.getTotalBindingsCount(b),
            length = a.getTotalLength(b),
        )

        override fun compareTo(other: IntermediateMatchResult): Int {
            // we prefer common bindings first
            if (common > other.common) {
                return 1
            } else if (common < other.common) {
                return -1
            }
            // next, we prefer longer segments, as longer segments require more data to
            //  create results
            if (length > other.length) {
                return 1
            } else if (length < other.length) {
                return -1
            }
            // we prefer lower amount of total bindings next, as fewer bindings in total
            //  means less data is likely to match
            return other.total - total
        }
    }

    private fun findGroupMatch(
        groups: List<TreeSegment>,
    ): MatchResult {
        require(groups.size > 1)

        val allResults = (0 ..< groups.size - 1).map { i ->
            val left = groups[i]
            var j = i + 1
            var bestMatchResult = IntermediateMatchResult(left, groups[j])
            for (k in i + 2 ..< groups.size) {
                val right = groups[k]
                val current = IntermediateMatchResult(left, right)
                if (current > bestMatchResult) {
                    bestMatchResult = current
                    j = k
                }
            }
            (i to j) to bestMatchResult
        }
        val best = allResults.maxBy { it.second }.first
        return MatchResult(
            group1 = best.first,
            group2 = best.second,
        )
    }

}
