
import dev.tesserakt.sparql.runtime.evaluation.BindingIdentifierSet
import dev.tesserakt.sparql.runtime.evaluation.DataDelta
import dev.tesserakt.sparql.runtime.evaluation.MappingDelta
import dev.tesserakt.sparql.runtime.evaluation.context.GlobalQueryContext
import dev.tesserakt.sparql.runtime.query.MutableJoinState
import dev.tesserakt.sparql.runtime.query.jointree.DynamicJoinTree
import dev.tesserakt.sparql.runtime.query.jointree.DynamicJoinTreeBuilder
import dev.tesserakt.sparql.util.Cardinality
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.test.*

class DynamicJoinTreeBuilderTest {

    class TestNode(override val bindings: Set<String>): MutableJoinState {

        constructor(bindings: List<String>): this(bindings = bindings.toSet())

        var indexes = bindings
            private set

        override val cardinality: Cardinality
            get() = throw UnsupportedOperationException()

        override fun rehash(bindings: BindingIdentifierSet) {
            indexes = bindings.asIntIterable().mapTo(mutableSetOf()) { binding ->
                GlobalQueryContext.resolveBinding(binding)
            }
        }

        override fun join(delta: MappingDelta) = throw UnsupportedOperationException()

        override fun peek(delta: DataDelta) = throw UnsupportedOperationException()

        override fun process(delta: DataDelta) = throw UnsupportedOperationException()

        override fun toString() = "Node(bindings=${bindings}, indexes=${indexes})"

    }

    @Test
    fun basic() = test (
        listOf("a", "b"),
        listOf("b"),
    ) { root ->
        // the root node has to be disconnected, as caching the very last step is not useful
        root.assertIsDisconnected()
    }

    @Test
    fun small() = test(
        listOf("a", "b"),
        listOf("b"),
        listOf("b", "c"),
    ) { root ->
        // ensuring correct root node type
        root.assertIsDisconnected()
        assertTrue {
            root.left is DynamicJoinTree.Node.Connected<TestNode> ||
            root.right is DynamicJoinTree.Node.Connected<TestNode>
        }
    }

    @Test
    fun chain() = test(
        listOf("1", "2"),
        listOf("2", "3"),
        listOf("3", "4"),
        listOf("4", "5"),
        listOf("5", "6"),
    ) { root ->
        // ensuring correct root node type
        root.assertIsDisconnected()
        // a deep-left/right join tree should be constructed; all nodes should have one leaf and one connected node,
        // until reaching the end
        var sibling = root.single<DynamicJoinTree.Node.Leaf<TestNode>>()
        var subtree = root.single<DynamicJoinTree.Node.Connected<TestNode>>()
        repeat(2) {
            // making sure its indexed on its common pair
            subtree.assertIsConnected()
            val activeIndex = subtree.buf.indexes.asIntIterable().single()
            val possibleIndexes = sibling.state.bindings
            assertContains(possibleIndexes, GlobalQueryContext.resolveBinding(activeIndex))
            // moving to the next check
            sibling = subtree.single<DynamicJoinTree.Node.Leaf<TestNode>>()
            subtree = subtree.single<DynamicJoinTree.Node.Connected<TestNode>>()
        }
        // the last subtree should have both children as leaf nodes
        subtree.both<DynamicJoinTree.Node.Leaf<TestNode>>()
    }

    @Test
    fun mix1() = test(
        // chain segments
        listOf("1", "2"),
        listOf("2", "3"),
        // star segments
        listOf("0", "1"),
        listOf("0", "2"),
        listOf("0", "3"),
        // unrelated segments
        listOf("0"),
        listOf("1"),
    ) { root ->
        // ensuring correct root node type
        root.assertIsDisconnected()
        // there is always at least one binding overlap between segments, so no cartesian joins are expected
        var sibling = root.single<DynamicJoinTree.Node.Leaf<TestNode>>()
        var subtree = root.single<DynamicJoinTree.Node.Connected<TestNode>>()
        repeat(4) {
            // making sure its indexed on its common pair
            subtree.assertIsConnected()
            val activeIndexes = subtree.buf.indexes.asIntIterable()
            if (!activeIndexes.iterator().hasNext()) {
                fail("Expected at least one active index, got none!")
            }
            val possibleIndexes = sibling.state.bindings
            assertContainsAll(possibleIndexes, activeIndexes.map { GlobalQueryContext.resolveBinding(it) })
            // moving to the next check
            sibling = subtree.single<DynamicJoinTree.Node.Leaf<TestNode>>()
            subtree = subtree.single<DynamicJoinTree.Node.Connected<TestNode>>()
        }
    }

