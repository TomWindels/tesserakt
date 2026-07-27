package dev.tesserakt.sparql.runtime.query.jointree

import dev.tesserakt.sparql.runtime.evaluation.BindingIdentifierSet
import dev.tesserakt.sparql.runtime.evaluation.context.QueryContext
import dev.tesserakt.sparql.runtime.query.FilterExpression
import dev.tesserakt.sparql.runtime.query.MutableJoinState
import dev.tesserakt.sparql.runtime.query.jointree.DynamicJoinTree.Node
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
        // TODO:::
        //  - rework join tree construction, changing use of prioritized bindings to be from filters, but only if there's
        //    binding overlap between children required to get a filter going; otherwise, cardinality estimates already
        //    tell the whole story


        // TODO prioritized bindings again
        val prioritizedBindings = BindingIdentifierSet.EMPTY

        val states = states.mapTo(mutableListOf()) { state -> TreeSegment.leaf(state) }
        if (prioritizedBindings.isEmpty()) {
            return build(context, states, filters).node
        }
        // we create subtrees for all states containing prioritized bindings
        val currentStates = mutableListOf<TreeSegment>()
        val subtrees = mutableListOf<TreeSegment>()
        prioritizedBindings.asIntIterable().forEach { bindingId ->
            var i = states.size
            currentStates.clear()
            while (i > 0) {
                i -= 1
                if (bindingId in states[i].bindings) {
                    // we claim this state for this subtree
                    currentStates.add(states.removeAt(i))
                }
            }
            // we test our subtrees too
            i = subtrees.size
            while (i > 0) {
                i -= 1
                if (bindingId in subtrees[i].bindings) {
                    // we now contain the subtree as our own
                    currentStates.add(subtrees.removeAt(i))
                }
            }
            // with our current set of states that need to be joined grouped together, we can create our subtree
            subtrees.add(build(context, currentStates, filters))
        }
        // next, we want to create the shortest possible paths between these subtrees, where possible
        loop@ while (subtrees.size > 1) {
            // we combine these subtrees using the shortest path possible
            repeat(subtrees.size) { i ->
                val left = subtrees[i]
                repeat(subtrees.size - i - 1) { j ->
                    val j = i + j + 1
                    val right = subtrees[j]
                    val connected = connect(context, left, right, states, filters)
                    if (connected != null) {
                        // we now have less states to deal with, with the two subtrees being properly connected
                        subtrees.removeAt(i)
                        subtrees.removeAt(j - 1)
                        subtrees.add(connected)
                        // starting back from the top
                        continue@loop
                    }
                }
            }
            // we found no paths between any subtrees, so we have to introduce a cartesian join here
            subtrees.add(
                TreeSegment.disconnected(
                    first = subtrees.removeLastElement(),
                    second = subtrees.removeLastElement(),
                )
            )
            // going back up top
        }
        // it's possible our shortest path consumed all elements already, meaning we can short circuit here
        if (states.isEmpty()) {
            // keeping old behaviour after having constructed the tree: we keep a disconnected node at the root
            return subtrees[0].node
        }
        // we have some nodes remaining, so we combine our subtrees with our remaining leaf nodes to get the total root
        //  node set up
        // the algorithm should be biased towards pushing our subtrees further back (deep) as we have more bindings in
        //  these segments
        currentStates.clear()
        currentStates.addAll(states)
        currentStates.addAll(subtrees)
        return build(context, currentStates, filters).node
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

    /**
     * Attempts to connect [left] and [right] using [Node.Connected] instances that form the shortest path, using a
     *  subset of the provided [paths] elements. If this is not possible (no fully connected path can be formed),
     *  `null` is returned instead. The [paths] list is also updated, removing all elements that were used to
     *  create this shortest path.
     *
     * Note that if multiple paths of equal length are possible, the result prioritizes introducing less 'new' bindings,
     *  but may have inconsistent results with different order of [paths] elements.
     */
    private fun connect(
        context: QueryContext,
        left: TreeSegment,
        right: TreeSegment,
        paths: MutableList<TreeSegment>,
        filters: List<FilterExpression>,
    ): TreeSegment? {
        // ideal case: there is already binding overlap, no extra path required
        if (left.bindings.intersectSize(right.bindings) != 0) {
            // considering we used none of the remaining paths, their bindings can be considered external
            return TreeSegment.join(
                context = context,
                first = left,
                second = right,
                externalBindings = paths.bindings(),
                filters = filters,
            )
        }
        var i = 0
        // FIXME don't use tree segments at this stage! will do eager joining in case of connected
        //  segments (which will always be the case!!!!) meaning that it will evaluate A LOT
        //  instead, use a shallow version that then transforms 1:1 in connected segments after the fact
        var extended = listOf((left to right) to emptySet<Int>())
        while (i < paths.size) {
            extended = extended.flatMap { (segments, consumed) ->
                paths.mapIndexedNotNull { i, path ->
                    // if the path is already used in this solution, we can't follow it further
                    if (i in consumed) {
                        return@mapIndexedNotNull null
                    }
                    if (
                        // we need to meaningfully connect with this path segment: there is at least 1 binding in common
                        segments.first.bindings.intersectSize(path.bindings) != 0 &&
                        // a path is not a logical continuation of our segment if it doesn't change the status quo in
                        //  terms of 'encountered bindings'
                        segments.first.bindings.size < segments.first.bindings.unionSize(path.bindings)
                    ) {
                        // only retaining the bindings found in paths not yet consumed
                        val externalBindings = paths.foldIndexed(BindingIdentifierSet.EMPTY) { j, set, path ->
                            if (i == j || j in consumed) {
                                set
                            } else {
                                set + path.bindings
                            }
                        }
                        (TreeSegment.join(context, segments.first, path, externalBindings, filters) to segments.second) to consumed + i
                    } else if (
                        // we need to meaningfully connect with this path segment: there is at least 1 binding in common
                        segments.second.bindings.intersectSize(path.bindings) != 0 &&
                        // a path is not a logical continuation of our segment if it doesn't change the status quo in
                        //  terms of 'encountered bindings'
                        segments.second.bindings.size < segments.second.bindings.unionSize(path.bindings)
                    ) {
                        // only retaining the bindings found in paths not yet consumed
                        val externalBindings = paths.foldIndexed(BindingIdentifierSet.EMPTY) { j, set, path ->
                            if (i == j || j in consumed) {
                                set
                            } else {
                                set + path.bindings
                            }
                        }
                        (segments.first to TreeSegment.join(context, segments.second, path, externalBindings, filters)) to consumed + i
                    } else {
                        // can't use this path
                        null
                    }
                }
            }
            // we check whether any have a connection between left and right, as that would be a valid (connected)
            // result
            if (extended.any { (segments, _) -> segments.first.bindings.intersectSize(segments.second.bindings) != 0 }) {
                // if multiple satisfy this requirement, we take the one with the smallest amount of paths
                //  taken (lowest set of used path instances), followed by the smallest number of total bindings of the
                //  solution
                val valid = extended
                    .filter { (segments, _) -> segments.first.bindings.intersectSize(segments.second.bindings) != 0 }
                val solution = if (valid.size == 1) {
                    valid.single()
                } else {
                    val smallestDistance = valid.minOf { (_, paths) -> paths.size }
                    val smallest = valid.filter { (_, paths) -> paths.size == smallestDistance }
                    if (smallest.size == 1) {
                        smallest.single()
                    } else {
                        smallest.minBy { (segments, _) ->
                            // union size
                            (segments.first.bindings + segments.second.bindings).size
                        }
                    }
                }
                val externalBindings = paths.foldIndexed(BindingIdentifierSet.EMPTY) { j, set, path ->
                    // `i` is already included in the 'consumed' set at this point
                    if (j in solution.second) {
                        set
                    } else {
                        set + path.bindings
                    }
                }
                val segment = TreeSegment.join(
                    context = context,
                    first = solution.first.first,
                    second = solution.first.second,
                    externalBindings = externalBindings,
                    filters = filters,
                )
                // we consume the paths that lead to this solution as well
                // we do so in reverse order so the indexes are valid (and less copying is required)
                solution.second.sortedDescending().forEach { pathIndex ->
                    paths.removeAt(pathIndex)
                }
                return segment
            }
            // we did not find a solution, we grow our existing attempts if we have more paths to 'consume'
            ++i
        }
        // we ended up with no valid solution, so we can assume these two segments will never connect
        return null
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

        override fun toString(): String = "TreeNode(${bindings.size} binding(s), length=${length})"

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
                    // * at least one of our children contains at least one of the filter bindings, but
                    //  not all (meaning that the filter is not applied to that child already)
                    filter.bindings in (first.bindings + second.bindings) && (
                        filter.bindings.intersectSize(first.bindings) in 1 ..< filter.bindings.size ||
                        filter.bindings.intersectSize(second.bindings) in 1 ..< filter.bindings.size
                    )
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
