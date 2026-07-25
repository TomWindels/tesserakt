
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.rdf.types.Quad.Companion.asLiteralTerm
import dev.tesserakt.rdf.types.Quad.Companion.asNamedTerm
import dev.tesserakt.util.jsCastOrBail
import dev.tesserakt.util.jsExpect

// can be kept here, top level
// considering JS is single threaded, no races for this value can occur
private var nextBlankId = 0

@OptIn(ExperimentalJsExport::class)
@JsExport
@JsName("Term")
sealed class TermJs {

    /**
     * The internal 'representation' used throughout the Kotlin implementation
     */
    internal abstract val repr: Quad.Element

    override fun hashCode(): Int {
        return repr.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        return repr == other
    }

    override fun toString(): String {
        return repr.toString()
    }

}

@OptIn(ExperimentalJsExport::class)
@JsExport
class NamedTerm(value: Any?): TermJs() {

    override val repr: Quad.NamedTerm = value as? Quad.NamedTerm ?: constructNamedTerm(value.jsExpect().jsCastOrBail())

    val value: String
        get() = repr.value

}

@OptIn(ExperimentalJsExport::class)
@JsExport
class LiteralTerm(value: Any?, type: Any? = undefined, language: Any? = undefined): TermJs() {

    override val repr: Quad.Literal = constructLiteralTerm(
        value = value,
        type = type,
        language = language,
    )

    val value: String
        get() = repr.value

    val type: NamedTerm
        get() = NamedTerm(repr.type)

    val language: String?
        get() = (repr as? Quad.LangString)?.language

}

@OptIn(ExperimentalJsExport::class)
@JsExport
class BlankTerm(id: Any? = undefined): TermJs() {

    override val repr: Quad.BlankTerm = constructBlankTerm(id)

    val id: Int
        get() = repr.id

}

@OptIn(ExperimentalJsExport::class)
@JsExport
class GraphTerm(value: Any? = undefined): TermJs() {

    override val repr: Quad.Graph = constructGraphTerm(value)

    val isDefaultGraph: Boolean
        get() = repr == Quad.DefaultGraph

    val value: String?
        get() = (repr as? Quad.NamedTerm)?.value

    val id: Int?
        get() = (repr as? Quad.BlankTerm)?.id

}

internal val DefaultGraphTerm = GraphTerm(Quad.DefaultGraph)

private fun constructNamedTerm(value: String?) = value.jsExpect().asNamedTerm()

private fun constructLiteralTerm(value: Any?, type: Any? = undefined, language: Any? = undefined): Quad.Literal {
    val value = value.jsExpect()
    return when {
        type is NamedTerm -> {
            Quad.Literal(
                value = value.toString(),
                type = type.repr,
            )
        }
        type is String -> {
            Quad.Literal(
                value = value.toString(),
                type = Quad.NamedTerm(type),
            )
        }
        type != null -> {
            throw Error("Unexpected type: `$type`. Expected either a string or named term instance.")
        }

        /* no type information available, inferring it */

        value is Number -> {
            value.asLiteralTerm()
        }
        value is String -> {
            if (language != null) {
                if (language !is String) {
                    throw Error("Invalid language string: `$language`")
                }
                Quad.LangString(value, language)
            } else {
                Quad.SimpleLiteral(value)
            }
        }
        else -> {
            throw Error("Invalid value used to construct a literal: `${value}`")
        }
    }
}

private fun constructBlankTerm(id: Any? = undefined): Quad.BlankTerm {
    val id = if (id != null) {
        if (id !is Number) {
            throw Error("Invalid ID used to create a blank term: `${id}`")
        }
        id.toInt()
    } else {
        nextBlankId++
    }
    return Quad.BlankTerm(id)
}

private fun constructGraphTerm(value: Any? = undefined): Quad.Graph {
    return when (value) {
        null -> Quad.DefaultGraph
        is Number -> constructBlankTerm(value)
        is BlankTerm -> value.repr
        is String -> constructNamedTerm(value)
        is NamedTerm -> value.repr
        else -> throw Error("Invalid graph identifier: `${value}`")
    }
}
