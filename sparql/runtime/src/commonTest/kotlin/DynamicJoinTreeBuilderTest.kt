
import dev.tesserakt.sparql.QueryStatistics
import dev.tesserakt.sparql.runtime.collection.MappingArrayHint
import dev.tesserakt.sparql.runtime.evaluation.BindingIdentifierSet
import dev.tesserakt.sparql.runtime.evaluation.DataDelta
import dev.tesserakt.sparql.runtime.evaluation.MappingDelta
import dev.tesserakt.sparql.runtime.evaluation.Statistics
import dev.tesserakt.sparql.runtime.evaluation.context.GlobalQueryContext
import dev.tesserakt.sparql.runtime.evaluation.context.QueryContext
import dev.tesserakt.sparql.runtime.query.FilterExpression
import dev.tesserakt.sparql.runtime.query.MutableJoinState
import dev.tesserakt.sparql.runtime.query.jointree.DynamicJoinTree
import dev.tesserakt.sparql.runtime.query.jointree.DynamicJoinTreeBuilder
import dev.tesserakt.sparql.runtime.stream.OptimisedStream
import dev.tesserakt.sparql.runtime.stream.emptyStream
import dev.tesserakt.sparql.types.Expression
import dev.tesserakt.sparql.util.Cardinality
import dev.tesserakt.sparql.util.ZeroCardinality
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.test.*

class DynamicJoinTreeBuilderTest {

    class TestNode(
        override val bindings: BindingIdentifierSet,
    ): MutableJoinState {

        constructor(bindings: List<String>): this(
            bindings = BindingIdentifierSet(GlobalQueryContext, bindings),
        )

        var indexes = bindings
            private set

        // this makes it so there's no bias towards any single test node
        override val cardinality: Cardinality
            get() = ZeroCardinality

        override fun reindex(bindings: BindingIdentifierSet, hint: MappingArrayHint) {
            indexes = bindings
        }

        override fun join(delta: MappingDelta): OptimisedStream<MappingDelta> {
            // no delta
            return emptyStream()
        }

        override fun peek(delta: DataDelta): OptimisedStream<MappingDelta> {
            // nothing matches
            return emptyStream()
        }

        override fun process(delta: DataDelta) {
            // no backing structure, no-op
        }

        override fun stats(context: QueryContext, granularity: QueryStatistics.Granularity) =
            Statistics.DescriptionElement(
                inner = Statistics.SingleElement(
                    cardinality = ZeroCardinality,
                    changeCount = 0L
                ),
                description = "Test node, bindings:\n${bindings.asIntIterable().joinToString { GlobalQueryContext.resolveBinding(it) }}")

        override fun toString() = "Node(bindings=${bindings}, indexes=${indexes})"

    }

    @Test
    fun basic() = test (
        listOf("a", "b"),
        listOf("b"),
    ) { root ->
        assertTrue {
            root is DynamicJoinTree.Node.Disconnected || (
                root is DynamicJoinTree.Node.Connected &&
                // we haven't been reindexed
                root.buf.indexes.isEmpty()
            )
        }
    }

