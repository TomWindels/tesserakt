package core.rdf.dsl

import core.rdf.types.Store
import core.rdf.types.Triple
import core.rdf.types.Triple.Companion.asLiteral
import core.rdf.types.Triple.Companion.asNamedNode
import kotlin.jvm.JvmInline
import kotlin.jvm.JvmStatic

class Builder internal constructor(
    val context: Context,
    private val onTripleAdded: (subject: Triple.Term, predicate: Triple.Term, `object`: Any /* either Blank, Term or List */) -> Unit
) {

    data class Context(
        val path: String
    ) {
        val Element.uri: Triple.NamedTerm
            get() = name.absolutePath
        val Triple.NamedTerm.relativePath: String
            get() = value.removePrefix("$path/")
        val String.absolutePath: Triple.NamedTerm
            get() = "${this@Context.path}/$this".asNamedNode()
    }

    interface Element {
        val name: String
    }

    val Element.uri
        get() = with (context) { uri }

    operator fun Triple.NamedTerm.unaryPlus() = Subject { predicate: Triple.NamedTerm, `object`: Any ->
        onTripleAdded(this, predicate, `object`)
    }

    @JvmInline
    value class Subject(private val callback: (predicate: Triple.NamedTerm, `object`: Any) -> Unit) {

        infix fun has(predicate: Triple.NamedTerm) = SubjectPredicate { callback(predicate, it) }

    }

    @JvmInline
    value class SubjectPredicate(private val callback: (`object`: Any) -> Unit) {

        infix fun being(literal: Int) = callback(literal.asLiteral())

        infix fun being(literal: Long) = callback(literal.asLiteral())

        infix fun being(literal: Float) = callback(literal.asLiteral())

        infix fun being(literal: Double) = callback(literal.asLiteral())

        infix fun being(value: Triple.Term) = callback(value)

        infix fun being(blank: Blank) = callback(blank)

        infix fun being(list: List) = callback(list)

    }

    @JvmInline
    value class Blank private constructor(internal val data: Map<Triple.NamedTerm, Any /* Term, List or Blank */>) {

        companion object {

            fun from(block: Scope.() -> Unit): Blank {
                return Blank(data = Scope().apply(block).data)
            }

        }

        class Scope {

            internal val data = mutableMapOf<Triple.NamedTerm, Any>()

            operator fun String.unaryPlus() = SubjectPredicate {
                data[this.asNamedNode()] = it
            }

            operator fun Triple.NamedTerm.unaryPlus() = SubjectPredicate {
                data[this] = it
            }

        }

    }

    @JvmInline
    value class List internal constructor(internal val data: kotlin.collections.List<Any>)

    fun blank(block: Blank.Scope.() -> Unit): Blank {
        return Blank.from(block)
    }

    // does the necessary type checking
    fun list(items: Iterable<Any>) = List(data = items.toList())

    fun list(vararg data: Any) = List(data.toList())

    companion object {

        @JvmStatic
        fun buildStore(path: String = "", block: Builder.() -> Unit) =
            Store().apply { insert(Context(path = path), block) }

        @JvmStatic
        fun buildStore(context: Context, block: Builder.() -> Unit) =
            Store().apply { insert(context, block) }

        @JvmStatic
        fun Store.insert(context: Context, block: Builder.() -> Unit) {
            Builder(context) { subject: Triple.Term, predicate: Triple.Term, `object`: Any ->
                // TODO: actual correct support for expected types here (blank subj, blank object, literal object, rdf list)
                println("Adding $subject")
                add(Triple(subject as Triple.NamedTerm, predicate as Triple.NamedTerm, `object` as Triple.Term))
            }.apply(block)
        }

    }

}
