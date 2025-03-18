package dev.tesserakt.sparql.runtime.compat

import dev.tesserakt.sparql.types.GraphPattern

object Compat {

    fun apply(body: GraphPattern): GraphPattern {
        return InnerFilterScopeCompat.apply(body)
    }

}
