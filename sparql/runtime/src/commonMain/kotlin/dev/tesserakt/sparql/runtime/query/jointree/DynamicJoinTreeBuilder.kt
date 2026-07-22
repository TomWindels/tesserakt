package dev.tesserakt.sparql.runtime.query.jointree

import dev.tesserakt.sparql.runtime.evaluation.BindingIdentifierSet
import dev.tesserakt.sparql.runtime.query.MutableJoinState
import dev.tesserakt.sparql.runtime.query.jointree.DynamicJoinTree.Node

internal object DynamicJoinTreeBuilder {

    /**
     * Produces a tree structure, returning its root node, that contains all provided [states] joined together using
     *  properties of the individual join states in the collection.
     */
    fun build(
        states: List<MutableJoinState>,
        prioritizedBindings: BindingIdentifierSet = BindingIdentifierSet.EMPTY,
    ): Node {
        // creating a set of groups, starting with every state set as a leaf node
        val groups = states.mapTo(ArrayList(states.size)) { TreeSegment.leaf(it) }
        // as long as not all groups have been merged into one, we find the best match pair to join together
        while (groups.size > 2) {
            val matches = findGroupMatch(groups, prioritizedBindings)
            val a = groups.removeAt(matches.group2)
            val b = groups.removeAt(matches.group1)

            // if the new group have internal binding overlap, having their combination cached is beneficial as the
            //  number of results obtained here are not the result of a cartesian join;
            //  otherwise, falling back on the indexes of the leafs themselves is as performant
            val segment = if (a.getCommonBindingsCount(b) > 0) {
                // getting all bindings found in the other groups, and intersecting these with the individual bindings
                //  found in this group
                val indexes = (1 ..< groups.size)
                    .fold(groups[0].bindings) { bindings, i -> bindings + groups[i].bindings }
                    .intersect(a.bindings + b.bindings)
                TreeSegment.connected(a, b, indexes)
            } else {
                TreeSegment.disconnected(a, b)
            }

            groups.add(segment)
        }
        return if (groups.size == 2) {
            // creating a temporary TreeSegment instance, so the two final groups are also properly configured
            //  indexing-wise
            TreeSegment.disconnected(groups[0], groups[1]).node
        } else {
            groups.single().node
        }
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

            fun connected(
                first: TreeSegment,
                second: TreeSegment,
                bindings: BindingIdentifierSet,
            ) = TreeSegment(
                node = Node.Connected(first.node, second.node, bindings),
                length = first.length + second.length,
            ).also {
                // requesting the child nodes to rehash themselves based on common bindings
                val common = first.node.bindings.intersect(second.node.bindings)
                first.node.reindex(common)
                second.node.reindex(common)
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
        val prioritizedCount: Int,
    ) : Comparable<IntermediateMatchResult> {

        constructor(a: TreeSegment, b: TreeSegment, prioritizedBindings: BindingIdentifierSet): this(
            common = a.getCommonBindingsCount(b),
            total = a.getTotalBindingsCount(b),
            length = a.getTotalLength(b),
            prioritizedCount = a.bindings.intersectSize(prioritizedBindings) + b.bindings.intersectSize(prioritizedBindings)
        )

        override fun compareTo(other: IntermediateMatchResult): Int {
            // if there's a mismatch in the number of prioritized bindings, the largest one takes priority
            if (prioritizedCount > other.prioritizedCount) {
                return 1
            } else if (prioritizedCount < other.prioritizedCount) {
                return -1
            }
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
        prioritizedBindings: BindingIdentifierSet,
    ): MatchResult {
        require(groups.size > 1)

        val allResults = (0 ..< groups.size - 1).map { i ->
            val left = groups[i]
            var j = i + 1
            var bestMatchResult = IntermediateMatchResult(left, groups[j], prioritizedBindings)
            for (k in i + 2 ..< groups.size) {
                val right = groups[k]
                val current = IntermediateMatchResult(left, right, prioritizedBindings)
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
