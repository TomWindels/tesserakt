package dev.tesserakt.sparql.runtime.evaluation

import dev.tesserakt.sparql.util.Cardinality

/**
 * Base statistics type, used to observe query state (e.g. explaining a join plan, behaviour of the state w.r.t. certain
 *  changes, ...).
 */
sealed interface Statistics {

    enum class Mode {
        /**
         * Only retain the logical structure of the query evaluation operators, with no descriptions.
         */
        STRUCTURE_ONLY,

        /**
         * Provide a high level set of query structure descriptions
         */
        HIGH_LEVEL,

        /**
         * Provide more detailed query structure definitions
         */
        DETAILED,

        /**
         * Provide all possible information with the query structure
         */
        VERBOSE,
        ;

        companion object {

            var current: Mode = DETAILED
                private set

            infix fun isAtLeast(mode: Mode) : Boolean {
                return current.ordinal >= mode.ordinal
            }

            fun setMode(mode: Mode) {
                current = mode
            }

        }

    }

    /**
     * The empty case: an empty join tree, empty UNION block, ...
     */
    data object Empty : Statistics {
        override fun toString(): String {
            return "( empty )"
        }
    }

    /**
     * A description added to a (set of) statistic nodes, used to identify what parts of a query correspond to
     *  certain statistics properties
     */
    @ConsistentCopyVisibility
    data class DescriptionElement internal constructor(val inner: Statistics, val description: String) : Statistics {

        override fun toString(): String {
            val inner = inner.toString().lines()
            val width = inner.maxOf { it.length }
            return buildString {
                append("┌─")
                repeat(width) {
                    append("─")
                }
                appendLine("─┐")

                description.lineSequence().forEach { line ->
                    line.chunked(width).forEach { substring ->
                        append("│ ")
                        append(substring)
                        append(" ".repeat(width - substring.length))
                        appendLine(" │")
                    }
                }

                append('│')
                append(" ".repeat(width + 2))
                appendLine('│')

                inner.forEach { line ->
                    append("│ ")
                    append(line)
                    append(" ".repeat(width - line.length))
                    appendLine(" │")
                }

                append("└─")
                repeat(width) {
                    append("─")
                }
                append("─┘")
            }
        }

    }

    /**
     * A single query element, simply holding a set of elements, resulting in a given cardinality (e.g. X bindings
     *  matching a single triple pattern), and the change count (e.g. X triples being inserted + Y of those X triples
     *  having been removed matching a triple pattern).
     */
    @ConsistentCopyVisibility
    data class SingleElement internal constructor(val cardinality: Cardinality, val changeCount: Long) : Statistics {
        override fun toString(): String {
            return "* cardinality: $cardinality\n* change count: $changeCount"
        }
    }

    /**
     * A combination of two elements being joined into one, e.g. two triple patterns or a union (cartesian join). Can be
     *  the [SelectiveElement.inner] element of a [SelectiveElement] if this join happens on a common (set of)
     *  binding(s).
     */
    @ConsistentCopyVisibility
    data class JoinedElement private constructor(val left: Statistics, val right: Statistics) : Statistics {

        override fun toString(): String {
            val top = StatsBlock(left)
            val bottom = StatsBlock(right)
            return buildString {
                appendLine(top.connectDown().prependIndent(" "))
                appendLine("[ ]")
                append(bottom.connectUp().prependIndent(" "))
            }
        }

        companion object {
            internal operator fun invoke(left: Statistics, right: Statistics) = when {
                left is Empty && right is Empty -> Empty
                left is Empty -> right
                right is Empty -> left
                else -> JoinedElement(left, right)
            }
        }

    }

    /**
     * A query element 'transforming' the incoming results, reducing to a subset.
     * Examples include a join on a (set of) common binding(s), a `FILTER` expression, `LIMIT` clause, ...
     * The [cardinality] can be `null` in cases where the cardinality is not cached / known for a given
     *  element (e.g. a specific `FILTER` expression)
     */
    @ConsistentCopyVisibility
    data class SelectiveElement internal constructor(val inner: Statistics, val cardinality: Cardinality?) : Statistics {
        override fun toString(): String {
            val cardinalityDescription = if (cardinality != null) {
                "< cardinality: $cardinality"
            } else {
                "< cardinality: unknown"
            }
            return StatsBlock(inner).withDescription(cardinalityDescription)
        }
    }

