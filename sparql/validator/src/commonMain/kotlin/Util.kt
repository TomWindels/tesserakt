import dev.tesserakt.sparql.Bindings
import dev.tesserakt.sparql.toBindings
import dev.tesserakt.testing.Comparison
import dev.tesserakt.testing.unorderedComparisonOf

fun unorderedBindingComparisonOf(
    a: Iterable<Bindings>,
    b: Iterable<Bindings>
): Comparison<Bindings> =
    unorderedComparisonOf(a.map { it.sortedBy { it.first } }, b.map { it.sortedBy { it.first } }).map { it.toBindings() }

fun orderedBindingComparisonOf(
    a: Iterable<Bindings>,
    b: Iterable<Bindings>,
): Comparison<Bindings> {
    val a = a.iterator()
    val b = b.iterator()
    while (a.hasNext()) {
        if (!b.hasNext()) {
            return Comparison(
                missing = listOf(a.next()),
                leftOver = emptyList()
            )
        }
        val a = a.next()
        val b = b.next()
        if (a.sortedBy { it.first } != b.sortedBy { it.first }) {
            return Comparison(
                missing = listOf(a),
                leftOver = listOf(b),
            )
        }
    }
    if (b.hasNext()) {
        return Comparison(
            missing = emptyList(),
            leftOver = listOf(b.next()),
        )
    }
    return Comparison(emptyList(), emptyList())
}
