package dev.tesserakt.util

internal class CommonPrefixStringPoolImpl(
    private val minNodeValueLength: Int = 5,
): CommonPrefixStringPool {

    private class Node(
        parent: Node?,
        value: String,
        // sorted using `backing.value[0]`
        var children: MutableList<Node>? = null,
    ) {

        // the backing structure used to only retain the various values and relevant tree section required for a
        //  complete value reconstruction
        class Backing(
            var parent: Backing?,
            // as it's possible for the hierarchy to change, parts of the value may move to the parent node over time
            //  requiring this to be mutable
            var value: String,
        ) : CommonPrefixStringPool.Handle {

            override fun retrieve(): String {
                var current: Backing? = parent ?: return value
                // retrieving all string segments first, as we have to append them in reverse order
                val collection = mutableListOf<String>()
                while (current != null) {
                    collection.add(current.value)
                    current = current.parent
                }
                return buildString {
                    collection.asReversed().forEach { append(it) }
                    append(value)
                }
            }

        }

        // the single backing instance we control; we keep a direct reference to it at all times as it's possible for
        //  the tree structure to mutate based on the
        val backing: Backing

        var value: String
            get() = backing.value
            set(value) { backing.value = value }

        init {
            // identical to writing it inline, but the linter is not too happy with that
            backing = Backing(
                parent = parent?.backing,
                value = value,
            )
        }

        override fun toString() = "`$value`"

    }

    private var root: Node? = null

    init {
        require(minNodeValueLength > 0) { "The minimum node length should be no smaller than 1, but was $minNodeValueLength" }
    }

    override fun createHandle(value: String): CommonPrefixStringPool.Handle {
        var node = root ?: run {
            val new = Node(parent = null, value = value, children = mutableListOf())
            root = new
            return new.backing
        }
        // we have to traverse the tree, starting from our current node, until reaching a point where we can insert
        //  (or find) the node that builds up to the passed-in value
        var valueIndex = 0
        // we have to cover the first case in which the root node
        node.match(
            refValue = value,
            startIndex = 0,
            onIncompatible = { nodeValueIndex ->
                // a split has to occur here, meaning the root node will have to change into a split node
                val splitNodeChildren = mutableListOf<Node>()
                // this becomes the new root, so no parent set
                val newRoot = Node(
                    parent = null,
                    value = node.value.substring(0, nodeValueIndex),
                    children = splitNodeChildren,
                )
                // we now also create a new child for the split node
                val newChild = Node(
                    parent = newRoot,
                    // the remainder of the value, without what is contained by the new split node
                    value = value.substring(/* valueIndex (= 0) +  */ nodeValueIndex),
                    // leaf
                    children = null,
                )
                // with it configured, we can set it as the root value
                root = newRoot
                // now we can transform the existing node to have this split node as its parent
                node.backing.parent = newRoot.backing
                node.value = node.value.substring(nodeValueIndex)
                // finally, we set this updated node and the newly created child as children of the split node;
                //  we can simply compare their first character value
                if (node.value[0] < newChild.value[0]) {
                    splitNodeChildren.add(node)
                    splitNodeChildren.add(newChild)
                } else {
                    splitNodeChildren.add(newChild)
                    splitNodeChildren.add(node)
                }
                // this new child also becomes the leaf we're looking for
                return newChild.backing
            },
            onExactMatch = {
                // special case where the root node itself has the exact value we need
                return node.backing
            },
            onNodeValueEnded = {
                // no node splitting required; we can continue with the tree traversal knowing the root node does
                //  not have to change
                valueIndex += node.value.length
            },
            onRefValueEnded = {
                // we have to update the root node here using a split node of the existing value, with the current
                //  root set as its direct child
                val newRoot = Node(
                    parent = null,
                    value = value,
                    children = mutableListOf(node),
                )
                // this original node (root) has to reduce its value's size
                node.value = node.value.substring(value.length)
                // moving the root over
                root = newRoot
                return newRoot.backing
            },
        )
        // with the `node` value guaranteed to be traversed and matching value-wise with the target value, we can
        //  continue going deeper into the tree knowing the root node doesn't change past this point
        while (true) {
            node.findChildNode(
                refValue = value,
                startIndex = valueIndex,
                onExactMatchFound = { child ->
                    // no further tree changes required, this is the node we're looking for
                    return child.backing
                },
                onFullMatchFound = { child ->
                    // we shift the value over for the entire existing child's value length
                    valueIndex += child.value.length
                    // and go deeper in the tree
                    node = child
                    // we continue iterating
                },
                onPartialMatchFound = { child, childIndex, childValueIndex ->
                    // the childValueIndex is guaranteed to be >= minNodeValueLength, meaning the child we retrieved can be
                    //  split into two nodes, with the original child being one of the new node's children, and a
                    //  new value node being the other
                    // we have to split the tree at this point, using a new node, containing a new child for the value
                    //  we're inserting
                    val splitNodeChildren = mutableListOf<Node>()
                    val splitNode = Node(
                        parent = node,
                        value = child.value.substring(0, childValueIndex),
                        children = splitNodeChildren,
                    )
                    // we now also create a new child for the split node
                    val newChild = Node(
                        parent = splitNode,
                        // the remainder of the value, without what is contained by the new split node
                        value = value.substring(valueIndex + childValueIndex),
                        // leaf
                        children = null,
                    )
                    // we replace the direct reference of the parent node from the original child node to the split node
                    //  if not root, or set the split node as the root node
                    node.children!![childIndex] = splitNode
                    // now we can transform the existing node to have this split node as its parent
                    child.backing.parent = splitNode.backing
                    child.value = child.value.substring(childValueIndex)
                    // finally, we set this updated node and the newly created child as children of the split node;
                    //  we can simply compare their first character value
                    if (child.value[0] < newChild.value[0]) {
                        splitNodeChildren.add(child)
                        splitNodeChildren.add(newChild)
                    } else {
                        splitNodeChildren.add(newChild)
                        splitNodeChildren.add(child)
                    }
                    // this new child also becomes the leaf we're looking for
                    return newChild.backing
                },
                onRefValueEnded = { child, childIndex ->
                    // we have to introduce a new split node again, but only so there's a handle mid-way through
                    //  the tree
                    val splitNode = Node(
                        parent = node,
                        value = child.value.substring(0, value.length - valueIndex),
                        children = mutableListOf(child)
                    )
                    // we have to update the original node's parent too
                    node.children!![childIndex] = splitNode
                    // we have to update the original node too
                    child.value = child.value.substring(value.length - valueIndex)
                    child.backing.parent = splitNode.backing
                    return splitNode.backing
                },
                onNoResult = { targetChildIndex ->
                    val newChild = Node(
                        parent = node,
                        // the remainder of the value
                        value = value.substring(valueIndex),
                        // leaf
                        children = null,
                    )
                    // also inserting this new child so it can be discovered for subsequent tree updates
                    val children = node.children
                    if (children != null) {
                        children.add(targetChildIndex, newChild)
                    } else {
                        check(targetChildIndex == 0)
                        node.children = mutableListOf(newChild)
                    }
                    // it's the leaf we're looking for as we've automatically set its value in accordance
                    return newChild.backing
                },
            )
        }
    }

    override fun toString(): String {
        fun nodeToString(node: Node, prefix: String): String = buildString {
            val children = node.children
            if (children == null || children.isEmpty()) {
                return "$prefix`${node.value}`"
            }
            val self = "$prefix`${node.value}`-|"
            val mid = children.size / 2
            repeat(mid) {
                val text = nodeToString(children[it], " ".repeat(self.length - 1) + "┌ ")
                appendLine(text)
            }
            appendLine(self)
            repeat(children.size - mid - 1) {
                val i = it + mid
                val text = nodeToString(children[i], " ".repeat(self.length - 1) + "└ ")
                appendLine(text)
            }
            append(nodeToString(children.last(), " ".repeat(self.length - 1) + "└ "))
        }
        return nodeToString(root ?: return "<empty>", "")
    }

    /* helpers */

    /**
     * Searches the [Node.children] of this [Node] to find its child for which the provided [refValue] (starting
     *  at [startIndex]) can be used, issuing exactly one of these callbacks depending on the scenario:
     *
     * @param onExactMatchFound called upon encountering a child node for which its entire value matches the remainder of
     *  the [refValue]
     * @param onFullMatchFound called upon encountering a child node for which its value is at least [minNodeValueLength]
     *  long and its entire value fits inside the remainder of the [refValue]
     * @param onPartialMatchFound called upon encountering a child node with a mismatching value at an index exceeding
     *  [minNodeValueLength], signaling the need for a split node. The two integer parameters represent the child's
     *  index in this [Node]s [Node.children] list, whilst the second represents the index at which the values mismatch.
     * @param onRefValueEnded called upon encountering a child node for which its value overlaps entirely with the
     *  remainder of the [refValue], with this node exceeding the content and having enough content left over of itself
     *  to become a smaller child node
     * @param onNoResult called when no children are found matching any of the scenarios above, with an index
     *  that can be used to insert a new child into if applicable (node value is long enough to be 'parent-worthy')
     */
    private inline fun Node.findChildNode(
        refValue: String,
        startIndex: Int,
        onExactMatchFound: (Node) -> Unit,
        onFullMatchFound: (Node) -> Unit,
        onPartialMatchFound: (Node, Int, Int) -> Unit,
        onRefValueEnded: (Node, Int) -> Unit,
        onNoResult: (Int) -> Unit,
    ) {
        val children = children
        if (children.isNullOrEmpty()) {
            onNoResult(0)
            return
        }
        val c = refValue[startIndex]
        var currentChildIndex = run {
            // only sorted based on the first char, no other sorting has been done
            var s = children.binarySearch { it.value[0].compareTo(c) }
            if (s < 0) {
                onNoResult(-s-1)
                return
            }
            // s is a valid index, but not necessarily the first one, so we put it back until it is
            while (s > 0 && children[s - 1].value[0] == c) {
                --s
            }
            s
        }
        // it's possible for the remainder of the value itself to be too short in any configuration, meaning
        //  we're better off giving it its own separate node
        if (refValue.length - startIndex < minNodeValueLength) {
            // we want it inserted at this child index, as it's possible we're at the very start here
            onNoResult(currentChildIndex)
            return
        }
        // we can now loop for every child that matches on a first character
        while (currentChildIndex < children.size && children[currentChildIndex].value[0] == c) {
            val child = children[currentChildIndex]
            child.match(
                refValue = refValue,
                startIndex = startIndex,
                onIncompatible = { nodeValueIndex ->
                    // ensuring that, when a split would be issued through this callback:
                    if (
                    // the first half of the node, which is guaranteed to be a parent, is long enough (minNodeValueLength)
                        nodeValueIndex >= minNodeValueLength &&
                        // the second half of the node, which may be a parent, is long enough to be a parent
                        (child.children.isNullOrEmpty() || child.value.length - nodeValueIndex >= minNodeValueLength)
                    ) {
                        onPartialMatchFound(child, currentChildIndex, nodeValueIndex)
                        return
                    }
                    // else, this node is not good enough to split of off, it would introduce a node that is too small
                    //  in a structural position where that is not allowed
                },
                onExactMatch = { onExactMatchFound(child) },
                onNodeValueEnded = {
                    if (child.value.length >= minNodeValueLength) {
                        onFullMatchFound(child)
                        return
                    }
                    // else, this node is not good enough to split of off
                },
                onRefValueEnded = {
                    // we already know that the value ref is long enough, so we can use the callback directly
                    onRefValueEnded(child, currentChildIndex)
                    return
                },
            )
            ++currentChildIndex
        }
        // if we reached the end of the loop, there's no child that is applicable anymore;
        // we had at least one child that was valid (we're now one past that index), meaning we
        //  can point the callback to that spot
        onNoResult(currentChildIndex - 1)
    }

    /**
     * Checks if this [Node] matches the provided [refValue] starting from the given [startIndex].
     *
     * @param refValue the value to check against
     * @param startIndex the index to start from (in the [refValue] only)
     * @param onIncompatible the callback invoked upon encountering a character mismatch; returns the index used in the
     *  node value (the ref value index can be obtained by adding [startIndex] to this value)
     * @param onExactMatch the callback invoked upon encountering an exact match
     */
    private inline fun Node.match(
        refValue: String,
        startIndex: Int,
        onIncompatible: (Int) -> Unit,
        onExactMatch: () -> Unit,
        onNodeValueEnded: () -> Unit,
        onRefValueEnded: () -> Unit,
    ) {
        var nodeValueIndex = 0
        var refValueIndex = startIndex
        while (nodeValueIndex < backing.value.length && refValueIndex < refValue.length) {
            if (backing.value[nodeValueIndex] != refValue[refValueIndex]) {
                onIncompatible(nodeValueIndex)
                return
            }
            ++nodeValueIndex
            ++refValueIndex
        }
        if (refValue.length - startIndex == backing.value.length) {
            onExactMatch()
            return
        }
        if (nodeValueIndex == backing.value.length) {
            onNodeValueEnded()
            return
        }
        onRefValueEnded()
    }

}
