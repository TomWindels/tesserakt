package dev.tesserakt.sparql.runtime.query.jointree

import dev.tesserakt.sparql.runtime.evaluation.QueryContext
import dev.tesserakt.sparql.runtime.query.MutableJoinState
import dev.tesserakt.sparql.runtime.query.jointree.DynamicJoinTree.Node

internal object DynamicJoinTreeBuilder {

    fun <J: MutableJoinState> build(context: QueryContext, states: List<J>): Node<J> {
        // creating a set of groups, starting with every state set as a leaf node
        val groups = states.mapTo(ArrayList(states.size)) { TreeSegment.leaf(it) }
        // as long as not all groups have been merged into one, we find the best match pair to join together
        while (groups.size > 2) {
            val matches = findGroupMatch(groups)
            val a = groups.removeAt(matches.group2)
            val b = groups.removeAt(matches.group1)

            // getting all bindings found in the other groups, and intersecting these with the individual bindings
            //  found in this group
            val indices = groups.flatMapTo(mutableSetOf()) { it.bindings }.apply {
                retainAll(a.bindings + b.bindings)
            }

            val segment = if (indices.isNotEmpty()) {
                TreeSegment.connected(context, a, b, indices)
            } else {
                TreeSegment.disconnected(context, a, b)
            }

            groups.add(segment)
        }
        return if (groups.size == 2) {
            Node.Disconnected(context, groups[0].node, groups[1].node)
        } else {
            groups.single().node
        }
    }

    class TreeSegment<J : MutableJoinState> private constructor(
        /**
         * The underlying node representing this segment of the tree
         */
        val node: Node<J>,
        /**
         * The node "length". Leaf nodes have length 1, a (dis)connected node with two leaf nodes have length 2, etc.
         *  Used in calculating the selectivity, so longer chains of leaf nodes are preferred, as these are expected to
         *  generate fewer intermediate results.
         */
        private val length: Int,
    ) {

        val bindings: Set<String> get() = node.bindings

        /**
         * Calculates a "score" representing how much data is estimated to go through; multiple segments with matching
         *  binding overlap should prefer matching with the node having lower selectivity.
         */
        fun peekSelectivity(other: TreeSegment<J>): Double {
            // a higher binding count matches with more possible values, e.g. leaf nodes `?s ?p ?o` and `?s a <Type>`
            //  should prefer matching with the latter if only `?s` binding matches, which can be achieved through
            //  an adjusted value for selectivity
            return unionSize(bindings, other.bindings).toDouble() / (length + other.length)
        }

        companion object {

            fun <J: MutableJoinState> leaf(leaf: J) = TreeSegment(
                node = Node.Leaf(leaf),
                length = 1,
            )

            fun <J: MutableJoinState> leaf(leaf: Node.Leaf<J>) = TreeSegment(
                node = leaf,
                length = 1,
            )

            fun <J: MutableJoinState> connected(
                context: QueryContext,
                first: TreeSegment<J>,
                second: TreeSegment<J>,
                bindings: Collection<String>
            ) = TreeSegment(
                node = Node.Connected(context, first.node, second.node, bindings),
                length = first.length + second.length,
            )

            fun <J: MutableJoinState> disconnected(
                context: QueryContext,
                first: TreeSegment<J>,
                second: TreeSegment<J>,
            ) = TreeSegment(
                node = Node.Disconnected(context, first.node, second.node),
                length = first.length + second.length,
            )

            /* helpers */

            private inline fun unionSize(left: Set<*>, right: Set<*>): Int {
                return if (left.size < right.size) {
                    right.size + left.count { it !in right }
                } else {
                    left.size + right.count { it !in left }
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

    private fun <J: MutableJoinState> findGroupMatch(
        groups: List<TreeSegment<J>>
    ): MatchResult {
        require(groups.size > 1)

        var group1 = 0
        var group2 = 1
        var currentSelectivity = groups[group1].peekSelectivity(groups[group2])

        for (candidate2 in 1 ..< groups.size) {
            val candidateSelectivity = groups[group1].peekSelectivity(groups[candidate2])
            if (candidateSelectivity < currentSelectivity) {
                group2 = candidate2
                currentSelectivity = candidateSelectivity
            }
        }

        for (candidate1 in 1 ..< groups.size) {
            for (candidate2 in candidate1 + 1 ..< groups.size) {
                val candidateSelectivity = groups[candidate1].peekSelectivity(groups[candidate2])
                if (candidateSelectivity < currentSelectivity) {
                    group1 = candidate1
                    group2 = candidate2
                    currentSelectivity = candidateSelectivity
                }
            }
        }

        return MatchResult(
            group1 = group1,
            group2 = group2,
        )
    }

}
