import dev.tesserakt.sparql.Bindings
import dev.tesserakt.sparql.toBindings
import dev.tesserakt.testing.Comparison
import dev.tesserakt.testing.comparisonOf

fun bindingComparisonOf(a: Iterable<Bindings>, b: Iterable<Bindings>): Comparison<Bindings> =
    comparisonOf(a.map { it.sortedBy { it.first } }, b.map { it.sortedBy { it.first } }).map { it.toBindings() }
