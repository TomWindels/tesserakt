import dev.tesserakt.rdf.dsl.RdfContext.Companion.buildStore
import dev.tesserakt.rdf.n3.ExperimentalN3Api
import dev.tesserakt.rdf.n3.Quad
import dev.tesserakt.rdf.n3.Store
import dev.tesserakt.rdf.serialization.N3Serializer
import dev.tesserakt.rdf.types.Quad.Companion.asNamedTerm
import kotlin.test.Test

@OptIn(ExperimentalN3Api::class)
class N3Serialization {

    @Test
    fun serialize() {
        // based on https://w3c.github.io/N3/spec/#example-22
        val inner = buildStore {
            local("Superman") has local("can") being local("fly")
        }.toN3Store()
        val store = Store()
        store.add(Quad(
            s = Quad.Term.RdfTerm("LoisLane".asNamedTerm()),
            p = Quad.Term.RdfTerm("believes".asNamedTerm()),
            o = Quad.Term.StatementsList(inner)
        ))
        println(N3Serializer.serialize(store))
    }

    private fun dev.tesserakt.rdf.types.Store.toN3Store(): Store {
        return Store(
            elements = map {
                Quad(
                    s = Quad.Term.RdfTerm(it.s),
                    p = Quad.Term.RdfTerm(it.p),
                    o = Quad.Term.RdfTerm(it.o),
                )
            }
        )
    }

}
