package dev.tesserakt.sparql.runtime.evaluation.context

import dev.tesserakt.sparql.types.QueryStructure
import dev.tesserakt.sparql.types.extractAllBindings

class BindingsContext(ast: QueryStructure) {

    // note: this cannot be a read only list, as some add bindings during initialisation, such as repeating paths
    private val bindings = ast.body.extractAllBindings().mapTo(mutableListOf()) { it.name }
    private val bindingsLut = bindings.withIndex().associateTo(mutableMapOf()) { (i, value) -> value to i }

    fun newAnonymousBinding(): Int {
        // TODO(perf): we can simply track the fact that we've created an anonymous binding and reserve it in the lut
        //  without an actual str representation stored
        var i = bindingsLut.size
        var name: String
        do {
            name = "gen_${++i}"
        } while (name in bindingsLut)
        return encode(name)
    }

    fun encode(value: String): Int {
        return bindingsLut.getOrPut(value) {
            val i = bindings.size
            require(i < 32)
            require(bindingsLut.size == i)
            bindings.add(value)
            i
        }
    }

    fun decode(id: Int): String {
        return bindings[id]
    }


}
