package dev.tesserakt.sparql.runtime.incremental.types

internal class DebugWriter {

    private val stack = mutableListOf(StringBuilder())
    val data: CharSequence get() = stack.first()

    /**
     * Creates a new block, where every line created inside of it is being additionally indented according to the
     *  parameter values [startIndent] for the first line and [nextIndent] for the subsequent lines
     */
    inline fun block(
        startIndent: String,
        nextIndent: String = " ".repeat(startIndent.length),
        block: StringBuilder.() -> Unit
    ) {
        val parent = stack.last()
        val scope = StringBuilder()
        stack.add(scope)
        block(scope)
        val lines = scope.trim().lineSequence().iterator()
        if (!lines.hasNext()) {
            stack.removeLast()
            return
        }
        parent.append(startIndent)
        parent.appendLine(lines.next())
        while (lines.hasNext()) {
            parent.append(nextIndent)
            parent.appendLine(lines.next())
        }
        stack.removeLast()
    }

    /**
     * Directly appends a line of the string representation of the given [obj], without creating a new block
     */
    fun appendLine(obj: Any) {
        stack.last().appendLine(obj)
    }

}
