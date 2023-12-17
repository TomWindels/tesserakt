package tesserakt.util.console

import tesserakt.util.shifted

class StylisedString private constructor(
    private val builder: StringBuilder,
    length: Int,
    /** LUT containing all applied styles with their affected (visual!) ranges. No overlap in range is possible. **/
    private val styles: MutableList<Pair<IntRange, List<StyleModifier>>>
) {

    var length: Int = length
        private set

    constructor(text: String, color: Color = Color.DEFAULT, vararg decoration: Decoration): this(
        builder = StringBuilder(text.formatted(color, *decoration)),
        length = text.length,
        styles = mutableListOf(text.indices to listOf(color, *decoration))
    )

    constructor(): this(
        builder = StringBuilder(),
        length = 0,
        styles = mutableListOf()
    )

    fun add(stylisedString: StylisedString): StylisedString {
        stylisedString.styles.forEach { (range, styles) ->
            this.styles.add((range shifted length) to styles)
        }
        builder.append(stylisedString.builder)
        length += stylisedString.length
        return this
    }

    fun add(
        text: String,
        color: Color,
        vararg decoration: Decoration
    ): StylisedString {
        styles.add(length until (length + text.length) to listOf(color, *decoration))
        builder.append(text.formatted(color, *decoration))
        length += text.length
        return this
    }

    fun add(
        text: String,
        decoration: Decoration,
        vararg extra: Decoration
    ): StylisedString {
        styles.add(length until (length + text.length) to listOf(decoration, *extra))
        builder.append(text.formatted(Color.DEFAULT, decoration, *extra))
        length += text.length
        return this
    }

    fun add(text: String): StylisedString {
        builder.append(text)
        length += text.length
        return this
    }

    fun add(char: Char): StylisedString {
        builder.append(char)
        ++length
        return this
    }

    fun clear() {
        builder.clear()
        length = 0
        styles.clear()
    }

    operator fun plus(text: String) = add(text)

    operator fun plus(char: Char) = add(char)

    override fun toString() = builder.toString()

    /**
     * Returns a (still styled) substring, containing the first `n` characters of actual text
     */
    fun take(n: Int): StylisedString {
        // mapping the index n to the expected index
        // yielding it as a result, while also capping the returned string's applied styles in the list
        return StylisedString(
            builder = StringBuilder(builder.substring(0, map(n)).andEndStyleIfNecessary(n)),
            length = n, // should be correct
            styles = getAllStylesUntilIndex(n)
        )
    }

    private fun map(index: Int): Int {
        // expecting the styles to be sorted
        var result = index
        styles.forEach { (range, styles) ->
            when {
                index < range.first -> {
                    // bailing, enough styles have been traversed
                    return result
                }
                index in range -> {
                    // bailing, only summing the applied styles
                    return result + styles.sumOf { it.unicodeLength }
                }
                index > range.last -> {
                    // full style and END character applied, adding and continuing
                    result += styles.sumOf { it.unicodeLength } + END.length
                }
            }
        }
        return result
    }

    private fun getAllStylesUntilIndex(end: Int): MutableList<Pair<IntRange, List<StyleModifier>>> {
        val result = mutableListOf<Pair<IntRange, List<StyleModifier>>>()
        styles.forEach { (range, styles) ->
            when {
                end < range.first -> {
                    // finished, all relevant styles have been processed
                    return result
                }
                end < range.last -> {
                    // cutting of the end of the style, as it is not fully relevant
                    result.add(range.first until end to styles)
                    // should've been the last element knowing the restrictions of the styles order, so returning
                    return result
                }
                else -> {
                    // relevant in its entirety, adding it as is
                    result.add(range to styles)
                }
            }
        }
        return result
    }

    private fun String.andEndStyleIfNecessary(visualIndex: Int): String {
        return if (isIndexStyled(visualIndex)) {
            this + END
        } else {
            this
        }
    }

    private fun isIndexStyled(index: Int): Boolean {
        styles.forEach { (range) ->
            when {
                index in range -> {
                    return true
                }
                index > range.last -> {
                    // don't have to look further, all relevant styles have been considered at this point
                    return false
                }
            }
        }
        return false
    }

    companion object {

        /* helpers */

        private fun String.formatted(color: Color = Color.DEFAULT, vararg decoration: Decoration) =
            "${decoration.joinToString(separator = "") { it.unicode }}${color.unicode}$this$END"

    }

}
