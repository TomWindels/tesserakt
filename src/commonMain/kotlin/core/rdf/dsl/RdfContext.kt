package core.rdf.dsl

import core.rdf.ontology.RDF
import core.rdf.types.Store
import core.rdf.types.Triple
import core.rdf.types.Triple.Companion.asLiteral
import core.rdf.types.Triple.Companion.asNamedTerm
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.jvm.JvmInline
import kotlin.jvm.JvmStatic

@Suppress("NOTHING_TO_INLINE", "PropertyName", "unused")
class RdfContext internal constructor(
    private val environment: Environment,
    val consumer: Consumer
) {

    var _blank_index = 0

    interface Consumer {

        /** Regular triple receiver **/
        fun process(
            subject: Triple.NamedTerm,
            predicate: Triple.NamedTerm,
            `object`: Triple.Term
        )

        /** Blank-node triple receiver **/
        fun process(
            subject: Triple.BlankTerm,
            predicate: Triple.NamedTerm,
            `object`: Triple.Term
        )

        /** A method processing incoming RDF lists, returning a term pointing to that list **/
        // default impl creates blank nodes acting like the list, no optimisation in representation (like turtle)
        //  out of the box
        fun process(context: RdfContext, list: List): Triple.Term = with(context) {
            return if (list.data.isEmpty()) {
                RDF.nil
            } else {
                val first = genBlankNodeId()
                var current = first
                val iter = list.data.iterator()
                // actually filling in the data
                current has RDF.first being iter.next()
                while (iter.hasNext()) {
                    val next = genBlankNodeId()
                    next has RDF.first being iter.next()
                    current has RDF.rest being next
                    current = next
                }
                // ending the list
                current has RDF.rest being RDF.nil
                // returning the term pointing to the start of the list
                first
            }
        }

    }

    open class Environment(
        val path: String
    ) {
        val Triple.NamedTerm.relativePath: String
            get() = value.removePrefix(path)
    }

    /** Creates an environment-aware `NamedTerm`, e.g. "shape" on "localhost:3000/" becomes `http://localhost:3000/shape` **/
    fun local(name: String) = "${environment.path}$name".asNamedTerm()

    infix fun Triple.NamedTerm.has(predicate: Triple.NamedTerm) = Statement(this, predicate)

    infix fun Triple.BlankTerm.has(predicate: Triple.NamedTerm) = BlankStatement(this, predicate)

    inner class Statement(val _s: Triple.NamedTerm, val _p: Triple.NamedTerm) {

        inline infix fun being(literal: Int) = consumer.process(_s, _p, literal.asLiteral())

        inline infix fun being(literal: Long) = consumer.process(_s, _p, literal.asLiteral())

        inline infix fun being(literal: Float) = consumer.process(_s, _p, literal.asLiteral())

        inline infix fun being(literal: Double) = consumer.process(_s, _p, literal.asLiteral())

        inline infix fun being(value: Triple.Term) = consumer.process(_s, _p, value)

        inline infix fun being(blank: Blank) = consumer.process(_s, _p, blank._name)

        inline infix fun being(multiple: Multiple) {
            multiple.data.forEach { term ->
                consumer.process(subject = _s, predicate = _p, `object`= term)
            }
        }

        inline infix fun being(list: List) = consumer.process(_s, _p, consumer.process(this@RdfContext, list))

    }

    inner class BlankStatement(val _s: Triple.BlankTerm, val _p: Triple.NamedTerm) {

        inline infix fun being(literal: Int) = consumer.process(_s, _p, literal.asLiteral())

        inline infix fun being(literal: Long) = consumer.process(_s, _p, literal.asLiteral())

        inline infix fun being(literal: Float) = consumer.process(_s, _p, literal.asLiteral())

        inline infix fun being(literal: Double) = consumer.process(_s, _p, literal.asLiteral())

        inline infix fun being(value: Triple.Term) = consumer.process(_s, _p, value)

        inline infix fun being(blank: Blank) = consumer.process(_s, _p, blank._name)

        inline infix fun being(multiple: Multiple) {
            multiple.data.forEach { term ->
                consumer.process(subject = _s, predicate = _p, `object`= term)
            }
        }

        inline infix fun being(list: List) = consumer.process(_s, _p, consumer.process(this@RdfContext, list))

    }

    inner class Blank(val _name: Triple.BlankTerm) {

        inline infix fun Triple.NamedTerm.being(literal: Int) {
            consumer.process(subject = _name, predicate = this, `object`= literal.asLiteral())
        }


        inline infix fun Triple.NamedTerm.being(literal: Long) {
            consumer.process(subject = _name, predicate = this, `object`= literal.asLiteral())
        }

        inline infix fun Triple.NamedTerm.being(literal: Float) {
            consumer.process(subject = _name, predicate = this, `object`= literal.asLiteral())
        }

        inline infix fun Triple.NamedTerm.being(literal: Double) {
            consumer.process(subject = _name, predicate = this, `object`= literal.asLiteral())
        }

        inline infix fun Triple.NamedTerm.being(term: Triple.Term) {
            consumer.process(subject = _name, predicate = this, `object`= term)
        }

        inline infix fun Triple.NamedTerm.being(blank: Blank) {
            consumer.process(subject = _name, predicate = this, `object`= blank._name)
        }

        inline infix fun Triple.NamedTerm.being(multiple: Multiple) {
            multiple.data.forEach { term ->
                consumer.process(subject = _name, predicate = this, `object`= term)
            }
        }

        inline infix fun Triple.NamedTerm.being(list: List) {
            consumer.process(subject = _name, predicate = this, `object`= consumer.process(this@RdfContext, list))
        }

    }

    @JvmInline
    value class List internal constructor(val data: Array<out Triple.Term>)

    @JvmInline
    value class Multiple internal constructor(val data: Array<out Triple.Term>)

    inline fun blank(block: Blank.() -> Unit): Triple.BlankTerm {
        return Blank(genBlankNodeId()).apply(block)._name
    }

    inline fun genBlankNodeId() = Triple.BlankTerm("_b_${_blank_index++}")

    fun list(data: Collection<Triple.Term>) = List(data = data.toTypedArray())

    fun list(vararg data: Triple.Term) = List(data)

    fun multiple(data: Collection<Triple.Term>) = Multiple(data.toTypedArray())

    fun multiple(vararg data: Triple.Term) = Multiple(data)

    companion object {

        @OptIn(ExperimentalContracts::class)
        @JvmStatic
        fun buildStore(path: String = "", block: RdfContext.() -> Unit): Store {
            contract {
                callsInPlace(block, InvocationKind.EXACTLY_ONCE)
            }
            return Store().apply { insert(Environment(path = path), block) }
        }

        @OptIn(ExperimentalContracts::class)
        @JvmStatic
        fun buildStore(environment: Environment, block: RdfContext.() -> Unit): Store {
            contract {
                callsInPlace(block, InvocationKind.EXACTLY_ONCE)
            }
            return Store().apply { insert(environment, block) }
        }

        @JvmStatic
        fun Store.insert(environment: Environment, block: RdfContext.() -> Unit) {
            RdfContext(
                environment = environment,
                consumer = StoreAdapter(this)
            )
            .apply(block)
        }

    }

}