    @Test
    fun mix2() = test(
        // chain segments
        listOf("1", "2"),
        listOf("2", "3"),
        // reverse star segments
        listOf("1", "0"),
        listOf("2", "0"),
        listOf("3", "0"),
        // unrelated segments
        listOf("0"),
        listOf("1"),
    ) { root ->
        // ensuring correct root node type
        root.assertIsDisconnected()
        // there is always at least one binding overlap between segments, so no cartesian joins are expected
        var sibling = root.single<DynamicJoinTree.Node.Leaf<TestNode>>()
        var subtree = root.single<DynamicJoinTree.Node.Connected<TestNode>>()
        repeat(4) {
            // making sure its indexed on its common pair
            subtree.assertIsConnected()
            val activeIndexes = subtree.buf.indexes.asIntIterable()
            if (!activeIndexes.iterator().hasNext()) {
                fail("Expected at least one active index, got none!")
            }
            val possibleIndexes = sibling.state.bindings
            assertContainsAll(possibleIndexes, activeIndexes.map { GlobalQueryContext.resolveBinding(it) })
            // moving to the next check
            sibling = subtree.single<DynamicJoinTree.Node.Leaf<TestNode>>()
            subtree = subtree.single<DynamicJoinTree.Node.Connected<TestNode>>()
        }
    }

    @Test
    fun disconnected() = test(
        // chain 1 segments
        listOf("1a", "2a"),
        listOf("2a", "3a"),
        // chain 2 segments
        listOf("1b", "2b"),
        listOf("2b", "3b"),
    ) { root ->
        val one = setOf("2a")
        val two = setOf("2b")
        // we expect two connected segments with its leafs configured for joining on the common binding,
        //  the buffers to have no indexing of its own (as there's no common binding with the structure just outside it),
        //  and these two joined by a disconnected one as its root
        root.assertIsDisconnected()
        root.left.assertIsConnected()
        assertTrue("No indexes were expected!") { root.left.buf.indexes.size == 0 }
        root.right.assertIsConnected()
        assertTrue("No indexes were expected!") { root.right.buf.indexes.size == 0 }

        root.left.left.assertIsLeaf()
        assertTrue { root.left.left.state.indexes == one || root.left.left.state.indexes == two }
        root.left.right.assertIsLeaf()
        assertTrue { root.left.right.state.indexes == one || root.left.right.state.indexes == two }
        root.right.left.assertIsLeaf()
        assertTrue { root.right.left.state.indexes == one || root.right.left.state.indexes == two }
        root.right.right.assertIsLeaf()
        assertTrue { root.right.right.state.indexes == one || root.right.right.state.indexes == two }
    }

    @Test
    fun chainWithCartesian() = test(
        // regular chain
        listOf("1", "2"),
        listOf("2", "3"),
        listOf("3", "4"),
        // two unrelated ones
        listOf("a"),
        listOf("b"),
    ) { root ->
        // we expect the root node to exists of disconnected nodes making up the unrelated segments, and the chain
        //  segment deep into the tree using a connected structure
        root.assertIsDisconnected()
        assertTrue("The tree structure (root level) has non-empty indexes for its leaf!") {
            root.single<DynamicJoinTree.Node.Leaf<TestNode>>().state.indexes.isEmpty()
        }
        val subtree = root.single<DynamicJoinTree.Node.Disconnected<TestNode>>()
        assertTrue("The tree structure (one level deep) has non-empty indexes for its leaf!") {
            subtree.single<DynamicJoinTree.Node.Leaf<TestNode>>().state.indexes.isEmpty()
        }
        // start of the chain, with the first element being connected and no indexes of its own
        val chainStart = subtree.single<DynamicJoinTree.Node.Connected<TestNode>>()
        assertTrue("The start of the chain is not indexed as expected!") {
            chainStart.buf.indexes.size == 0
        }
        val common = chainStart.single<DynamicJoinTree.Node.Leaf<TestNode>>().state.indexes.singleOrNull()
        assertNotNull(common, "No common binding found, while one was expected!")
        val innerChain = chainStart.single<DynamicJoinTree.Node.Connected<TestNode>>()
        assertContentEquals(setOf(GlobalQueryContext.resolveBinding(common)), innerChain.buf.indexes.asIntIterable())
        innerChain.both<DynamicJoinTree.Node.Leaf<TestNode>>()
    }

    @Test
    fun cartesian() = test(
        listOf("a", "b"),
        listOf("1", "2"),
        listOf("x", "y")
    ) {

    }

    /* test helpers */

    private fun test(vararg bindings: List<String>, test: (DynamicJoinTree.Node<TestNode>) -> Unit) {
        var i = 0
        bindings.toList().permutations().forEach { bindings ->
            val tree = buildTree(bindings)
            try {
                test(tree)
            } catch (t: Throwable) {
                fail("Permutation $i (bindings: ${bindings.joinToString()}) failed with ${t::class.simpleName}\n${t.message}\nTree structure:\n${tree.debugInformation()}", t)
            }
            ++i
        }
    }