    /**
     * Creates a new instance of this element compared to the [reference] element (difference in change count).
     * This method fails if the structure mismatches with the [reference], yielding an [IllegalArgumentException].
     *
     * This is mainly intended for scenarios where the impact of data changes is to be observed w.r.t. the query
     *  structure.
     */
    fun diff(reference: Statistics): Statistics {
        if (!reference::class.isInstance(reference)) {
            throw IllegalArgumentException("Tree structure mismatch!")
        }
        return when (this) {
            Empty -> Empty
            is DescriptionElement -> {
                reference as DescriptionElement
                // the actual description may mismatch; this is not considered a structural mismatch, so instead we want
                //  to keep the most up-to-date version of the description (= ours)
                this.copy(
                    inner = this.inner.diff(reference.inner)
                )
            }
            is JoinedElement -> {
                reference as JoinedElement
                JoinedElement(
                    left = left.diff(reference.left),
                    right = right.diff(reference.right),
                )
            }
            is SelectiveElement -> {
                reference as SelectiveElement
                this.copy(
                    inner = inner.diff(reference.inner),
                    // we do not alter the cardinality; it could become negative otherwise, which is not meaningful
                )
            }
            is SingleElement -> {
                reference as SingleElement
                // we do not alter the cardinality; it could become negative otherwise, which is not meaningful
                this.copy(
                    // if used properly, the change count should still be >= 0, as changes only increment over time
                    changeCount = this.changeCount - reference.changeCount,
                )
            }
        }
    }

}

private class StatsBlock(stats: Statistics) {

    /**
     * The actual lines making up the block, without any prefix
     */
    private val lines = stats.toString().lines()

    /**
     * The index of the first line that is considered part of the top level element
     */
    val elementStart = lines.indexOfFirst { it.isElementLine() }

    /**
     * The index of the last line that is considered part of the top level element
     */
    val elementEnd = lines.indexOfLast { it.isElementLine() }

    /**
     * The size of the element (= number of lines ranging over the top level element), which fits in the [totalHeight]
     */
    val elementHeight get() = elementEnd - elementStart

    /**
     * The total size of the block (= with child nodes expanded)
     */
    val totalHeight get() = lines.size

    /**
     * Constructs the string representation of this block, with an indent and `/` connection lines
     */
    fun connectDown(): String {
        val center = (elementStart + elementEnd) / 2
        return construct { i ->
            when {
                i < center -> {
                    "   "
                }
                i == center -> {
                    "┌──"
                }
                else -> {
                    "│  "
                }
            }
        }
    }

    /**
     * Constructs the string representation of this block, with an indent and `\` connection lines
     */
    fun connectUp(): String {
        return construct { i ->
            val center = (elementStart + elementEnd) / 2
            return construct { i ->
                when {
                    i > center -> {
                        "   "
                    }

                    i == center -> {
                        "└──"
                    }

                    else -> {
                        "│  "
                    }
                }
            }
        }
    }

    fun withDescription(text: String): String {
        val description = "$text ─"
        val padding = " ".repeat(text.length + 2)
        return construct { i ->
            if (i == elementEnd) {
                description
            } else {
                padding
            }
        }
    }

    /**
     * Constructs the string representation of this block using a custom prefix for every element
     */
    private inline fun construct(
        createPrefix: (index: Int) -> String,
    ): String {
        return buildString {
            (0 ..< lines.size - 1).forEach { i ->
                append(createPrefix(i))
                appendLine(lines[i])
            }
            append(createPrefix(lines.size - 1))
            append(lines.last())
        }
    }

    /**
     * Checks if this string is a line that should be considered 'part of the top level element'
     */
    private fun String.isElementLine() : Boolean {
        // this[0] has to exist if we aren't blank
        return !isBlank() && !this[0].isWhitespace()
    }

}
