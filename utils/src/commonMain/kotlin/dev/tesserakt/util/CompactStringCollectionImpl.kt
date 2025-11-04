package dev.tesserakt.util

internal class CompactStringCollectionImpl: CompactStringCollection {

    private class Node(
        parent: Node?,
        value: String,
        // sorted using `backing.value`
        val children: MutableList<Node>
    ) {

        // the backing structure used to only retain the various values and relevant tree section required for a
        //  complete value reconstruction
        class Backing(
            var parent: Backing?,
            // as it's possible for the hierarchy to change, parts of the value may move to the parent node over time
            //  requiring this to be mutable
            var value: String,
        ) : CompactStringCollection.Handle {

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

    override fun add(value: String): CompactStringCollection.Handle {
        var node = root ?: run {
            val new = Node(parent = null, value = value, children = mutableListOf())
            root = new
            return new.backing
        }
        // we need to track this node's parent separately; `null` meaning it has no parent (as we're looking at the root)
        var nodeParent: Node? = null
        // four possible scenarios
        // * the node we're currently pointing at is identical with the remaining value to add, meaning it's already
        //  present and its handle can be reused (`onExactMatch`)
        // * the node we want to insert into is compatible with the new value, meaning we have to look at one of its
        //  children (`onNodeValueEnded`)
        // * the node we want to insert into is not compatible with the new value, meaning we have to create a new
        //  in-between 'split' node that is a parent of both the incompatible node and the new child (`onIncompatible`)
        // * the remaining value we want to insert is shorter than the currently considered node, meaning we have
        //  to create a 'split' node to have a handle that terminates quicker in the tree, and the original node can
        //  branch of off (`onRefValueEnded`)
        var valueIndex = 0
        while (true) {
            node.match(
                refValue = value,
                startIndex = valueIndex,
                onExactMatch = {
                    // we don't have to add anything, we already found the node we're looking for
                    return node.backing
                },
                onNodeValueEnded = {
                    // we know we're compatible, so we can move the value index the entire node value's length
                    valueIndex += node.value.length
                    // we know the valueIndex is not out of bounds, so we can now find the next node safely
                    val c = value[valueIndex]
                    val childIndex = node.children.binarySearch { childNode ->
                        childNode.value[0].compareTo(c)
                    }
                    // it's possible for the childIndex to be negative, i.e. equal to `- insertion point - 1`, in which
                    //  case no child exist with a matching start character, creating it here
                    if (childIndex >= 0) {
                        // continuing our search
                        nodeParent = node
                        node = node.children[childIndex]
                    } else {
                        val newChild = Node(
                            parent = node,
                            // the remainder of the value
                            value = value.substring(valueIndex),
                            // leaf
                            children = mutableListOf()
                        )
                        // also inserting this new child so it can be discovered for subsequent tree updates
                        val targetIndex = - childIndex - 1
                        node.children.add(targetIndex, newChild)
                        // it's the leaf we're looking for as we've automatically set its value in accordance
                        return newChild.backing
                    }
                },
                onIncompatible = { nodeIndex ->
                    // we have to split the tree at this point, using a new node, containing a new child for the value
                    //  we're inserting
                    val splitNode = Node(
                        parent = nodeParent,
                        value = node.value.substring(0, nodeIndex),
                        children = mutableListOf()
                    )
                    // we now also create a new child for the split node
                    val newChild = Node(
                        parent = splitNode,
                        // the remainder of the value, without what is contained by the new split node
                        value = value.substring(valueIndex + nodeIndex),
                        // leaf
                        children = mutableListOf()
                    )
                    // we replace the direct reference of the parent node from the original child node to the split node
                    //  if not root, or set the split node as the root node
                    nodeParent?.let { parent ->
                        val c = splitNode.value[0]
                        val i = parent.children.binarySearch { childNode ->
                            childNode.value[0].compareTo(c)
                        }
                        check(i >= 0) { "Failed to find child `${splitNode.value}${node.value}` in parent ${parent} with children ${parent.children.joinToString()}. Structure:\n$this" }
                        parent.children[i] = splitNode
                    } ?: run {
                        root = splitNode
                    }
                    // now we can transform the existing node to have this split node as its parent
                    node.backing.parent = splitNode.backing
                    node.value = node.value.substring(nodeIndex)
                    // finally, we set this updated node and the newly created child as children of the split node;
                    //  we can simply compare their first character value
                    splitNode.children.add(node)
                    splitNode.children.add(newChild)
                    splitNode.children.sortBy { it.value.first() }
                    // this new child also becomes the leaf we're looking for
                    return newChild.backing
                },
                onRefValueEnded = {
                    // we have to introduce a new split node again, but only so there's a handle mid-way through
                    //  the tree
                    val splitNode = Node(
                        parent = nodeParent,
                        value = node.value.substring(0, value.length - valueIndex),
                        children = mutableListOf(node)
                    )
                    // we have to update the original node's parent too
                    nodeParent?.let { parent ->
                        val c = splitNode.value[0]
                        val i = parent.children.binarySearch { childNode ->
                            childNode.value[0].compareTo(c)
                        }
                        check(i >= 0)
                        parent.children[i] = splitNode
                    } ?: run {
                        root = splitNode
                    }
                    // we have to update the original node too
                    node.value = node.value.substring(value.length - valueIndex)
                    node.backing.parent = splitNode.backing
                    return splitNode.backing
                },
            )
        }
    }

    override fun toString(): String {
        fun nodeToString(node: Node, prefix: String): String = buildString {
            val children = node.children
            if (children.isEmpty()) {
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
