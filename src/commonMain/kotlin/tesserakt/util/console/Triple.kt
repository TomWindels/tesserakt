@file:Suppress("NOTHING_TO_INLINE", "unused")

package tesserakt.util.console

import tesserakt.rdf.types.Triple

inline fun Triple.toStylisedString(): StylisedString =
    buildStylisedString {
        add("Triple {\n\ts = ")
        add(s.toStylisedString())
        add("\n\tp = ")
        add(p.toStylisedString())
        add("\n\to = ")
        add(o.toStylisedString())
        add("\n}")
    }

inline fun Triple.Term.toStylisedString(): StylisedString = when (this) {
    is Triple.BlankTerm ->
        "blank_$id".stylise(Color.BLACK)
    is Triple.Literal<*> -> buildStylisedString {
        add('"')
        add(value, Color.BRIGHT_GREEN)
        add("\"^^")
        add(type.value, Color.WHITE)
    }
    is Triple.NamedTerm -> value.stylise(Color.BRIGHT_BLUE)
}
