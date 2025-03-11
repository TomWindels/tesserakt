package dev.tesserakt.util.console

import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.sparql.runtime.evaluation.Bindings
import dev.tesserakt.util.fit
import dev.tesserakt.util.toString
import dev.tesserakt.util.weightedSort
import kotlin.math.max

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

class BindingsTable(
    private val _bindings: List<Bindings>
): Iterable<BindingsTable.Entry> {

    private val _columns = buildSet { _bindings.forEach { addAll(it.keys) } }
        .toMutableList()
    private val _widths = _columns
        .map { column ->
            max(column.length, _bindings.maxOf { binding -> binding[column]?.toString()?.length ?: 0 })
        }
        .toMutableList()
    val indexWidth = bindings.size.toString().length

    val columns: List<String> get() = _columns
    val bindings: List<Bindings> get() = _bindings
    val widths: List<Int> get() = _widths

    /**
     * Re-orders the columns. Only affects the `toString()` representation of this table. Names not included as
     *  parameters keep their relative order. Names included that are not part of the columns are ignored.
     */
    fun order(vararg name: String) {
        val weights = _columns.map { column ->
            val pos = name.indexOf(column)
            if (pos == -1) { name.size + 1 } else { pos }
        }
        _columns.weightedSort(weights)
        _widths.weightedSort(weights)
    }

    operator fun get(index: Int, name: String): Quad.Term? =
        _bindings.getOrNull(index)?.get(name)

    override fun toString(): String {
        return buildString {
            append("#")
            append(" ".repeat(indexWidth - 1))
            _columns.forEachIndexed { i, column ->
                append(" | ")
                append(column.fit(_widths[i]))
            }
            _bindings.forEachIndexed { i, bindings ->
                append("\n")
                append((i + 1).toString().fit(indexWidth))
                _columns.forEachIndexed { j, column ->
                    append(" | ")
                    append(bindings[column]?.toString(_widths[j]) ?: " ".repeat(_widths[j]))
                }
            }
        }
    }

    override operator fun iterator(): Iterator<Entry> = iterator {
        var i = 0
        while (i < _bindings.size) {
            yield(Entry(_bindings[i++]))
        }
    }

    inner class Entry internal constructor(private val binding: Bindings): Iterable<Quad.Term?> {

        operator fun get(name: String): Quad.Term? = binding[name]

        override operator fun iterator(): Iterator<Quad.Term?> = iterator {
            _columns.forEach { column ->
                yield(binding[column])
            }
        }

    }

    companion object {

        fun Collection<Bindings>.tabulate() = BindingsTable(_bindings = this.toList())

    }

}