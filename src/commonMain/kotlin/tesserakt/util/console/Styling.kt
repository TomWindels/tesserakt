@file:Suppress("NOTHING_TO_INLINE", "unused")

package tesserakt.util.console

// src for the unicode values: https://www.lihaoyi.com/post/BuildyourownCommandLinewithANSIescapecodes.html

sealed interface StyleModifier {
    val unicodeLength: Int
}

enum class Decoration(internal val unicode: String) : StyleModifier {
    NONE(""),
    BOLD("\u001b[1m"),
    UNDERLINE("\u001b[4m"),
    REVERSED("\u001b[7m");

    override val unicodeLength: Int get() = unicode.length

}

enum class Color(internal val unicode: String) : StyleModifier {
    DEFAULT(""),
    RED("\u001b[31m"),
    BLACK("\u001b[30m"),
    GREEN("\u001b[32m"),
    YELLOW("\u001b[33m"),
    BLUE("\u001b[34m"),
    MAGENTA("\u001b[35m"),
    CYAN("\u001b[36m"),
    WHITE("\u001b[37m"),
    BRIGHT_BLACK("\u001b[30;1m"),
    BRIGHT_RED("\u001b[31;1m"),
    BRIGHT_GREEN("\u001b[32;1m"),
    BRIGHT_YELLOW("\u001b[33;1m"),
    BRIGHT_BLUE("\u001b[34;1m"),
    BRIGHT_MAGENTA("\u001b[35;1m"),
    BRIGHT_CYAN("\u001b[36;1m"),
    BRIGHT_WHITE("\u001b[37;1m");

    override val unicodeLength: Int get() = unicode.length

}

internal const val END = "\u001b[0m"

inline fun String.stylise(decoration: Decoration, vararg extra: Decoration) =
    StylisedString(text = this, color = Color.DEFAULT, decoration, *extra)

inline fun String.stylise(color: Color, vararg decoration: Decoration) =
    StylisedString(text = this, decoration = decoration, color = color)

inline fun buildStylisedString(block: StylisedString.() -> Unit): StylisedString {
    return StylisedString().apply(block)
}

inline fun String.bold() = stylise(Decoration.BOLD)
