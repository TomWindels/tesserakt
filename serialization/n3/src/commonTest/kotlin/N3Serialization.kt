
import dev.tesserakt.rdf.n3.ExperimentalN3Api
import dev.tesserakt.rdf.n3.dsl.N3Context.Companion.buildStore
import dev.tesserakt.rdf.ontology.XSD
import dev.tesserakt.rdf.serialization.N3Serializer
import kotlin.test.Test

@OptIn(ExperimentalN3Api::class)
class N3Serialization {

    @Test
    fun serialize0() {
        // based on https://w3c.github.io/N3/spec/#example-21
        val store = buildStore {
            val s = statements { local("cervantes") has local("wrote") being local("moby_dick") }
            s has local("opinion") being local("lie")
            s has local("retrievedFrom") being local("http://lies_r_us.com")
            s has local("retrievedAt") being "2020-07-12T09:01:33".asLiteralTerm(type = XSD.dateTime)
        }
        println(N3Serializer.serialize(store))
    }

    @Test
    fun serialize1() {
        // based on https://w3c.github.io/N3/spec/#example-22
        val store = buildStore {
            "LoisLane".asNamedTerm() has "believes".asNamedTerm() being statements {
                local("Superman") has local("can") being local("fly")
            }
        }
        println(N3Serializer.serialize(store))
    }

}
