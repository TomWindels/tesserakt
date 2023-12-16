package tesserakt.util.console

import tesserakt.util.BindingsTable
import tesserakt.util.fit

fun BindingsTable.toStylisedString(): StylisedString {
    return buildStylisedString {
        add("#", Decoration.BOLD, Decoration.UNDERLINE)
        add(" ".repeat(indexWidth - 1), Decoration.UNDERLINE)
        columns.forEachIndexed { i, column ->
            add(" | ", Decoration.UNDERLINE)
            add(column.fit(widths[i]), Decoration.BOLD, Decoration.UNDERLINE)
        }
        bindings.forEachIndexed { i, bindings ->
            add("\n")
            add((i + 1).toString().fit(indexWidth))
            columns.forEachIndexed { j, column ->
                add(" | ")
                bindings[column]?.let { term ->
                    add(term.toStylisedString().fit(widths[j]))
                } ?: run {
                    add(" ".repeat(widths[j]))
                }
            }
        }
    }
}