    @Test
    fun small() = test(
        listOf("a", "b"),
        listOf("b"),
        listOf("b", "c"),
    ) { root ->
        // ensuring correct root node type
        assertTrue {
            root is DynamicJoinTree.Node.Connected && (
                root.left is DynamicJoinTree.Node.Connected ||
                root.right is DynamicJoinTree.Node.Connected
            ) || root is DynamicJoinTree.Node.Disconnected && (
                root.left is DynamicJoinTree.Node.Connected ||
                root.right is DynamicJoinTree.Node.Connected
            )
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
        // a deep-left/right join tree should be constructed; all nodes should have one leaf and one connected node,
        // until reaching the end
        var sibling = root.single<DynamicJoinTree.Node.Leaf>()
        var subtree = root.single<DynamicJoinTree.Node.Connected>()
        repeat(2) {
            // making sure its indexed on its common pair
            subtree.assertIsConnected()
            val activeIndex = subtree.buf.indexes.asIntIterable().single()
            val possibleIndexes = sibling.state.bindings
            assertTrue { activeIndex in possibleIndexes }
            // moving to the next check
            sibling = subtree.single<DynamicJoinTree.Node.Leaf>()
            subtree = subtree.single<DynamicJoinTree.Node.Connected>()
        }
        // the last subtree should have both children as leaf nodes
        subtree.both<DynamicJoinTree.Node.Leaf>()
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
    ) { root, patterns ->
        // there is always at least one binding overlap between segments, so no cartesian joins are expected
        // we check if we're missing any patterns after constructing the tree; we do so by checking all leaf bindings
        //  before the tree has been constructed with the individual leafs discovered after traversing the result;
        //  we can do this as we know the binding combinations are unique per leaf
        val missing = patterns.mapTo(mutableSetOf()) { TestNode(it).bindings }

        fun check(node: DynamicJoinTree.Node) {
            // making sure its indexed on its common pair
            node.assertIsConnected()
            val activeIndexes = node.buf.indexes.asIntIterable()
            if (!activeIndexes.iterator().hasNext()) {
                fail("Expected at least one active index, got none: ${node.left.bindings} <> ${node.right.bindings} is cached with ${node.buf}!")
            }
            val possibleIndexes = node.bindings
            assertTrue { activeIndexes.all { it in possibleIndexes } }
            // moving to the next check
            when (node.left) {
                is DynamicJoinTree.Node.Connected -> {
                    check(node.left)
                }
                is DynamicJoinTree.Node.Disconnected -> {
                    fail("Unexpected disconnected child!")
                }
                is DynamicJoinTree.Node.Leaf -> {
                    // terminal point reached
                    missing.remove(node.left.state.bindings)
                }
            }
            when (node.right) {
                is DynamicJoinTree.Node.Connected -> {
                    check(node.right)
                }
                is DynamicJoinTree.Node.Disconnected -> {
                    fail("Unexpected disconnected child!")
                }
                is DynamicJoinTree.Node.Leaf -> {
                    // terminal point reached
                    missing.remove(node.right.state.bindings)
                }
            }
        }
        when (root) {
            // can be valid if no further tree information is known, but
            //  we don't expect any indexes here as it was never reindexed
            is DynamicJoinTree.Node.Connected -> {
                assertTrue {
                    root.buf.indexes == BindingIdentifierSet.EMPTY
                }
                when (root.left) {
                    is DynamicJoinTree.Node.Connected -> {
                        check(root.left)
                    }
                    is DynamicJoinTree.Node.Disconnected -> {
                        fail("Did not expect a disconnected node at this point!")
                    }
                    is DynamicJoinTree.Node.Leaf -> {
                        missing.remove(root.left.state.bindings)
                    }
                }
                when (root.right) {
                    is DynamicJoinTree.Node.Connected -> {
                        check(root.right)
                    }
                    is DynamicJoinTree.Node.Disconnected -> {
                        fail("Did not expect a disconnected node at this point!")
                    }
                    is DynamicJoinTree.Node.Leaf -> {
                        missing.remove(root.right.state.bindings)
                    }
                }
            }
            // can be valid if we're configured to be the most top-level tree
            is DynamicJoinTree.Node.Disconnected -> {
                when (root.left) {
                    is DynamicJoinTree.Node.Connected -> {
                        check(root.left)
                    }
                    is DynamicJoinTree.Node.Disconnected -> {
                        fail("Did not expect a disconnected node at this point!")
                    }
                    is DynamicJoinTree.Node.Leaf -> {
                        missing.remove(root.left.state.bindings)
                    }
                }
                when (root.right) {
                    is DynamicJoinTree.Node.Connected -> {
                        check(root.right)
                    }
                    is DynamicJoinTree.Node.Disconnected -> {
                        fail("Did not expect a disconnected node at this point!")
                    }
                    is DynamicJoinTree.Node.Leaf -> {
                        missing.remove(root.right.state.bindings)
                    }
                }
            }
            is DynamicJoinTree.Node.Leaf -> fail("Root node is of wrong type!")
        }
        if (missing.isNotEmpty()) {
            fail("Missing the following states: ${missing.joinToString()}")
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
    ) { root, patterns ->
        // there is always at least one binding overlap between segments, so no cartesian joins are expected
        // we check if we're missing any patterns after constructing the tree; we do so by checking all leaf bindings
        //  before the tree has been constructed with the individual leafs discovered after traversing the result;
        //  we can do this as we know the binding combinations are unique per leaf
        val missing = patterns.mapTo(mutableSetOf()) { TestNode(it).bindings }

        fun check(node: DynamicJoinTree.Node) {
            // making sure its indexed on its common pair
            node.assertIsConnected()
            val activeIndexes = node.buf.indexes.asIntIterable()
            if (!activeIndexes.iterator().hasNext()) {
                fail("Expected at least one active index, got none: ${node.left.bindings} <> ${node.right.bindings} is cached with ${node.buf}!")
            }
            val possibleIndexes = node.bindings
            assertTrue { activeIndexes.all { it in possibleIndexes } }
            // moving to the next check
            when (node.left) {
                is DynamicJoinTree.Node.Connected -> {
                    check(node.left)
                }
                is DynamicJoinTree.Node.Disconnected -> {
                    fail("Unexpected disconnected child!")
                }
                is DynamicJoinTree.Node.Leaf -> {
                    // terminal point reached
                    missing.remove(node.left.state.bindings)
                }
            }
            when (node.right) {
                is DynamicJoinTree.Node.Connected -> {
                    check(node.right)
                }
                is DynamicJoinTree.Node.Disconnected -> {
                    fail("Unexpected disconnected child!")
                }
                is DynamicJoinTree.Node.Leaf -> {
                    // terminal point reached
                    missing.remove(node.right.state.bindings)
                }
            }
        }
        when (root) {
            // can be valid if no further tree information is known, but
            //  we don't expect any indexes here as it was never reindexed
            is DynamicJoinTree.Node.Connected -> {
                assertTrue {
                    root.buf.indexes == BindingIdentifierSet.EMPTY
                }
                when (root.left) {
                    is DynamicJoinTree.Node.Connected -> {
                        check(root.left)
                    }
                    is DynamicJoinTree.Node.Disconnected -> {
                        fail("Did not expect a disconnected node at this point!")
                    }
                    is DynamicJoinTree.Node.Leaf -> {
                        missing.remove(root.left.state.bindings)
                    }
                }
                when (root.right) {
                    is DynamicJoinTree.Node.Connected -> {
                        check(root.right)
                    }
                    is DynamicJoinTree.Node.Disconnected -> {
                        fail("Did not expect a disconnected node at this point!")
                    }
                    is DynamicJoinTree.Node.Leaf -> {
                        missing.remove(root.right.state.bindings)
                    }
                }
            }
            // can be valid if we're configured to be the most top-level tree
            is DynamicJoinTree.Node.Disconnected -> {
                when (root.left) {
                    is DynamicJoinTree.Node.Connected -> {
                        check(root.left)
                    }
                    is DynamicJoinTree.Node.Disconnected -> {
                        fail("Did not expect a disconnected node at this point!")
                    }
                    is DynamicJoinTree.Node.Leaf -> {
                        missing.remove(root.left.state.bindings)
                    }
                }
                when (root.right) {
                    is DynamicJoinTree.Node.Connected -> {
                        check(root.right)
                    }
                    is DynamicJoinTree.Node.Disconnected -> {
                        fail("Did not expect a disconnected node at this point!")
                    }
                    is DynamicJoinTree.Node.Leaf -> {
                        missing.remove(root.right.state.bindings)
                    }
                }
            }
            is DynamicJoinTree.Node.Leaf -> fail("Root node is of wrong type!")
        }
        if (missing.isNotEmpty()) {
            fail("Missing the following states: ${missing.joinToString()}")
        }
    }


    @Test
    fun connected() = test(
        // chain 1 segments
        listOf("1a", "2a"),
        listOf("2a", "3a"),
        // chain 2 segments
        listOf("1b", "2b"),
        listOf("2b", "3b"),
    ) { root ->
        val one = BindingIdentifierSet(GlobalQueryContext, setOf("2a"))
        val two = BindingIdentifierSet(GlobalQueryContext, setOf("2b"))
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
        // we want to ensure the root node here is disconnected, as it has to join with no overlap at the very
        //  end (cartesian join)
        root.assertIsDisconnected()
        // we expect a single child of our root parent that contains our unrelated bindings in a disconnected child
        assertTrue("The tree structure (root level) has non-empty indexes for its leaf!") {
            root.single<DynamicJoinTree.Node.Leaf>().state.indexes.isEmpty()
        }
        val subtree = root.single<DynamicJoinTree.Node.Disconnected>()
        assertTrue("The tree structure (one level deep) has non-empty indexes for its leaf!") {
            subtree.single<DynamicJoinTree.Node.Leaf>().state.indexes.isEmpty()
        }
        // start of the chain, with the first element being connected and no indexes of its own
        val chainStart = subtree.single<DynamicJoinTree.Node.Connected>()
        assertTrue("The start of the chain is not indexed as expected!") {
            chainStart.buf.indexes.size == 0
        }
        val common = chainStart.single<DynamicJoinTree.Node.Leaf>().state.indexes.asIntIterable().singleOrNull()
        assertNotNull(common, "No common binding found, while one was expected!")
        val innerChain = chainStart.single<DynamicJoinTree.Node.Connected>()
        assertContentEquals(listOf(common), innerChain.buf.indexes.asIntIterable())
        innerChain.both<DynamicJoinTree.Node.Leaf>()
    }

    @Test
    fun cartesian() = test(
        listOf("a", "b"),
        listOf("1", "2"),
        listOf("x", "y")
    ) { root ->
        // the tree should be completely made up of disconnected nodes and no indexes for any of its leafs
        root.assertIsDisconnected()
        assertTrue("Got unexpected indexes!") { root.single<DynamicJoinTree.Node.Leaf>().state.indexes.isEmpty() }
        val subtree = root.single<DynamicJoinTree.Node.Disconnected>()
        subtree.left.assertIsLeaf()
        assertTrue("Got unexpected indexes!") { subtree.left.state.indexes.isEmpty() }
        subtree.right.assertIsLeaf()
        assertTrue("Got unexpected indexes!") { subtree.right.state.indexes.isEmpty() }
    }

    // we simulate a filter that compares `?a` with `?f`, so these bindings are prioritized
    // the remaining structure of the patterns would otherwise not lead to such a hierarchy naturally
    @Test
    fun filters() = testWithFilters(
        filters = listOf(
            FilterExpression(
                context = GlobalQueryContext,
                expr = Expression.Calculation(
                    lhs = Expression.BindingValues("a"),
                    rhs = Expression.BindingValues("f"),
                    operator = Expression.Calculation.Operator.CMP_NEQ
                )
            )),
        listOf("a", "b"),
        listOf("a"),
        listOf("b"),
        listOf("a", "c"),
        listOf("c"),
        listOf("g", "c"),
        listOf("g"),
        listOf("g", "d"),
        listOf("d"),
        listOf("d", "e"),
        listOf("e"),
        listOf("f", "e"),
    ) { root ->
        assertTrue {
            root is DynamicJoinTree.Node.Disconnected || (
                root is DynamicJoinTree.Node.Connected &&
                root.buf.indexes == BindingIdentifierSet.EMPTY
            )
        }

        // we first expect none to contain either `a` or `f` in the leaf nodes that are part of the subtree
        var treeDepth = 0
        var subtree = root
        while (
            subtree.hasSingle<DynamicJoinTree.Node.Leaf>() &&
            subtree.getSingle<DynamicJoinTree.Node.Connected>().let { connected ->
                connected != null && connected.filters.isEmpty()
            }
        ) {
            // we go deeper, away from the leaf we just checked
            subtree = subtree.getSingle<DynamicJoinTree.Node.Connected>()
                // should work as we just checked
                ?: fail("Unexpected failure during tree traversal")
            ++treeDepth
        }
        // we need to be a few levels deep
        assertTrue { treeDepth > 5 }
        // we traverse to the child that has our filters
        subtree = subtree.getSingle<DynamicJoinTree.Node.Connected>()
            ?: fail("Did not find the tree segment with the filter!")
        // we should have ended at a connected segment that has our filter
        assertTrue { subtree.filters.isNotEmpty() }
        // and this node has all bindings required to form the proper path, including those required by the filter
        assertTrue { BindingIdentifierSet(GlobalQueryContext, listOf("a", "c", "d", "g", "e", "f")) in subtree.bindings }
    }

    /* test helpers */

    private fun test(vararg bindings: List<String>, test: (DynamicJoinTree.Node) -> Unit) {
        var i = 0
        bindings.toList().permutations().forEach { bindings ->
            val tree = buildTree(bindings)
            try {
                test(tree)
            } catch (t: Throwable) {
                fail(
                    "Permutation $i (bindings: ${bindings.joinToString()}) failed with ${t::class.simpleName}\n${t.message}\n${tree.stats(
                    GlobalQueryContext, QueryStatistics.Granularity.VERBOSE)}", t)
            }
            ++i
        }
    }

    private fun test(vararg bindings: List<String>, test: (DynamicJoinTree.Node, List<List<String>>) -> Unit) {
        var i = 0
        bindings.toList().permutations().forEach { bindings ->
            val tree = buildTree(bindings)
            try {
                test(tree, bindings)
            } catch (t: Throwable) {
                fail(
                    "Permutation $i (bindings: ${bindings.joinToString()}) failed with ${t::class.simpleName}\n${t.message}\n${tree.stats(
                    GlobalQueryContext, QueryStatistics.Granularity.VERBOSE)}", t)
            }
            ++i
        }
    }

