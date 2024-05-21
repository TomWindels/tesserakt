@file:Suppress("NOTHING_TO_INLINE", "unused")

package dev.tesserakt.util.console

import dev.tesserakt.rdf.types.Quad

inline fun Quad.toStylisedString(): StylisedString =
    buildStylisedString {
        add("Triple {\n\ts = ")
        add(s.toStylisedString())
        add("\n\tp = ")
        add(p.toStylisedString())
        add("\n\to = ")
        add(o.toStylisedString())
        add("\n}")
    }

inline fun Quad.Term.toStylisedString(): StylisedString = when (this) {
    is Quad.BlankTerm ->
        "blank_$id".stylise(Color.BLACK)
    is Quad.Literal<*> -> buildStylisedString {
        add('"')
        add(value, Color.BRIGHT_GREEN)
        add("\"^^")
        add(type.value, Color.WHITE)
    }
    is Quad.NamedTerm -> value.stylise(Color.BRIGHT_BLUE)
}