    private fun <T> List<T>.permutations(): Sequence<List<T>> {
        fun generate(elements: List<T>): Sequence<List<T>> = sequence {
            if (elements.isEmpty()) {
                yield(listOf())
                return@sequence
            }
            for (i in elements.indices) {
                val base = elements.toMutableList()
                val last = base.removeAt(i)
                generate(base).forEach {
                    yield(it + last)
                }
            }
        }
        return generate(this)
    }

    private fun buildTree(bindings: List<List<String>>): DynamicJoinTree.Node<TestNode> {
        val nodes = bindings.map { TestNode(it) }
        return DynamicJoinTreeBuilder.build(GlobalQueryContext, nodes)
    }

    /**
     * Returns the only child of matching type [N], or fails the test if not exactly one child is of the provided type
     */
    private inline fun <reified N: DynamicJoinTree.Node<TestNode>> DynamicJoinTree.Node<TestNode>.single(): N = when (this) {
        is DynamicJoinTree.Node.Leaf<*> -> {
            fail("A node child ${N::class.simpleName} was expected, but parent is a leaf node!")
        }
        is DynamicJoinTree.Node.Connected<TestNode> -> {
            if (left is N && right is N) {
                fail("Both children are of type ${N::class.simpleName}, while exactly one such instance was expected!")
            } else if (left is N) {
                left
            } else if (right is N) {
                right
            } else {
                fail("No children of type ${N::class.simpleName} present, got ${left::class.simpleName} and ${right::class.simpleName} instead!")
            }
        }
        is DynamicJoinTree.Node.Disconnected<TestNode> -> {
            if (left is N && right is N) {
                fail("Both children are of type ${N::class.simpleName}, while exactly one such instance was expected!")
            } else if (left is N) {
                left
            } else if (right is N) {
                right
            } else {
                fail("No children of type ${N::class.simpleName} present, got ${left::class.simpleName} and ${right::class.simpleName} instead!")
            }
        }
    }

    /**
     * Asserts both children are of type [N]
     */
    private inline fun <reified N: DynamicJoinTree.Node<TestNode>> DynamicJoinTree.Node<TestNode>.both() {
        when (this) {
            is DynamicJoinTree.Node.Leaf<*> -> {
                fail("A node child ${N::class.simpleName} was expected, but parent is a leaf node!")
            }

            is DynamicJoinTree.Node.Connected<TestNode> -> {
                if (left !is N && right !is N) {
                    fail("Neither children are ${N::class.simpleName}, got ${left::class.simpleName} and ${right::class.simpleName} instead!")
                } else if (left !is N) {
                    fail("First child is of type ${left::class.simpleName}, expected ${N::class.simpleName}")
                } else if (right !is N) {
                    fail("Second child is of type ${right::class.simpleName}, expected ${N::class.simpleName}")
                }
            }

            is DynamicJoinTree.Node.Disconnected<TestNode> -> {
                if (left !is N && right !is N) {
                    fail("Neither children are ${N::class.simpleName}, got ${left::class.simpleName} and ${right::class.simpleName} instead!")
                } else if (left !is N) {
                    fail("First child is of type ${left::class.simpleName}, expected ${N::class.simpleName}")
                } else if (right !is N) {
                    fail("Second child is of type ${right::class.simpleName}, expected ${N::class.simpleName}")
                }
            }
        }
    }

    @OptIn(ExperimentalContracts::class)
    private fun DynamicJoinTree.Node<TestNode>.assertIsLeaf() {
        contract {
            returns() implies (this@assertIsLeaf is DynamicJoinTree.Node.Leaf<TestNode>)
        }
        assertIs<DynamicJoinTree.Node.Leaf<TestNode>>(this)
    }

    @OptIn(ExperimentalContracts::class)
    private fun DynamicJoinTree.Node<TestNode>.assertIsConnected() {
        contract {
            returns() implies (this@assertIsConnected is DynamicJoinTree.Node.Connected<TestNode>)
        }
        assertIs<DynamicJoinTree.Node.Connected<*>>(this)
    }

    @OptIn(ExperimentalContracts::class)
    private fun DynamicJoinTree.Node<TestNode>.assertIsDisconnected() {
        contract {
            returns() implies (this@assertIsDisconnected is DynamicJoinTree.Node.Disconnected<TestNode>)
        }
        assertIs<DynamicJoinTree.Node.Disconnected<TestNode>>(this)
    }

    private fun <T> assertContainsAll(collection: Collection<T>, elements: Iterable<T>) {
        elements.forEach { element ->
            if (element !in collection) {
                fail("Element ${element} was not found in $collection!")
            }
        }
    }

}
