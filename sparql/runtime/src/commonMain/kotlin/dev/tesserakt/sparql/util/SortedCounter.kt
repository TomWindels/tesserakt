package dev.tesserakt.sparql.util

import dev.tesserakt.sparql.util.SortedCounter.TreeNode.Companion.nil


/**
 * A special [Counter] implementation that also ensures counted elements are ordered using the [comparator].
 *  Order is not guaranteed between elements for which the provided [comparator] yields 0, and the order between such
 *  elements can change at any time. Backed by a red-black tree for element ordering and linked lists to track elements
 *  with no strict order.
 */
class SortedCounter<T>(
    val comparator: Comparator<in T>
) : Iterable<Map.Entry<T, Int>> {

    /**
     * A single node inside the red-black tree structure. Implements the [Map.Entry] interface to allow iterators to
     *  return the same node instead of having to yield a temporary entry.
     *
     * IMPORTANT: this class does not override the [equals] method, and instead solely relies on referential equality.
     *  Constructing a node that is virtually identical in links & values as another node is therefore not correct!
     */
    private class TreeNode<T> private constructor(
        /**
         * The first node of the linked list associated with this tree node. This node cannot be
         *  the [DataNode.nil] value.
         */
        var data: DataNode<T>,
        var color: Boolean,
    ) {

        // initialised to point to itself, as this is a requirement for the `nil` element that is immediately
        //  constructed upon first use
        // the parent structure is allowed to mutate these pointers at any time (as long as we're not the `nil` node)
        var left = this
        var right = this
        var parent = this

        constructor(
            element: T,
            count: Int,
        ): this(
            data = DataNode(element = element, count = count),
            color = RED
        ) {
            this.left = nil()
            this.right = nil()
            this.parent = nil()
        }

        /**
         * Uses its own links to surrounding nodes to find the next logical node, or [nil]
         */
        fun next(): TreeNode<T> {
            var node = this
            if (!node.right.isNil()) {
                node = node.right
                while (!node.left.isNil()) {
                    node = node.left
                }
                return node
            }
            var parent = node.parent
            while (node === parent.right) {
                node = parent
                parent = parent.parent
            }
            return parent
        }

        /**
         * Uses its own links to surrounding nodes to find the previous logical node, or [nil]
         */
        fun previous(): TreeNode<T> {
            var node = this
            if (!node.left.isNil()) {
                node = node.left
                while (!node.right.isNil()) {
                    node = node.right
                }
                return node
            }
            var parent = node.parent
            while (node === parent.left) {
                node = parent
                parent = node.parent
            }
            return parent
        }

        /**
         * Checks if this [TreeNode] represents the `nil` element
         */
        // as nil element always points to itself, and only itself, a simple reference check using any of our
        //  neighbouring nodes with ourselves will tell us
        inline fun isNil() = left === this

        companion object {

            /**
             * A special tree node, used as a marker instead of a regular `null` value, as it reduces the amount
             *  of checks required through its circular references: checks such as the existence of a node's grandparent
             *  does not require the check to first validate the existence of a parent:
             *  `node -> nil -> nil -> ...` will always yield a `nil` node (= `isNil()` is `true`)
             */
            private val _nil = TreeNode<Nothing?>(DataNode.nil(), BLACK)

            @Suppress("UNCHECKED_CAST")
            inline fun <T> nil(): TreeNode<T> = _nil as TreeNode<T>

        }
    }

    /**
     * A single node in the linked data structure that follows from comparator-based identical values
     */
    private class DataNode<T> private constructor(
        val element: T,
    ) : Map.Entry<T, Int> {

        override val key: T
            get() = element

        override val value: Int
            get() = count

        var count: Int = 0
        var next: DataNode<T> = this

        constructor(
            element: T,
            count: Int,
        ): this(element) {
            this.count = count
            this.next = nil()
        }

        fun isNil() = next === this

        override fun toString() = "$element (x $count)"

        companion object {
            /**
             * A special data node, used as a marker instead of a regular `null` value
             */
            private val _nil = DataNode(null)

            @Suppress("UNCHECKED_CAST")
            inline fun <T> nil(): DataNode<T> = _nil as DataNode<T>
        }

    }

    private var root = nil<T>()

    // various state-related properties; they're private as they make more sense when used in their respective
    //  collections, i.e. `current.size` and `flattened.size`
    /**
     * The number of distinct elements in the counter
     */
    private var size: Int = 0

    /**
     * The total number of inserted items (= sum of all node counts)
     */
    private var count: Int = 0

    fun clear() {
        root = nil()
        size = 0
        count = 0
    }

    operator fun contains(element: T): Boolean {
        return !getDataNode(element).isNil()
    }

    fun first(): Map.Entry<T, Int> {
        if (root.isNil()) throw NoSuchElementException()
        return firstNode().data
    }

    fun last(): Map.Entry<T, Int> {
        if (root.isNil()) throw NoSuchElementException()
        return lastNode().data
    }

    operator fun get(key: T): Int {
        return getDataNode(key).count
    }

    fun increment(element: T, count: Int = 1) {
        // updating the count first, as the element and the tree's state heavily influence on what branch we take
        this.count += count
        // traversing the tree; it's possible the node is already present, and we simply have to increment the
        //  count
        var current = root
        var parent = nil<T>()
        var cmp = 0
        while (!current.isNil()) {
            parent = current
            // as all elements of a given node point to elements that sort the same, using the very first element
            //  of that node suffices
            cmp = comparator.compare(element, current.data.element)
            if (cmp == 0) {
                // we need to work inside this node's linked list, possibly appending a new data node
                var cur = current.data
                while (cur.element != element && !cur.next.isNil()) {
                    cur = cur.next
                }
                if (cur.element == element) {
                    cur.count += count
                } else {
                    // a new node means an increase in element size
                    ++size
                    cur.next = DataNode(element, count)
                }
                return
            } else {
                current = if (cmp > 0) {
                    current.right
                } else /* cmp < 0 */ {
                    current.left
                }
            }
        }
        // we have not found an exact match, but are now at the correct spot to add the new node to
        //  as this means we have to add another node to the tree, the tree size itself grows, so we
        //  also have to update that state
        ++size
        val new = TreeNode(
            element = element,
            count = count,
        )
        new.parent = parent
        // checking if this is our first element, not a lot of balancing required in this case
        if (parent.isNil()) {
            root = new
            return
        }
        if (cmp > 0) {
            parent.right = new
        } else {
            parent.left = new
        }
        // as we added a new node, we need to make sure we're balanced
        onNodeAdded(new)
    }

    fun decrement(element: T, count: Int = 1) {
        // helper to do the actual decrement on the given `dataNode`, returning `true` if the node has to be removed
        //  instead
        fun doDecrement(dataNode: DataNode<T>): Boolean = when {
            dataNode.isNil() || dataNode.count < count -> {
                // we're not actually mutating anything here, so we're not updating the count state either
                throw NoSuchElementException()
            }

            dataNode.count > count -> {
                this.count -= count
                dataNode.count -= count
                false
            }

            else -> {
                this.count -= count
                this.size -= 1
                true
            }
        }

        val treeNode = getNode(element)
        if (treeNode.isNil()) {
            throw NoSuchElementException()
        }
        // traversing the linked list for from our tree node until we find the desired element
        // checking for the special case first, in which there is only one data node and it's the one we target
        if (treeNode.data.element == element) {
            if (doDecrement(treeNode.data)) {
                // the data node seizes to exist if that node has no successor
                if (treeNode.data.next.isNil()) {
                    removeNode(treeNode)
                } else {
                    treeNode.data = treeNode.data.next
                }
            }
            // else... the data node has been adjusted but requires no structural adjustments
        } else if (treeNode.data.next.isNil()) {
            // we have no further elements, and the first one is not a match, so we don't have the requested element
            throw NoSuchElementException()
        } else {
            // we have to look further along in the linked list, and can guarantee the tree node will survive
            var parent = treeNode.data
            var child = parent.next
            while (child.element != element && !child.next.isNil()) {
                parent = child
                child = child.next
            }
            if (child.element != element) {
                throw NoSuchElementException()
            }
            // we can adjust the child - if it ends up being removed, the parent takes ownership of our child's child;
            //  the child itself is dropped from the list
            if (doDecrement(child)) {
                parent.next = child.next
            }
        }
    }

    override fun iterator() = object: Iterator<Map.Entry<T, Int>> {

        private var treeNode = firstNode()
        private var dataNode = treeNode.data

        override fun hasNext(): Boolean {
            return !dataNode.isNil()
        }

        override fun next(): Map.Entry<T, Int> {
            val current = dataNode
            if (current.isNil()) {
                throw NoSuchElementException()
            }
            if (!dataNode.next.isNil()) {
                dataNode = dataNode.next
            } else {
                treeNode = treeNode.next()
                dataNode = treeNode.data
            }
            return current
        }

    }

    /**
     * Returns an iterator that goes through the various elements in this counter in reverse order. This is not
     *  guaranteed to be a 1:1 mirror of [iterator]: elements for which the [comparator] resulted in `0` will not be
     *  reversed.
     */
    fun reversed() = object: Iterator<Map.Entry<T, Int>> {

        private var treeNode = lastNode()
        private var dataNode = treeNode.data

        override fun hasNext(): Boolean {
            return !dataNode.isNil()
        }

        override fun next(): Map.Entry<T, Int> {
            val current = dataNode
            if (current.isNil()) {
                throw NoSuchElementException()
            }
            dataNode = if (!dataNode.next.isNil()) {
                dataNode.next
            } else {
                treeNode = treeNode.previous()
                treeNode.data
            }
            return current
        }

    }

    val current: Set<T> = object: Set<T> {
        override val size: Int
            get() = this@SortedCounter.size

        override fun iterator(): Iterator<T> = object: Iterator<T> {

            private var treeNode = this@SortedCounter.firstNode()
            private var dataNode = treeNode.data

            override fun hasNext(): Boolean {
                return !dataNode.isNil()
            }

            override fun next(): T {
                if (dataNode.isNil()) {
                    throw NoSuchElementException()
                }
                val cur = dataNode
                dataNode = if (!dataNode.next.isNil()) {
                    dataNode.next
                } else {
                    treeNode = treeNode.next()
                    treeNode.data
                }
                return cur.element
            }
        }

        override fun isEmpty(): Boolean {
            return size == 0
        }

        override fun contains(element: T): Boolean {
            return this@SortedCounter[element] > 0
        }

        override fun containsAll(elements: Collection<T>): Boolean {
            // whilst looking it up in bulk is not necessarily a problem, it could result in incorrect results as
            //  the number of times an element occurs in the `elements` collection is not taken into account, and could
            //  thus exceed the number of occurrences in the counter
            throw UnsupportedOperationException()
        }
    }

    val flattened: Collection<T> = object: Collection<T> {
        override val size: Int
            get() = this@SortedCounter.count

        override fun contains(element: T): Boolean {
            return this@SortedCounter[element] > 0
        }

        override fun isEmpty(): Boolean {
            return count == 0
        }

        override fun iterator() = object: Iterator<T> {

            private var treeNode = firstNode()
            private var dataNode = treeNode.data
            private var remaining = dataNode.count

            override fun hasNext(): Boolean {
                return remaining > 0
            }

            override fun next(): T {
                if (remaining <= 0) {
                    throw NoSuchElementException()
                }

                val cur = dataNode
                if (--remaining <= 0) {
                    dataNode = if (!dataNode.next.isNil()) {
                        dataNode.next
                    } else {
                        treeNode = treeNode.next()
                        treeNode.data
                    }
                    remaining = dataNode.count
                }
                return cur.element
            }
        }

        override fun containsAll(elements: Collection<T>): Boolean {
            throw UnsupportedOperationException()
        }
    }

    private fun getNode(key: T): TreeNode<T> {
        var current = root
        while (!current.isNil()) {
            val cmp = comparator.compare(key, current.data.element)
            current = if (cmp == 0) {
                return current
            } else if (cmp > 0) {
                current.right
            } else /* cmp < 0 */ {
                current.left
            }
        }
        return current
    }

    private fun getDataNode(key: T): DataNode<T> {
        val treeNode = getNode(key)
        var dataNode = treeNode.data
        while (dataNode.element != key && !dataNode.next.isNil()) {
            dataNode = dataNode.next
        }
        return dataNode
    }

    private fun firstNode(start: TreeNode<T> = root): TreeNode<T> {
        var node = start
        while (!node.left.isNil()) {
            node = node.left
        }
        return node
    }

    private fun lastNode(start: TreeNode<T> = root): TreeNode<T> {
        var node = start
        while (!node.right.isNil()) {
            node = node.right
        }
        return node
    }

    private fun removeNode(node: TreeNode<T>) {
        // the node has no direct replacements for which the comparator behaves the same (all data nodes are removed),
        //  and thus we intend to remove `node` and its relative position in the tree; depending on its function in the
        //  tree structure, it may make more sense to remove another node instead, and rescue the 'actually-removed'
        //  node's data by storing it into the target node
        val removed: TreeNode<T>
        // the node that is linked to our to-be-removed node, for which it's links have to be altered to remain part
        //  of the structure
        var child: TreeNode<T>
        // using the properties of our target node to find its structural 'role' (leaf, at most one child
        if (node.left.isNil()) {
            // we have at most one child (right link), as the balanced nature of the tree ensures we'd otherwise have two
            //  direct links
            removed = node
            child = node.right
        } else if (node.right.isNil()) {
            // we have exactly one child (left link), as the balanced nature of the tree ensures we'd otherwise have two
            //  direct links
            removed = node
            child = node.left
        } else {
            // the target node has 2 direct children, meaning there may be more nodes further down the chain too
            // we set the target to the node that is the deleted value's most direct predecessor to reduce the
            //  structural impact of node removal, and we 'rescue' its contents by moving it into the other node
            removed = lastNode(node.left)
            child = removed.left
            node.data = removed.data
        }
        // with our target node now known, we can remove all references from it, unlinking it from the structure
        var parent = removed.parent
        if (!child.isNil()) {
            child.parent = parent
        }
        // if our removed node had no parent, and we already ensured the depth after this node is limited, we
        //  can conclude there is at most one more node remaining - the child - which becomes the root node
        if (parent.isNil()) {
            root = child
            return
        }
        if (removed === parent.left) {
            parent.left = child
        } else {
            parent.right = child
        }
        if (!removed.color /* RED */) {
            // no further changes are required as only black node removals require extra work
            return
        }
        // we need to make sure all node paths have an equal amount of black nodes;
        //  if such a node was removed, rebalancing has to be done
        while (child !== root && child.color /* BLACK */) {
            if (child === parent.left) {
                // rebalancing the left side
                var sibling = parent.right
                if (!sibling.color /* RED */) {
                    sibling.color = BLACK
                    parent.color = RED
                    rotateLeft(parent)
                    sibling = parent.right
                }
                if (sibling.left.color && sibling.right.color /* both are BLACK */) {
                    sibling.color = RED
                    child = parent
                    parent = parent.parent
                } else {
                    if (sibling.right.color /* BLACK */) {
                        sibling.left.color = BLACK
                        sibling.color = RED
                        rotateRight(sibling)
                        sibling = parent.right
                    }
                    sibling.color = parent.color
                    parent.color = BLACK
                    sibling.right.color = BLACK
                    rotateLeft(parent)
                    child = root
                }
            } else {
                // rebalancing the right side; same code with left & right (rotations and links) mirrored
                var sibling = parent.left
                if (!sibling.color /* RED */) {
                    sibling.color = BLACK
                    parent.color = RED
                    rotateRight(parent)
                    sibling = parent.left
                }
                if (sibling.right.color && sibling.left.color /* both are BLACK */) {
                    sibling.color = RED
                    child = parent
                    parent = parent.parent
                } else {
                    if (sibling.left.color /* BLACK */) {
                        sibling.right.color = BLACK
                        sibling.color = RED
                        rotateLeft(sibling)
                        sibling = parent.left
                    }
                    sibling.color = parent.color
                    parent.color = BLACK
                    sibling.left.color = BLACK
                    rotateRight(parent)
                    child = root
                }
            }
        }
        child.color = BLACK
    }

    private fun onNodeAdded(node: TreeNode<T>) {
        var node = node
        // structural changes are required while:
        // * our current parent is red,
        // * our node is two levels deep (node.parent.parent is not nil)
        while (!node.parent.color /* RED */ && !node.parent.parent.isNil()) {
            if (node.parent === node.parent.parent.left) {
                val uncle = node.parent.parent.right
                // it may be nil, with black as color
                if (!uncle.color /* RED */) {
                    node.parent.color = BLACK
                    uncle.color = BLACK
                    uncle.parent.color = RED
                    node = uncle.parent
                } else {
                    if (node === node.parent.right) {
                        node = node.parent
                        rotateLeft(node)
                    }
                    node.parent.color = BLACK
                    node.parent.parent.color = RED
                    rotateRight(node.parent.parent)
                }
            } else {
                // the same logic as above, but applied to the other side of the tree
                val uncle = node.parent.parent.left
                // it may be nil, with black as color
                if (!uncle.color /* RED */) {
                    node.parent.color = BLACK
                    uncle.color = BLACK
                    uncle.parent.color = RED
                    node = uncle.parent
                } else {
                    if (node === node.parent.left) {
                        node = node.parent
                        rotateRight(node)
                    }
                    node.parent.color = BLACK
                    node.parent.parent.color = RED
                    rotateLeft(node.parent.parent)
                }
            }
        }
        root.color = BLACK
    }

    private fun rotateLeft(node: TreeNode<T>) {
        val child = node.right
        node.right = child.left
        if (!child.left.isNil()) {
            child.left.parent = node
        }
        child.parent = node.parent
        if (!node.parent.isNil()) {
            if (node === node.parent.left) {
                node.parent.left = child
            } else {
                node.parent.right = child
            }
        } else {
            // the original node didn't have a parent, meaning
            //  it was the root node
            root = child
        }
        child.left = node
        node.parent = child
    }

    private fun rotateRight(node: TreeNode<T>) {
        val child = node.left
        node.left = child.right
        if (!child.right.isNil()) {
            child.right.parent = node
        }
        child.parent = node.parent
        if (!node.parent.isNil()) {
            if (node === node.parent.right) {
                node.parent.right = child
            } else {
                node.parent.left = child
            }
        } else {
            // the original node didn't have a parent, meaning
            //  it was the root node
            root = child
        }
        child.right = node
        node.parent = child
    }

//    fun structureAsString(): String {
//
//        fun format(node: DataNode<T>) = buildString {
//            append(node)
//            var node = node.next
//            while (!node.isNil()) {
//                append(" -> ")
//                append(node)
//                node = node.next
//            }
//        }
//
//        fun format(node: TreeNode<T>): String {
//            if (node.isNil()) {
//                return ""
//            }
//            return buildString {
//                val base = format(node.data)
//                if (!node.right.isNil()) {
//                    format(node.right).lineSequence().forEach { line ->
//                        append(" ".repeat(base.length))
//                        append('|')
//                        appendLine(line)
//                    }
//                }
//                append(base)
//                if (!node.left.isNil()) {
//                    format(node.left).lineSequence().forEach { line ->
//                        appendLine()
//                        append(" ".repeat(base.length))
//                        append('|')
//                        append(line)
//                    }
//                }
//            }
//        }
//        return format(root)
//    }

    companion object {

        private const val RED = false
        private const val BLACK = true

        operator fun <T: Comparable<T>> invoke(): SortedCounter<T> {
            return SortedCounter(comparator = Comparator { a, b -> a.compareTo(b) })
        }

    }
}
