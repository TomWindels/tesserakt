
import dev.tesserakt.sparql.runtime.evaluation.BindingIdentifierSet
import dev.tesserakt.sparql.runtime.evaluation.DataDelta
import dev.tesserakt.sparql.runtime.evaluation.MappingDelta
import dev.tesserakt.sparql.runtime.evaluation.context.GlobalQueryContext
import dev.tesserakt.sparql.runtime.query.MutableJoinState
import dev.tesserakt.sparql.runtime.query.jointree.DynamicJoinTree
import dev.tesserakt.sparql.runtime.query.jointree.DynamicJoinTreeBuilder
import dev.tesserakt.sparql.util.Cardinality
import kotlin.test.*

class DynamicJoinTreeBuilderTest {

    class TestNode(override val bindings: Set<String>): MutableJoinState {

        constructor(bindings: List<String>): this(bindings = bindings.toSet())

        private var indexes = bindings

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

    // TODO: apply the testing logic (tree structure validation) using variations of the `buildTree`
    //  arguments (randomised order of binding lists), ensuring there is no input-order dependency on the final
    //  tree structure in any way (tree should be AST order independent structurally)

    @Test
    fun basic() {
        val root = buildTree(
            listOf("a", "b"),
            listOf("b"),
        )
        // the root node has to be disconnected, as caching the very last step is not useful
        assertIs<DynamicJoinTree.Node.Disconnected<*, *, *>>(root)
    }

    @Test
    fun small() {
        val root = buildTree(
            listOf("a", "b"),
            listOf("b"),
            listOf("b", "c"),
        )
        // ensuring correct root node type
        assertIs<DynamicJoinTree.Node.Disconnected<*, *, *>>(root)
        assertTrue {
            root.left is DynamicJoinTree.Node.Connected<*, *, *> ||
            root.right is DynamicJoinTree.Node.Connected<*, *, *>
        }
    }

    @Test
    fun chain() {
        val root = buildTree(
            listOf("1", "2"),
            listOf("2", "3"),
            listOf("3", "4"),
            listOf("4", "5"),
            listOf("5", "6"),
        )
        // ensuring correct root node type
        assertIs<DynamicJoinTree.Node.Disconnected<*, *, *>>(root)
        // a deep-left/right join tree should be constructed; all nodes should have one leaf and one connected node,
        // until reaching the end
        var sibling = root.single<DynamicJoinTree.Node.Leaf<TestNode>>()
        var subtree = root.single<DynamicJoinTree.Node.Connected<TestNode, *, *>>()
        repeat(2) {
            // making sure its indexed on its common pair
            assertIs<DynamicJoinTree.Node.Connected<TestNode, *, *>>(subtree)
            val activeIndex = subtree.buf.indexes.asIntIterable().single()
            val possibleIndexes = sibling.state.bindings
            assertContains(possibleIndexes, GlobalQueryContext.resolveBinding(activeIndex))
            // moving to the next check
            sibling = subtree.single<DynamicJoinTree.Node.Leaf<TestNode>>()
            subtree = subtree.single<DynamicJoinTree.Node.Connected<TestNode, *, *>>()
        }
        // the last subtree should have both children as leaf nodes
        subtree.both<DynamicJoinTree.Node.Leaf<TestNode>>()
    }

    @Test
    fun mix1() {
        val root = buildTree(
            // chain segments
            listOf("1", "2"),
            listOf("2", "3"),
            listOf("3", "4"),
            // first unrelated segment
            listOf("0"),
            // star segments
            listOf("0", "1"),
            listOf("0", "2"),
            listOf("0", "3"),
            listOf("0", "4"),
            // remaining unrelated segments
            listOf("1"),
            listOf("2"),
            listOf("3"),
            listOf("4"),
        )
    }

    @Test
    fun mix2() {
        val root = buildTree(
            // chain segments
            listOf("1", "2"),
            listOf("2", "3"),
            listOf("3", "4"),
            // first unrelated segment
            listOf("0"),
            // reverse star segments
            listOf("1", "0"),
            listOf("2", "0"),
            listOf("3", "0"),
            listOf("4", "0"),
            // remaining unrelated segments
            listOf("1"),
            listOf("2"),
            listOf("3"),
            listOf("4"),
        )
    }

    /* test helpers */

    private fun buildTree(vararg bindings: List<String>): DynamicJoinTree.Node<TestNode> {
        val nodes = bindings.map { TestNode(it) }
        return DynamicJoinTreeBuilder.build(GlobalQueryContext, nodes)
            .also { root ->
                println("Created the following tree:\n${root.debugInformation()}")
            }
    }

    /**
     * Returns the only child of matching type [N], or fails the test if not exactly one child is of the provided type
     */
    private inline fun <reified N: DynamicJoinTree.Node<TestNode>> DynamicJoinTree.Node<TestNode>.single(): N = when (this) {
        is DynamicJoinTree.Node.Leaf<*> -> {
            fail("A node child ${N::class.simpleName} was expected, but parent is a leaf node!")
        }
        is DynamicJoinTree.Node.Connected<*, *, *> -> {
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
        is DynamicJoinTree.Node.Disconnected<*, *, *> -> {
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

            is DynamicJoinTree.Node.Connected<*, *, *> -> {
                if (left !is N && right !is N) {
                    fail("Neither children are ${N::class.simpleName}, got ${left::class.simpleName} and ${right::class.simpleName} instead!")
                } else if (left !is N) {
                    fail("First child is of type ${left::class.simpleName}, expected ${N::class.simpleName}")
                } else if (right !is N) {
                    fail("Second child is of type ${right::class.simpleName}, expected ${N::class.simpleName}")
                }
            }

            is DynamicJoinTree.Node.Disconnected<*, *, *> -> {
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

}