    private fun testWithFilters(
        filters: List<FilterExpression>,
        vararg bindings: List<String>, test: (DynamicJoinTree.Node) -> Unit
    ) {
        var i = 0
        bindings.toList().permutations().forEach { bindings ->
            val tree = buildTree(bindings, filters)
            try {
                test(tree)
            } catch (t: Throwable) {
                fail(
                    "Permutation $i (bindings: ${bindings.joinToString()}) failed with ${t::class.simpleName}\n${t.message}\n${tree.stats(
                        GlobalQueryContext, QueryStatistics.Granularity.VERBOSE)}", t)
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
        // making sure the # of permutations don't explode; we only test the first 1000 in that case
        return generate(this).take(1_000)
    }

    private fun buildTree(
        bindings: List<List<String>>,
        filters: List<FilterExpression> = emptyList(),
    ): DynamicJoinTree.Node {
        val nodes = bindings.map { TestNode(it) }
        return DynamicJoinTreeBuilder.build(GlobalQueryContext, nodes, filters, externalBindings = BindingIdentifierSet.EMPTY)
    }

    /**
     * Returns the only child of matching type [N], or fails the test if not exactly one child is of the provided type
     */
    private inline fun <reified N: DynamicJoinTree.Node> DynamicJoinTree.Node.single(): N = when (this) {
        is DynamicJoinTree.Node.Leaf -> {
            fail("A node child ${N::class.simpleName} was expected, but parent is a leaf node!")
        }
        is DynamicJoinTree.Node.Connected -> {
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
        is DynamicJoinTree.Node.Disconnected -> {
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
     * Returns the only child of matching type [N], or fails the test if not exactly one child is of the provided type
     */
    private inline fun <reified N: DynamicJoinTree.Node> DynamicJoinTree.Node.getSingle(): N? = when (this) {
        is DynamicJoinTree.Node.Leaf -> {
            return null
        }
        is DynamicJoinTree.Node.Connected -> {
            if (left is N && right is N) {
                null
            } else if (left is N) {
                left
            } else if (right is N) {
                right
            } else {
                null
            }
        }
        is DynamicJoinTree.Node.Disconnected -> {
            if (left is N && right is N) {
                null
            } else if (left is N) {
                left
            } else if (right is N) {
                right
            } else {
                null
            }
        }
    }

    /**
     * Returns the only child of matching type [N], or fails the test if not exactly one child is of the provided type
     */
    private inline fun <reified N: DynamicJoinTree.Node> DynamicJoinTree.Node.hasSingle(): Boolean = when (this) {
        is DynamicJoinTree.Node.Leaf -> {
            false
        }
        is DynamicJoinTree.Node.Connected -> {
            if (left is N && right is N) {
                false
            } else if (left is N) {
                true
            } else if (right is N) {
                true
            } else {
                false
            }
        }
        is DynamicJoinTree.Node.Disconnected -> {
            if (left is N && right is N) {
                false
            } else if (left is N) {
                true
            } else if (right is N) {
                true
            } else {
                false
            }
        }
    }

    /**
     * Returns the other child of mismatching type [N], or fails the test if not exactly one child is of the provided
     *  type
     */
    private inline fun <reified N: DynamicJoinTree.Node> DynamicJoinTree.Node.singleNot(): DynamicJoinTree.Node = when (this) {
        is DynamicJoinTree.Node.Leaf -> {
            fail("A node child ${N::class.simpleName} was expected, but parent is a leaf node!")
        }
        is DynamicJoinTree.Node.Connected -> {
            if (left is N && right is N) {
                fail("Both children are of type ${N::class.simpleName}, while exactly one such instance was expected!")
            } else if (left is N) {
                right
            } else if (right is N) {
                left
            } else {
                fail("No children of type ${N::class.simpleName} present, got ${left::class.simpleName} and ${right::class.simpleName} instead!")
            }
        }
        is DynamicJoinTree.Node.Disconnected -> {
            if (left is N && right is N) {
                fail("Both children are of type ${N::class.simpleName}, while exactly one such instance was expected!")
            } else if (left is N) {
                right
            } else if (right is N) {
                left
            } else {
                fail("No children of type ${N::class.simpleName} present, got ${left::class.simpleName} and ${right::class.simpleName} instead!")
            }
        }
    }

    /**
     * Asserts both children are of type [N]
     */
    private inline fun <reified N: DynamicJoinTree.Node> DynamicJoinTree.Node.both() {
        when (this) {
            is DynamicJoinTree.Node.Leaf -> {
                fail("A node child ${N::class.simpleName} was expected, but parent is a leaf node!")
            }

            is DynamicJoinTree.Node.Connected -> {
                if (left !is N && right !is N) {
                    fail("Neither children are ${N::class.simpleName}, got ${left::class.simpleName} and ${right::class.simpleName} instead!")
                } else if (left !is N) {
                    fail("First child is of type ${left::class.simpleName}, expected ${N::class.simpleName}")
                } else if (right !is N) {
                    fail("Second child is of type ${right::class.simpleName}, expected ${N::class.simpleName}")
                }
            }

            is DynamicJoinTree.Node.Disconnected -> {
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

    /**
     * Tests whether both children are of type [N]
     */
    private inline fun <reified N: DynamicJoinTree.Node> DynamicJoinTree.Node.hasBoth(): Boolean {
        return when (this) {
            is DynamicJoinTree.Node.Leaf -> {
                false
            }

            is DynamicJoinTree.Node.Connected -> {
                if (left !is N && right !is N) {
                    false
                } else if (left !is N) {
                    false
                } else if (right !is N) {
                    false
                } else {
                    true
                }
            }

            is DynamicJoinTree.Node.Disconnected -> {
                if (left !is N && right !is N) {
                    false
                } else if (left !is N) {
                    false
                } else if (right !is N) {
                    false
                } else {
                    true
                }
            }
        }
    }

    @OptIn(ExperimentalContracts::class)
    private fun DynamicJoinTree.Node.assertIsLeaf() {
        contract {
            returns() implies (this@assertIsLeaf is DynamicJoinTree.Node.Leaf)
        }
        assertIs<DynamicJoinTree.Node.Leaf>(this)
    }

    @OptIn(ExperimentalContracts::class)
    private fun DynamicJoinTree.Node.assertIsConnected() {
        contract {
            returns() implies (this@assertIsConnected is DynamicJoinTree.Node.Connected)
        }
        assertIs<DynamicJoinTree.Node.Connected>(this)
    }

    @OptIn(ExperimentalContracts::class)
    private fun DynamicJoinTree.Node.assertIsDisconnected() {
        contract {
            returns() implies (this@assertIsDisconnected is DynamicJoinTree.Node.Disconnected)
        }
        assertIs<DynamicJoinTree.Node.Disconnected>(this)
    }

    private fun <T> assertContainsAll(collection: Collection<T>, elements: Iterable<T>) {
        elements.forEach { element ->
            if (element !in collection) {
                fail("Element ${element} was not found in $collection!")
            }
        }
    }

    private val MutableJoinState.indexes: BindingIdentifierSet
        get() = (this as TestNode).indexes

}
