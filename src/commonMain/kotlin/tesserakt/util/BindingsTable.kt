package tesserakt.util

import tesserakt.rdf.types.Triple
import tesserakt.sparql.runtime.types.Bindings
import tesserakt.util.Unicode.bold
import kotlin.math.max

class BindingsTable(
    private val bindings: List<Bindings>
): Iterable<BindingsTable.Entry> {

    private val columns = buildSet { bindings.forEach { addAll(it.keys) } }
        .toMutableList()
    private val columnLengths = columns
        .map { column -> max(column.length, bindings.maxOf { binding -> binding[column]?.value?.length ?: 0 }) }
        .toMutableList()
    private val numberWidth = bindings.size.toString().length

    /**
     * Re-orders the columns. Only affects the `toString()` representation of this table. Names not included as
     *  parameters keep their relative order. Names included that are not part of the columns are ignored.
     */
    fun order(vararg name: String) {
        val weights = columns.map { column ->
            val pos = name.indexOf(column)
            if (pos == -1) { name.size + 1 } else { pos }
        }
        columns.weightedSort(weights)
        columnLengths.weightedSort(weights)
    }

    operator fun get(index: Int, name: String): Triple.Term? =
        bindings.getOrNull(index)?.get(name)

    override fun toString(): String {
        return buildString {
            append("#".bold())
            append(" ".repeat(numberWidth - 1))
            columns.forEachIndexed { i, column ->
                append(" | ")
                append(column.fit(columnLengths[i]).bold())
            }
            bindings.forEachIndexed { i, bindings ->
                append("\n")
                append((i + 1).toString().fit(numberWidth))
                columns.forEachIndexed { j, column ->
                    append(" | ")
                    append(
                        bindings[column]
                            ?.value
                            ?.fit(max(column.length, columnLengths[j]))
                            ?: " ".repeat(column.length)
                    )
                }
            }
        }
    }

    override operator fun iterator(): Iterator<Entry> = iterator {
        var i = 0
        while (i < bindings.size) {
            yield(Entry(bindings[i++]))
        }
    }

    inner class Entry internal constructor(private val binding: Bindings): Iterable<Triple.Term?> {

        operator fun get(name: String): Triple.Term? = binding[name]

        override operator fun iterator(): Iterator<Triple.Term?> = iterator {
            columns.forEach { column ->
                yield(binding[column])
            }
        }

    }

    companion object {

        fun Collection<Bindings>.tabulate() = BindingsTable(bindings = this.toList())

    }

}
